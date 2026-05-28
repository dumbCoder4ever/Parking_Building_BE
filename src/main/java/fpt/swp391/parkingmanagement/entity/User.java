package fpt.swp391.parkingmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "Users")
@SQLDelete(sql = "UPDATE users SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
public class User {
@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long userId;
    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "gmail", unique = true)
    private String gmail;

    @Column(name = "phone", unique = true)
    private String phone;

    @Column(nullable = false)
    private String password;

    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private UserStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Enums
    public enum Role {
        ADMIN, MANAGER, STAFF, DRIVER
    }

    public enum UserStatus {
        ACTIVE, INACTIVE, BAN
    }
}
