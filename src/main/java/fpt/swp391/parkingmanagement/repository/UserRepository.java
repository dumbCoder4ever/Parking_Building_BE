package fpt.swp391.parkingmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
