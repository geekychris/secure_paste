package com.securepaste.controller;

import com.securepaste.dto.PasteCreateRequest;
import com.securepaste.dto.PasteResponse;
import com.securepaste.dto.PasteUpdateRequest;
import com.securepaste.service.PasteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for paste operations.
 * 
 * Provides endpoints for creating, retrieving, updating, and deleting pastes.
 */
@RestController
@RequestMapping("/api/pastes")
@Tag(name = "Paste API", description = "Operations for managing code pastes")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PasteController {

    private final PasteService pasteService;

    @Autowired
    public PasteController(PasteService pasteService) {
        this.pasteService = pasteService;
    }

    /**
     * Create a new paste.
     */
    @PostMapping
    @Operation(summary = "Create a new paste", description = "Creates a new paste with the provided content and metadata")
    public ResponseEntity<PasteResponse> createPaste(@Valid @RequestBody PasteCreateRequest request) {
        PasteResponse response = pasteService.createPaste(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieve a paste by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get paste by ID", description = "Retrieves a paste by its unique identifier")
    public ResponseEntity<PasteResponse> getPaste(
            @Parameter(description = "Paste ID") @PathVariable String id,
            @Parameter(description = "Password for protected pastes") @RequestParam(required = false) String password) {
        PasteResponse response = pasteService.getPaste(id, password);
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing paste.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update paste", description = "Updates an existing paste with new content or metadata")
    public ResponseEntity<PasteResponse> updatePaste(
            @Parameter(description = "Paste ID") @PathVariable String id,
            @Valid @RequestBody PasteUpdateRequest request) {
        PasteResponse response = pasteService.updatePaste(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a paste.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete paste", description = "Soft deletes a paste by marking it as deleted")
    public ResponseEntity<Void> deletePaste(@Parameter(description = "Paste ID") @PathVariable String id) {
        pasteService.deletePaste(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get public pastes with pagination.
     */
    @GetMapping("/public")
    @Operation(summary = "Get public pastes", description = "Retrieves public pastes with pagination")
    public ResponseEntity<Page<PasteResponse>> getPublicPastes(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Page<PasteResponse> pastes = pasteService.getPublicPastes(page, size);
        return ResponseEntity.ok(pastes);
    }

    /**
     * Search pastes by content or title.
     */
    @GetMapping("/search")
    @Operation(summary = "Search pastes", description = "Searches public pastes by title or content")
    public ResponseEntity<Page<PasteResponse>> searchPastes(
            @Parameter(description = "Search term") @RequestParam String q,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Page<PasteResponse> pastes = pasteService.searchPastes(q, page, size);
        return ResponseEntity.ok(pastes);
    }

    /**
     * Get pastes by programming language.
     */
    @GetMapping("/language/{language}")
    @Operation(summary = "Get pastes by language", description = "Retrieves pastes filtered by programming language")
    public ResponseEntity<Page<PasteResponse>> getPastesByLanguage(
            @Parameter(description = "Programming language") @PathVariable String language,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Page<PasteResponse> pastes = pasteService.getPastesByLanguage(language, page, size);
        return ResponseEntity.ok(pastes);
    }

    /**
     * Get recent pastes (last 24 hours).
     */
    @GetMapping("/recent")
    @Operation(summary = "Get recent pastes", description = "Retrieves recent public pastes from the last 24 hours")
    public ResponseEntity<Page<PasteResponse>> getRecentPastes(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Page<PasteResponse> pastes = pasteService.getRecentPastes(page, size);
        return ResponseEntity.ok(pastes);
    }

    /**
     * Get paste statistics.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get statistics", description = "Retrieves overall paste statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = pasteService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Simple health check endpoint")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "SecurePaste"
        ));
    }
}