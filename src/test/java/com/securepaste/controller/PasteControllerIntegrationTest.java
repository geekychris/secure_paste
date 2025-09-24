package com.securepaste.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepaste.dto.PasteCreateRequest;
import com.securepaste.entity.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for PasteController.
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class PasteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createPaste_Success() throws Exception {
        PasteCreateRequest request = new PasteCreateRequest();
        request.setTitle("Integration Test Paste");
        request.setContent("public class HelloWorld {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}");
        request.setLanguage("java");
        request.setAuthorName("Test Author");
        request.setVisibility(Visibility.PUBLIC);

        mockMvc.perform(post("/api/pastes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Integration Test Paste"))
                .andExpect(jsonPath("$.content").value(request.getContent()))
                .andExpect(jsonPath("$.language").value("java"))
                .andExpect(jsonPath("$.authorName").value("Test Author"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.viewCount").value(0));
    }

    @Test
    void createPaste_ValidationError() throws Exception {
        PasteCreateRequest request = new PasteCreateRequest();
        // Missing required fields

        mockMvc.perform(post("/api/pastes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void createAndGetPaste_Success() throws Exception {
        // First create a paste
        PasteCreateRequest createRequest = new PasteCreateRequest();
        createRequest.setTitle("Test Paste");
        createRequest.setContent("Test content for getting");
        createRequest.setLanguage("text");
        createRequest.setVisibility(Visibility.PUBLIC);

        String createResponse = mockMvc.perform(post("/api/pastes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract the ID from the response
        var responseMap = objectMapper.readValue(createResponse, java.util.Map.class);
        String pasteId = (String) responseMap.get("id");

        // Then get the paste
        mockMvc.perform(get("/api/pastes/" + pasteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pasteId))
                .andExpect(jsonPath("$.title").value("Test Paste"))
                .andExpect(jsonPath("$.content").value("Test content for getting"))
                .andExpect(jsonPath("$.language").value("text"))
                .andExpect(jsonPath("$.viewCount").value(1)); // Should be incremented
    }

    @Test
    void getPaste_NotFound() throws Exception {
        mockMvc.perform(get("/api/pastes/non-existent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void createPasteWithPassword_Success() throws Exception {
        PasteCreateRequest request = new PasteCreateRequest();
        request.setTitle("Password Protected Paste");
        request.setContent("This is a secret message");
        request.setPassword("secret123");
        request.setVisibility(Visibility.UNLISTED);

        String createResponse = mockMvc.perform(post("/api/pastes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordProtected").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var responseMap = objectMapper.readValue(createResponse, java.util.Map.class);
        String pasteId = (String) responseMap.get("id");

        // Try to access without password - should fail
        mockMvc.perform(get("/api/pastes/" + pasteId))
                .andExpect(status().isForbidden());

        // Try to access with correct password - should succeed
        mockMvc.perform(get("/api/pastes/" + pasteId)
                .param("password", "secret123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Password Protected Paste"));
    }

    @Test
    void getPublicPastes_Success() throws Exception {
        // Create a public paste first
        PasteCreateRequest request = new PasteCreateRequest();
        request.setTitle("Public Test Paste");
        request.setContent("This is public content");
        request.setVisibility(Visibility.PUBLIC);

        mockMvc.perform(post("/api/pastes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Get public pastes
        mockMvc.perform(get("/api/pastes/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.size").value(20)) // Default page size
                .andExpect(jsonPath("$.number").value(0)); // First page
    }

    @Test
    void searchPastes_Success() throws Exception {
        // Create a searchable paste
        PasteCreateRequest request = new PasteCreateRequest();
        request.setTitle("Searchable Java Code");
        request.setContent("public class SearchTest { }");
        request.setLanguage("java");
        request.setVisibility(Visibility.PUBLIC);

        mockMvc.perform(post("/api/pastes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Search for the paste
        mockMvc.perform(get("/api/pastes/search")
                .param("q", "SearchTest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getPastesByLanguage_Success() throws Exception {
        // Create a Python paste
        PasteCreateRequest request = new PasteCreateRequest();
        request.setTitle("Python Test");
        request.setContent("print('Hello, World!')");
        request.setLanguage("python");
        request.setVisibility(Visibility.PUBLIC);

        mockMvc.perform(post("/api/pastes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Get Python pastes
        mockMvc.perform(get("/api/pastes/language/python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getRecentPastes_Success() throws Exception {
        mockMvc.perform(get("/api/pastes/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void getStatistics_Success() throws Exception {
        mockMvc.perform(get("/api/pastes/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPastes").isNumber())
                .andExpect(jsonPath("$.publicPastes").isNumber())
                .andExpect(jsonPath("$.totalViews").isNumber())
                .andExpect(jsonPath("$.popularLanguages").isArray());
    }

    @Test
    void healthCheck_Success() throws Exception {
        mockMvc.perform(get("/api/pastes/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("SecurePaste"));
    }

    @Test
    void deletePaste_Success() throws Exception {
        // Create a paste first
        PasteCreateRequest request = new PasteCreateRequest();
        request.setTitle("To Be Deleted");
        request.setContent("This paste will be deleted");

        String createResponse = mockMvc.perform(post("/api/pastes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var responseMap = objectMapper.readValue(createResponse, java.util.Map.class);
        String pasteId = (String) responseMap.get("id");

        // Delete the paste
        mockMvc.perform(delete("/api/pastes/" + pasteId))
                .andExpect(status().isNoContent());

        // Verify it's deleted
        mockMvc.perform(get("/api/pastes/" + pasteId))
                .andExpect(status().isNotFound());
    }
}