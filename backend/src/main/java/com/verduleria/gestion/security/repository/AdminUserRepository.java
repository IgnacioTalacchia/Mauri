package com.verduleria.gestion.security.repository;

import com.verduleria.gestion.security.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    Optional<AdminUser> findByUsernameIgnoreCase(String username);
}
