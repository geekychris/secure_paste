package com.securepaste.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepaste.dto.PasteCreateRequest;
import com.securepaste.dto.PasteResponse;
import com.securepaste.entity.Paste;
import com.securepaste.entity.Visibility;
import com.securepaste.exception.PasteNotFoundException;
import com.securepaste.repository.PasteRepository;
import com.securepaste.service.PasteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * View-limited ("burn after reading") pastes: the API, the tap-to-reveal
 * page and concurrent reveals.
 *
 * Not @Transactional: the concurrency test needs each reveal in its own
 * committed transaction, and the others should see the database as a
 * real client does. Rows are removed after each test.
 *
 * Runs on H2 by default. To run it against PostgreSQL:
 * mvn test -Dtest=ViewLimitTest -Dspring.datasource.url=jdbc:postgresql://... \
 *   -Dspring.datasource.username=... -Dspring.datasource.password=... \
 *   -Dspring.datasource.driver-class-name=org.postgresql.Driver \
 *   -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViewLimitTest {

    private static final String SECRET = "the launch code is 0000";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasteService pasteService;
    @Autowired private PasteRepository pasteRepository;

    @AfterEach
    void cleanUp() {
        pasteRepository.deleteAll();
    }

    private String create(Integer maxViews, String password) throws Exception {
        PasteCreateRequest request = new PasteCreateRequest("Secret title", SECRET);
        request.setVisibility(Visibility.PUBLIC);
        request.setMaxViews(maxViews);
        request.setPassword(password);
        String body = mockMvc.perform(post("/api/pastes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) objectMapper.readValue(body, Map.class).get("id");
    }

    @Test
    void singleUse_revealOnceThenGone() throws Exception {
        String id = create(1, null);

        mockMvc.perform(post("/api/pastes/" + id + "/reveal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(SECRET))
                .andExpect(jsonPath("$.burned").value(true))
                .andExpect(jsonPath("$.viewsRemaining").value(0))
                .andExpect(jsonPath("$.maxViews").value(1));

        // The row itself is gone, not soft-deleted with its content.
        assertThat(pasteRepository.findById(id)).isEmpty();

        mockMvc.perform(post("/api/pastes/" + id + "/reveal")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pastes/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void maxViewsN_allowsExactlyN() throws Exception {
        String id = create(3, null);
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/pastes/" + id + "/reveal"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value(SECRET))
                    .andExpect(jsonPath("$.viewsRemaining").value(3 - i))
                    .andExpect(jsonPath("$.burned").value(i == 3));
        }
        mockMvc.perform(post("/api/pastes/" + id + "/reveal")).andExpect(status().isNotFound());
    }

    @Test
    void concurrentReveal_onlyOneWins() throws Exception {
        for (int round = 0; round < 5; round++) {
            String id = pasteService.createPaste(request(1)).getId();
            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger wins = new AtomicInteger();
            AtomicInteger gone = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    start.await();
                    try {
                        PasteResponse r = pasteService.revealPaste(id, null);
                        assertThat(r.getContent()).isEqualTo(SECRET);
                        wins.incrementAndGet();
                    } catch (PasteNotFoundException e) {
                        gone.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS); // rethrows anything other than "gone"
            }
            pool.shutdown();
            assertThat(wins.get()).as("reveals that got the content").isEqualTo(1);
            assertThat(gone.get()).isEqualTo(threads - 1);
            assertThat(pasteRepository.findById(id)).isEmpty();
        }
    }

    @Test
    void concurrentReveal_maxViewsThree_exactlyThreeWin() throws Exception {
        String id = pasteService.createPaste(request(3)).getId();
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit((Callable<Void>) () -> {
                start.await();
                try {
                    pasteService.revealPaste(id, null);
                    wins.incrementAndGet();
                } catch (PasteNotFoundException e) {
                    // expected for all but three
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(wins.get()).isEqualTo(3);
        assertThat(pasteRepository.findById(id)).isEmpty();
    }

    @Test
    void passwordAndBurn_wrongPasswordUsesNoView() throws Exception {
        String id = create(1, "hunter2");

        mockMvc.perform(post("/api/pastes/" + id + "/reveal")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pastes/" + id + "/reveal").param("password", "wrong"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pastes/" + id + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden());

        assertThat(pasteRepository.findById(id)).get()
                .extracting(Paste::getViewCount).isEqualTo(0L);

        // Metadata of a protected paste: no content and no title.
        mockMvc.perform(get("/api/pastes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.passwordProtected").value(true))
                .andExpect(jsonPath("$.viewsRemaining").value(1));

        // The password in a JSON body.
        mockMvc.perform(post("/api/pastes/" + id + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"hunter2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(SECRET))
                .andExpect(jsonPath("$.burned").value(true));
        mockMvc.perform(post("/api/pastes/" + id + "/reveal").param("password", "hunter2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiryAndBurn_expiredPasteCannotBeRevealed() throws Exception {
        String id = create(1, null);
        Paste paste = pasteRepository.findById(id).orElseThrow();
        paste.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        pasteRepository.save(paste);

        mockMvc.perform(post("/api/pastes/" + id + "/reveal")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pastes/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/paste/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(SECRET))));
        assertThat(pasteRepository.findById(id)).get()
                .extracting(Paste::getViewCount).isEqualTo(0L);
    }

    @Test
    void interstitialGet_usesNoViewAndLeaksNothing() throws Exception {
        String id = create(1, null);

        for (int i = 0; i < 3; i++) { // link previews, scanners, the sender checking the link
            mockMvc.perform(get("/paste/" + id))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("This paste can be viewed 1 more time; viewing it will use one.")))
                    .andExpect(content().string(containsString("Reveal")))
                    .andExpect(content().string(not(containsString(SECRET))))
                    .andExpect(content().string(not(containsString("Secret title"))))
                    .andExpect(content().string(containsString("name=\"_csrf\"")));
        }

        // The API GET is metadata only, too.
        mockMvc.perform(get("/api/pastes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.revealRequired").value(true))
                .andExpect(jsonPath("$.viewsRemaining").value(1));

        assertThat(pasteRepository.findById(id)).get()
                .extracting(Paste::getViewCount).isEqualTo(0L);

        // Limited pastes stay out of listings and search.
        mockMvc.perform(get("/api/pastes/public"))
                .andExpect(content().string(not(containsString(id))));
        mockMvc.perform(get("/api/pastes/search").param("q", "launch code"))
                .andExpect(content().string(not(containsString(id))));
        mockMvc.perform(get("/api/pastes/recent"))
                .andExpect(content().string(not(containsString(id))));
    }

    @Test
    void interstitialPluralises() throws Exception {
        String id = create(3, null);
        mockMvc.perform(get("/paste/" + id))
                .andExpect(content().string(containsString("This paste can be viewed 3 more times; viewing it will use one.")));
    }

    @Test
    void formReveal_needsCsrfAndBurns() throws Exception {
        String id = create(1, null);

        // Without the token: refused, and nothing used.
        mockMvc.perform(post("/paste/" + id + "/reveal")).andExpect(status().isForbidden());
        assertThat(pasteRepository.findById(id)).isPresent();

        mockMvc.perform(post("/paste/" + id + "/reveal").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(SECRET)))
                .andExpect(content().string(containsString("This paste has now been deleted")))
                .andExpect(content().string(containsString("Copy Content")));

        mockMvc.perform(post("/paste/" + id + "/reveal").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("This paste is gone")));
        mockMvc.perform(get("/paste/" + id))
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test
    void formReveal_viewsLeftAndWrongPassword() throws Exception {
        String id = create(3, "pw");

        mockMvc.perform(get("/paste/" + id))
                .andExpect(content().string(containsString("type=\"password\"")));

        mockMvc.perform(post("/paste/" + id + "/reveal").with(csrf()).param("password", "nope"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Wrong password. No view was used.")))
                .andExpect(content().string(containsString("viewed 3 more times")))
                .andExpect(content().string(not(containsString(SECRET))));

        mockMvc.perform(post("/paste/" + id + "/reveal").with(csrf()).param("password", "pw"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(SECRET)))
                .andExpect(content().string(containsString("2 views left.")));
    }

    @Test
    void unlimitedPaste_behaviourUnchanged() throws Exception {
        String id = create(null, null);

        mockMvc.perform(get("/api/pastes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(SECRET))
                .andExpect(jsonPath("$.maxViews").doesNotExist())
                .andExpect(jsonPath("$.revealRequired").value(false))
                .andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(get("/api/pastes/" + id))
                .andExpect(jsonPath("$.viewCount").value(2));

        // The page that loads it through the API, not the interstitial.
        mockMvc.perform(get("/paste/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Loading paste...")))
                .andExpect(content().string(not(containsString("viewing it will use one"))));

        // Still listed, and reveal works and never deletes it.
        mockMvc.perform(get("/api/pastes/public"))
                .andExpect(content().string(containsString(id)));
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/pastes/" + id + "/reveal"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value(SECRET))
                    .andExpect(jsonPath("$.burned").value(false));
        }
        assertThat(pasteRepository.findById(id)).isPresent();
    }

    @Test
    void listingsHideContentOfPasswordProtectedPastes() throws Exception {
        String id = create(null, "pw");
        mockMvc.perform(get("/api/pastes/public"))
                .andExpect(content().string(containsString(id)))
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test
    void createValidation_rejectsZeroViews() throws Exception {
        mockMvc.perform(post("/api/pastes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"maxViews\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxViews").exists());
    }

    private static PasteCreateRequest request(int maxViews) {
        PasteCreateRequest r = new PasteCreateRequest("Secret title", SECRET);
        r.setVisibility(Visibility.UNLISTED);
        r.setMaxViews(maxViews);
        return r;
    }
}
