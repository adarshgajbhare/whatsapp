package com.chatapp.whatsapp.respository;

import com.chatapp.whatsapp.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Existing methods
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.username = :usernameOrEmail OR u.email = :usernameOrEmail")
    Optional<User> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.isActive = true")
    List<User> findAllActiveUsers();

    // NEW: Search methods
    List<User> findByUsernameContainingIgnoreCaseAndIsActiveTrue(String username, Pageable pageable);

    // Find users by username containing a string, excluding a specific user ID, and only active users
    List<User> findByUsernameContainingIgnoreCaseAndIdNotAndIsActiveTrue(String username, Long userId);

    Optional<User> findByUsernameAndIsActiveTrue(String username);

    List<User> findByIsActiveTrueOrderByUsernameAsc(Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
            "u.isActive = true")
    List<User> searchActiveUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

    long countByIsActiveTrue();

    List<User> findByUsernameContainingIgnoreCase(String username);

       long countByIdIn(Collection<Long> ids);
}
