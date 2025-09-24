package com.securepaste.service;

import com.securepaste.dto.PasteCreateRequest;
import com.securepaste.dto.PasteResponse;
import com.securepaste.dto.PasteUpdateRequest;
import com.securepaste.entity.Paste;
import com.securepaste.entity.Visibility;
import com.securepaste.exception.PasteNotFoundException;
import com.securepaste.exception.PasteAccessDeniedException;
import com.securepaste.repository.PasteRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service class for paste management.
 * 
 * Handles business logic for creating, retrieving, updating, and deleting pastes.
 * Also manages expiration cleanup and statistics.
 */
@Service
@Transactional
public class PasteService {

    private final PasteRepository pasteRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public PasteService(PasteRepository pasteRepository, PasswordEncoder passwordEncoder) {
        this.pasteRepository = pasteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Create a new paste.
     */
    public PasteResponse createPaste(PasteCreateRequest request) {
        Paste paste = new Paste();
        paste.setTitle(request.getTitle());
        paste.setContent(request.getContent());
        paste.setLanguage(request.getLanguage());
        paste.setAuthorName(request.getAuthorName());
        paste.setAuthorEmail(request.getAuthorEmail());
        paste.setVisibility(request.getVisibility() != null ? request.getVisibility() : Visibility.PUBLIC);
        
        // Set expiration if provided
        if (request.getExpirationMinutes() != null && request.getExpirationMinutes() > 0) {
            paste.setExpiresAt(LocalDateTime.now().plusMinutes(request.getExpirationMinutes()));
        }
        
        // Hash password if provided
        if (StringUtils.isNotBlank(request.getPassword())) {
            paste.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        
        paste = pasteRepository.save(paste);
        return convertToResponse(paste);
    }

    /**
     * Retrieve a paste by ID.
     */
    @Transactional(readOnly = true)
    public PasteResponse getPaste(String id, String password) {
        Paste paste = findPasteById(id);
        
        // Check if paste is expired
        if (paste.isExpired()) {
            throw new PasteNotFoundException("Paste has expired");
        }
        
        // Check password if required
        if (paste.isPasswordProtected()) {
            if (StringUtils.isBlank(password) || 
                !passwordEncoder.matches(password, paste.getPasswordHash())) {
                throw new PasteAccessDeniedException("Invalid password");
            }
        }
        
        // Increment view count
        pasteRepository.incrementViewCount(id);
        paste.incrementViewCount();
        
        return convertToResponse(paste);
    }

    /**
     * Update an existing paste.
     */
    public PasteResponse updatePaste(String id, PasteUpdateRequest request) {
        Paste paste = findPasteById(id);
        
        if (paste.isExpired()) {
            throw new PasteNotFoundException("Paste has expired");
        }
        
        // Update fields if provided
        if (StringUtils.isNotBlank(request.getTitle())) {
            paste.setTitle(request.getTitle());
        }
        
        if (StringUtils.isNotBlank(request.getContent())) {
            paste.setContent(request.getContent());
        }
        
        if (StringUtils.isNotBlank(request.getLanguage())) {
            paste.setLanguage(request.getLanguage());
        }
        
        if (request.getVisibility() != null) {
            paste.setVisibility(request.getVisibility());
        }
        
        paste = pasteRepository.save(paste);
        return convertToResponse(paste);
    }

    /**
     * Delete a paste (soft delete).
     */
    public void deletePaste(String id) {
        Paste paste = findPasteById(id);
        paste.setIsDeleted(true);
        pasteRepository.save(paste);
    }

    /**
     * Get public pastes with pagination.
     */
    @Transactional(readOnly = true)
    public Page<PasteResponse> getPublicPastes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Paste> pastes = pasteRepository.findPublicAndActive(LocalDateTime.now(), pageable);
        return pastes.map(this::convertToResponse);
    }

    /**
     * Search pastes by term.
     */
    @Transactional(readOnly = true)
    public Page<PasteResponse> searchPastes(String searchTerm, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Paste> pastes = pasteRepository.searchPublicPastes(searchTerm, LocalDateTime.now(), pageable);
        return pastes.map(this::convertToResponse);
    }

    /**
     * Get pastes by programming language.
     */
    @Transactional(readOnly = true)
    public Page<PasteResponse> getPastesByLanguage(String language, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Paste> pastes = pasteRepository.findByLanguageAndActive(language, LocalDateTime.now(), pageable);
        return pastes.map(this::convertToResponse);
    }

    /**
     * Get recent pastes (last 24 hours).
     */
    @Transactional(readOnly = true)
    public Page<PasteResponse> getRecentPastes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Page<Paste> pastes = pasteRepository.findRecentPublicPastes(since, LocalDateTime.now(), pageable);
        return pastes.map(this::convertToResponse);
    }

    /**
     * Get paste statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalPastes", pasteRepository.countActivePastes());
        stats.put("publicPastes", pasteRepository.countPublicPastes());
        
        Long totalViews = pasteRepository.getTotalViewCount();
        stats.put("totalViews", totalViews != null ? totalViews : 0L);
        
        // Get top 10 popular languages
        List<Object[]> popularLanguages = pasteRepository.getPopularLanguages(PageRequest.of(0, 10));
        stats.put("popularLanguages", popularLanguages);
        
        return stats;
    }

    /**
     * Scheduled task to clean up expired pastes.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredPastes() {
        int deletedCount = pasteRepository.deleteExpiredPastes(LocalDateTime.now());
        if (deletedCount > 0) {
            System.out.println("Cleaned up " + deletedCount + " expired pastes");
        }
    }

    /**
     * Find paste by ID or throw exception.
     */
    private Paste findPasteById(String id) {
        return pasteRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new PasteNotFoundException("Paste not found: " + id));
    }

    /**
     * Convert Paste entity to PasteResponse DTO.
     */
    private PasteResponse convertToResponse(Paste paste) {
        PasteResponse response = new PasteResponse();
        response.setId(paste.getId());
        response.setTitle(paste.getTitle());
        response.setContent(paste.getContent());
        response.setLanguage(paste.getLanguage());
        response.setAuthorName(paste.getAuthorName());
        response.setVisibility(paste.getVisibility());
        response.setViewCount(paste.getViewCount());
        response.setCreatedAt(paste.getCreatedAt());
        response.setUpdatedAt(paste.getUpdatedAt());
        response.setExpiresAt(paste.getExpiresAt());
        response.setPasswordProtected(paste.isPasswordProtected());
        return response;
    }
}