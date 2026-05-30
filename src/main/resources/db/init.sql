CREATE DATABASE IF NOT EXISTS parking_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE parking_db;

-- Xoa schema cu (Users/id/password) de tranh xung dot voi entity User.java
DROP TABLE IF EXISTS `Users`;
DROP TABLE IF EXISTS `users`;

CREATE TABLE `users` (
  `user_id`      VARCHAR(36)  NOT NULL,
  `username`     VARCHAR(50)  NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `full_name`    VARCHAR(100) DEFAULT NULL,
  `phone_number` VARCHAR(20)  DEFAULT NULL,
  `email`        VARCHAR(100) DEFAULT NULL,
  `avatar_url`   VARCHAR(255) DEFAULT NULL,
  `role`         VARCHAR(20)  NOT NULL COMMENT 'ROLE_ADMIN, ROLE_MANAGER, ROLE_STAFF, ROLE_DRIVER',
  `status`       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  `is_deleted`   TINYINT(1)   NOT NULL DEFAULT 0,
  `deleted_at`   DATETIME     DEFAULT NULL,
  `last_login`   DATETIME     DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK_users_username` (`username`),
  UNIQUE KEY `UK_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mat khau da hash bang BCrypt (Spring Security)
-- admin  -> 123
-- driver1 -> 1
INSERT INTO `users` (
  `user_id`,
  `username`,
  `password_hash`,
  `full_name`,
  `role`,
  `status`
) VALUES
(
  '11111111-1111-1111-1111-111111111111',
  'admin',
  '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea',
  'Admin',
  'ROLE_ADMIN',
  'ACTIVE'
),
(
  '22222222-2222-2222-2222-222222222222',
  'driver1',
  '$2a$10$3R/XmppgsdJqbVcjBda5m.qNC8S.4u8cSb9n9yYoTuCg8SFWn/r0y',
  'Driver',
  'ROLE_DRIVER',
  'ACTIVE'
);
