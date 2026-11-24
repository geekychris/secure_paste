package com.securepaste.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepaste.dto.PasteCreateRequest;
import com.securepaste.entity.Visibility;
import com.securepaste.mail.FakeSmtpServer;
import com.securepaste.repository.PasteRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Email this link" through the API, delivered to a fake SMTP server. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class MailControllerTest {

    private static final String SECRET = "the launch code is 0000";
    private static final FakeSmtpServer SMTP;

    static {
        try {
            SMTP = new FakeSmtpServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry r) {
        r.add("securepaste.base-url", () -> "https://securepaste.example.com/");
        r.add("securepaste.mail.host", () -> "127.0.0.1");
        r.add("securepaste.mail.port", SMTP::port);
        r.add("securepaste.mail.tls", () -> "none");
        r.add("securepaste.mail.from", () -> "paste@example.com");
        r.add("securepaste.mail.site-name", () -> "Belmont");
        r.add("securepaste.mail.rate-per-recipient-per-hour", () -> "3");
    }

    @AfterAll
    static void stopSmtp() throws IOException {
        SMTP.close();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasteRepository pasteRepository;

    @AfterEach
    void cleanUp() {
        pasteRepository.deleteAll();
        SMTP.clear();
    }

    private String create(Integer maxViews, String password) throws Exception {
        PasteCreateRequest request = new PasteCreateRequest("Secret title", SECRET);
        request.setVisibility(Visibility.UNLISTED);
        request.setMaxViews(maxViews);
        request.setPassword(password);
        request.setExpirationMinutes(60);
        String body = mockMvc.perform(post("/api/pastes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return (String) objectMapper.readValue(body, Map.class).get("id");
    }

    private org.springframework.test.web.servlet.ResultActions email(String id, String to) throws Exception {
        return mockMvc.perform(post("/api/pastes/" + id + "/email").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("to", to))));
    }

    @Test
    void statusSaysEnabled() throws Exception {
        mockMvc.perform(get("/api/mail/status")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void emailsTheLinkNotTheContent(CapturedOutput output) throws Exception {
        String id = create(1, "pw-123");
        email(id, "sam@example.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true)).andExpect(jsonPath("$.to").value("sam@example.com"));

        assertThat(SMTP.messages()).hasSize(1);
        FakeSmtpServer.Message m = SMTP.messages().get(0);
        assertThat(m.to()).containsExactly("sam@example.com");
        String data = m.data().replace("=\r\n", "");
        assertThat(data).contains("https://securepaste.example.com/paste/" + id, "SecurePaste (Belmont)", "viewed once",
                "expires on", "password");
        assertThat(data).doesNotContain(SECRET).doesNotContain("Secret title").doesNotContain("pw-123");
        // The paste is untouched: no view used.
        mockMvc.perform(post("/api/pastes/" + id + "/reveal?password=pw-123")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(SECRET));
        // The mail code logs the recipient, never the link (the paste id).
        // (The test profile's DEBUG web logging prints request paths; the
        // deployed profiles don't.)
        String mailLines = output.getAll().lines().filter(l -> l.contains("securepaste.mail.MailService"))
                .reduce("", (a, b) -> a + b + "\n");
        assertThat(mailLines).contains("mail sent to=sam@example.com").doesNotContain(id);
    }

    @Test
    void refusesBadAddressesAndInjection() throws Exception {
        String id = create(null, null);
        for (String bad : new String[] {"sam", "sam@example.com\r\nBcc: x@evil.com", "a@example.com, b@example.com", "Sam <sam@example.com>"}) {
            email(id, bad).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/pastes/" + id + "/email").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(SMTP.messages()).isEmpty();
    }

    @Test
    void unknownPasteIs404() throws Exception {
        email("does-not-exist", "sam@example.com").andExpect(status().isNotFound());
        email("../etc", "sam@example.com").andExpect(status().is4xxClientError());
        assertThat(SMTP.messages()).isEmpty();
    }

    @Test
    void rateLimited() throws Exception {
        String id = create(null, null);
        for (int i = 0; i < 3; i++) {
            email(id, "limit@example.com").andExpect(status().isOk());
        }
        email(id, "limit@example.com").andExpect(status().isTooManyRequests());
        assertThat(SMTP.messages()).hasSize(3);
    }
}
