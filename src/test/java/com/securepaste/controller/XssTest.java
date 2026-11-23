package com.securepaste.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepaste.dto.PasteCreateRequest;
import com.securepaste.entity.Visibility;
import com.securepaste.repository.PasteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paste fields are untrusted: a title, author or language full of markup
 * must reach the browser as text. The pages render the server side with
 * Thymeleaf's escaped th:text, and the client side with textContent (their
 * scripts are static files, checked here for innerHTML); a CSP forbids any
 * inline script as a second line of defence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class XssTest {

    private static final String SCRIPT_TITLE = "<script>alert(1)</script>";
    private static final String IMG_AUTHOR = "\"><img src=x onerror=alert(2)>";
    private static final String SVG_LANGUAGE = "<svg onload=alert(3)>";
    private static final String SCRIPT_CONTENT = "</pre><script>alert(4)</script>";

    /** An inline <script> (one without src) or an on* event attribute. */
    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\ssrc=)[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_ATTR = Pattern.compile("<[^>]+\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasteRepository pasteRepository;

    @AfterEach
    void cleanUp() {
        pasteRepository.deleteAll();
    }

    private String create(Integer maxViews) throws Exception {
        PasteCreateRequest r = new PasteCreateRequest(SCRIPT_TITLE, SCRIPT_CONTENT);
        r.setAuthorName(IMG_AUTHOR);
        r.setLanguage(SVG_LANGUAGE);
        r.setVisibility(Visibility.PUBLIC);
        r.setMaxViews(maxViews);
        String body = mockMvc.perform(post("/api/pastes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) objectMapper.readValue(body, Map.class).get("id");
    }

    private static void assertNoMarkupLeak(String html) {
        assertThat(html).doesNotContain(SCRIPT_TITLE, "<img src=x", "<svg onload", "<script>alert");
        assertThat(INLINE_SCRIPT.matcher(html).find()).as("inline <script> in page").isFalse();
        assertThat(EVENT_ATTR.matcher(html).find()).as("on* event attribute in page").isFalse();
    }

    @Test
    void homePage_noInlineScript_andListBuiltWithTextContent() throws Exception {
        create(null);
        String html = mockMvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoMarkupLeak(html);
        assertThat(html).contains("<script src=\"/js/index.js\"></script>");

        // The list the page draws comes from this JSON, as text.
        mockMvc.perform(get("/api/pastes/public"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        String js = mockMvc.perform(get("/js/index.js")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(js).doesNotContain("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write")
                .contains("textContent");
    }

    @Test
    void pastePage_noInlineScript_andDetailsBuiltWithTextContent() throws Exception {
        String id = create(null);
        String html = mockMvc.perform(get("/paste/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoMarkupLeak(html);
        String js = mockMvc.perform(get("/js/paste.js")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(js).doesNotContain("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write");
    }

    @Test
    void search_returnsJsonNotHtml() throws Exception {
        create(null);
        mockMvc.perform(get("/api/pastes/search").param("q", "script"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void revealPage_escapesTitleLanguageAndContent() throws Exception {
        String id = create(1);
        String inter = mockMvc.perform(get("/paste/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoMarkupLeak(inter);

        String html = mockMvc.perform(post("/paste/" + id + "/reveal").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoMarkupLeak(html);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;")   // title, as text
                .contains("&lt;/pre&gt;&lt;script&gt;alert(4)&lt;/script&gt;") // content, as text
                .contains("&lt;svg onload=alert(3)&gt;")                      // language, as text
                .contains("<script src=\"/js/reveal.js\"></script>");
    }

    @Test
    void revealPage_wrongPassword_escapesNothingUserSupplied() throws Exception {
        PasteCreateRequest r = new PasteCreateRequest(SCRIPT_TITLE, SCRIPT_CONTENT);
        r.setMaxViews(2);
        r.setPassword("pw");
        String body = mockMvc.perform(post("/api/pastes").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r))).andReturn().getResponse().getContentAsString();
        String id = (String) objectMapper.readValue(body, Map.class).get("id");
        String html = mockMvc.perform(post("/paste/" + id + "/reveal").with(csrf()).param("password", "<b>x"))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertNoMarkupLeak(html);
        assertThat(html).doesNotContain("<b>x");
    }

    @Test
    void contentSecurityPolicy_onPagesAndApi() throws Exception {
        String id = create(null);
        for (String path : new String[]{"/", "/paste/" + id, "/api/pastes/" + id, "/js/index.js"}) {
            mockMvc.perform(get(path))
                    .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                    .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self';")))
                    .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")));
        }
    }
}
