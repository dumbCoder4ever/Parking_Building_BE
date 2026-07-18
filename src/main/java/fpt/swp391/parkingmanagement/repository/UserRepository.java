package fpt.swp391.parkingmanagement.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.User;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);


    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByUsernameAndUserIdNot(String username, String userId);

    boolean existsByEmailAndUserIdNot(String email, String userId);

    boolean existsByPhoneNumberAndUserIdNot(String phoneNumber, String userId);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findByRoleOrderByFullNameAsc(String role);

    // ============ DASHBOARD STATS ============

    @Query("""
            SELECT u.role, COUNT(u) FROM User u
            WHERE (u.isDeleted IS NULL OR u.isDeleted = false)
            GROUP BY u.role
            """)
    List<Object[]> countActiveGroupedByRole();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND (u.isDeleted IS NULL OR u.isDeleted = false)")
    long countActiveByRole(@Param("role") String role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt < :to AND u.role != 'ROLE_ADMIN' AND (u.isDeleted IS NULL OR u.isDeleted = false)")
    long countNewUsersInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
