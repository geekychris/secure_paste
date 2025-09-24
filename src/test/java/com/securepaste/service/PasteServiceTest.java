package com.securepaste.service;

import com.securepaste.dto.PasteCreateRequest;
import com.securepaste.dto.PasteResponse;
import com.securepaste.entity.Paste;
import com.securepaste.entity.Visibility;
import com.securepaste.exception.PasteNotFoundException;
import com.securepaste.repository.PasteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PasteService.
 */
@ExtendWith(MockitoExtension.class)
class PasteServiceTest {

    @Mock
    private PasteRepository pasteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasteService pasteService;

    private Paste testPaste;
    private PasteCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        testPaste = new Paste("Test Title", "Test Content");
        testPaste.setId("test-id");
        testPaste.setLanguage("java");
        testPaste.setAuthorName("Test Author");
        testPaste.setVisibility(Visibility.PUBLIC);
        testPaste.setCreatedAt(LocalDateTime.now());
        testPaste.setUpdatedAt(LocalDateTime.now());
        testPaste.setViewCount(0L);

        createRequest = new PasteCreateRequest();
        createRequest.setTitle("Test Title");
        createRequest.setContent("Test Content");
        createRequest.setLanguage("java");
        createRequest.setAuthorName("Test Author");
        createRequest.setVisibility(Visibility.PUBLIC);
    }

    @Test
    void createPaste_Success() {
        // Given
        when(pasteRepository.save(any(Paste.class))).thenReturn(testPaste);

        // When
        PasteResponse response = pasteService.createPaste(createRequest);

        // Then
        assertNotNull(response);
        assertEquals("Test Title", response.getTitle());
        assertEquals("Test Content", response.getContent());
        assertEquals("java", response.getLanguage());
        assertEquals("Test Author", response.getAuthorName());
        assertEquals(Visibility.PUBLIC, response.getVisibility());
        verify(pasteRepository).save(any(Paste.class));
    }

    @Test
    void createPaste_WithPassword() {
        // Given
        createRequest.setPassword("secret");
        when(passwordEncoder.encode("secret")).thenReturn("hashed-password");
        when(pasteRepository.save(any(Paste.class))).thenReturn(testPaste);

        // When
        PasteResponse response = pasteService.createPaste(createRequest);

        // Then
        assertNotNull(response);
        verify(passwordEncoder).encode("secret");
        verify(pasteRepository).save(any(Paste.class));
    }

    @Test
    void createPaste_WithExpiration() {
        // Given
        createRequest.setExpirationMinutes(60);
        when(pasteRepository.save(any(Paste.class))).thenReturn(testPaste);

        // When
        PasteResponse response = pasteService.createPaste(createRequest);

        // Then
        assertNotNull(response);
        verify(pasteRepository).save(argThat(paste -> 
            paste.getExpiresAt() != null && 
            paste.getExpiresAt().isAfter(LocalDateTime.now())
        ));
    }

    @Test
    void getPaste_Success() {
        // Given
        when(pasteRepository.findByIdAndNotDeleted("test-id")).thenReturn(Optional.of(testPaste));

        // When
        PasteResponse response = pasteService.getPaste("test-id", null);

        // Then
        assertNotNull(response);
        assertEquals("Test Title", response.getTitle());
        assertEquals("Test Content", response.getContent());
        verify(pasteRepository).incrementViewCount("test-id");
    }

    @Test
    void getPaste_NotFound() {
        // Given
        when(pasteRepository.findByIdAndNotDeleted("non-existent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(PasteNotFoundException.class, () -> 
            pasteService.getPaste("non-existent", null)
        );
    }

    @Test
    void getPaste_Expired() {
        // Given
        testPaste.setExpiresAt(LocalDateTime.now().minusHours(1)); // Expired
        when(pasteRepository.findByIdAndNotDeleted("test-id")).thenReturn(Optional.of(testPaste));

        // When & Then
        assertThrows(PasteNotFoundException.class, () -> 
            pasteService.getPaste("test-id", null)
        );
    }

    @Test
    void getPaste_PasswordProtected_CorrectPassword() {
        // Given
        testPaste.setPasswordHash("hashed-password");
        when(pasteRepository.findByIdAndNotDeleted("test-id")).thenReturn(Optional.of(testPaste));
        when(passwordEncoder.matches("secret", "hashed-password")).thenReturn(true);

        // When
        PasteResponse response = pasteService.getPaste("test-id", "secret");

        // Then
        assertNotNull(response);
        verify(passwordEncoder).matches("secret", "hashed-password");
    }

    @Test
    void getPaste_PasswordProtected_WrongPassword() {
        // Given
        testPaste.setPasswordHash("hashed-password");
        when(pasteRepository.findByIdAndNotDeleted("test-id")).thenReturn(Optional.of(testPaste));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        // When & Then
        assertThrows(com.securepaste.exception.PasteAccessDeniedException.class, () -> 
            pasteService.getPaste("test-id", "wrong")
        );
    }

    @Test
    void deletePaste_Success() {
        // Given
        when(pasteRepository.findByIdAndNotDeleted("test-id")).thenReturn(Optional.of(testPaste));
        when(pasteRepository.save(any(Paste.class))).thenReturn(testPaste);

        // When
        pasteService.deletePaste("test-id");

        // Then
        verify(pasteRepository).save(argThat(paste -> paste.getIsDeleted()));
    }

    @Test
    void getPublicPastes_Success() {
        // Given
        List<Paste> pasteList = List.of(testPaste);
        Page<Paste> pastePage = new PageImpl<>(pasteList);
        when(pasteRepository.findPublicAndActive(any(LocalDateTime.class), any(PageRequest.class)))
            .thenReturn(pastePage);

        // When
        Page<PasteResponse> response = pasteService.getPublicPastes(0, 10);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Test Title", response.getContent().get(0).getTitle());
    }

    @Test
    void searchPastes_Success() {
        // Given
        List<Paste> pasteList = List.of(testPaste);
        Page<Paste> pastePage = new PageImpl<>(pasteList);
        when(pasteRepository.searchPublicPastes(eq("test"), any(LocalDateTime.class), any(PageRequest.class)))
            .thenReturn(pastePage);

        // When
        Page<PasteResponse> response = pasteService.searchPastes("test", 0, 10);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        verify(pasteRepository).searchPublicPastes(eq("test"), any(LocalDateTime.class), any(PageRequest.class));
    }

    @Test
    void getPastesByLanguage_Success() {
        // Given
        List<Paste> pasteList = List.of(testPaste);
        Page<Paste> pastePage = new PageImpl<>(pasteList);
        when(pasteRepository.findByLanguageAndActive(eq("java"), any(LocalDateTime.class), any(PageRequest.class)))
            .thenReturn(pastePage);

        // When
        Page<PasteResponse> response = pasteService.getPastesByLanguage("java", 0, 10);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("java", response.getContent().get(0).getLanguage());
    }

    @Test
    void getStatistics_Success() {
        // Given
        when(pasteRepository.countActivePastes()).thenReturn(100L);
        when(pasteRepository.countPublicPastes()).thenReturn(80L);
        when(pasteRepository.getTotalViewCount()).thenReturn(1500L);

        // When
        var stats = pasteService.getStatistics();

        // Then
        assertNotNull(stats);
        assertEquals(100L, stats.get("totalPastes"));
        assertEquals(80L, stats.get("publicPastes"));
        assertEquals(1500L, stats.get("totalViews"));
    }

    @Test
    void cleanupExpiredPastes_Success() {
        // Given
        when(pasteRepository.deleteExpiredPastes(any(LocalDateTime.class))).thenReturn(5);

        // When
        pasteService.cleanupExpiredPastes();

        // Then
        verify(pasteRepository).deleteExpiredPastes(any(LocalDateTime.class));
    }
}