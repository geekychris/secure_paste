package com.securepaste.repository;

import com.securepaste.entity.Paste;
import com.securepaste.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Paste entity.
 * 
 * Provides data access methods for paste management including
 * custom queries for filtering, searching, and cleanup operations.
 */
@Repository
public interface PasteRepository extends JpaRepository<Paste, String> {

    /**
     * Find all non-deleted pastes with given visibility, ordered by creation date descending.
     */
    @Query("SELECT p FROM Paste p WHERE p.isDeleted = false AND p.visibility = :visibility ORDER BY p.createdAt DESC")
    Page<Paste> findByVisibilityAndNotDeleted(@Param("visibility") Visibility visibility, Pageable pageable);

    /**
     * Find all public, non-deleted, non-expired pastes.
     */
    @Query("SELECT p FROM Paste p WHERE p.isDeleted = false AND p.visibility = 'PUBLIC' " +
           "AND (p.expiresAt IS NULL OR p.expiresAt > :now) ORDER BY p.createdAt DESC")
    Page<Paste> findPublicAndActive(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * Find a paste by ID, ensuring it's not deleted.
     */
    @Query("SELECT p FROM Paste p WHERE p.id = :id AND p.isDeleted = false")
    Optional<Paste> findByIdAndNotDeleted(@Param("id") String id);

    /**
     * Search pastes by title or content containing the search term.
     */
    @Query("SELECT p FROM Paste p WHERE p.isDeleted = false AND p.visibility = 'PUBLIC' " +
           "AND (p.expiresAt IS NULL OR p.expiresAt > :now) " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(p.content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY p.createdAt DESC")
    Page<Paste> searchPublicPastes(@Param("searchTerm") String searchTerm, 
                                   @Param("now") LocalDateTime now, 
                                   Pageable pageable);

    /**
     * Find pastes by programming language.
     */
    @Query("SELECT p FROM Paste p WHERE p.isDeleted = false AND p.visibility = 'PUBLIC' " +
           "AND (p.expiresAt IS NULL OR p.expiresAt > :now) " +
           "AND LOWER(p.language) = LOWER(:language) ORDER BY p.createdAt DESC")
    Page<Paste> findByLanguageAndActive(@Param("language") String language, 
                                        @Param("now") LocalDateTime now, 
                                        Pageable pageable);

    /**
     * Find pastes by author email.
     */
    @Query("SELECT p FROM Paste p WHERE p.isDeleted = false AND p.authorEmail = :email ORDER BY p.createdAt DESC")
    Page<Paste> findByAuthorEmail(@Param("email") String email, Pageable pageable);

    /**
     * Find expired pastes that should be cleaned up.
     */
    @Query("SELECT p FROM Paste p WHERE p.isDeleted = false AND p.expiresAt IS NOT NULL AND p.expiresAt < :now")
    List<Paste> findExpiredPastes(@Param("now") LocalDateTime now);

    /**
     * Soft delete expired pastes.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Paste p SET p.isDeleted = true WHERE p.expiresAt IS NOT NULL AND p.expiresAt < :now")
    int deleteExpiredPastes(@Param("now") LocalDateTime now);

    /**
     * Update view count for a paste.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Paste p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") String id);

    /**
     * Get paste statistics.
     */
    @Query("SELECT COUNT(p) FROM Paste p WHERE p.isDeleted = false")
    long countActivePastes();

    @Query("SELECT COUNT(p) FROM Paste p WHERE p.isDeleted = false AND p.visibility = 'PUBLIC'")
    long countPublicPastes();

    @Query("SELECT SUM(p.viewCount) FROM Paste p WHERE p.isDeleted = false")
    Long getTotalViewCount();

    /**
     * Get popular languages (top 10).
     */
    @Query("SELECT p.language, COUNT(p) as count FROM Paste p " +
           "WHERE p.isDeleted = false AND p.language IS NOT NULL AND p.language != '' " +
           "GROUP BY p.language ORDER BY count DESC")
    List<Object[]> getPopularLanguages(Pageable pageable);

    /**
     * Find recent pastes (last 24 hours).
     */
    @Query("SELECT p FROM Paste p WHERE p.isDeleted = false AND p.visibility = 'PUBLIC' " +
           "AND p.createdAt > :since AND (p.expiresAt IS NULL OR p.expiresAt > :now) " +
           "ORDER BY p.createdAt DESC")
    Page<Paste> findRecentPublicPastes(@Param("since") LocalDateTime since, 
                                       @Param("now") LocalDateTime now, 
                                       Pageable pageable);
}