package fpt.swp391.parkingmanagement.repository;

import fpt.swp391.parkingmanagement.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByGmail(String gmail);

    Optional<User> findByPhone(String phone);

    boolean existsByUsername(String username);

    boolean existsByGmail(String gmail);

    boolean existsByPhone(String phone);

    boolean existsByUsernameAndUserIdNot(String username, Long userId);

    boolean existsByGmailAndUserIdNot(String gmail, Long userId);

    boolean existsByPhoneAndUserIdNot(String phone, Long userId);

    @Query("SELECT u FROM User u WHERE " +
            "(:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.gmail) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:status IS NULL OR u.status = :status)")
    Page<User> searchUsers(@Param("keyword") String keyword,
                           @Param("role") User.Role role,
                           @Param("status") User.UserStatus status,
                           Pageable pageable);
}
