package com.verduleria.gestion.security.service;

import com.verduleria.gestion.security.entity.AdminUser;
import com.verduleria.gestion.security.repository.AdminUserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {
    private final AdminUserRepository repository;
    private final PasswordEncoder encoder;
    private final EntityManager entityManager;

    public AdminBootstrapService(AdminUserRepository repository, PasswordEncoder encoder,
                                 EntityManager entityManager) {
        this.repository = repository;
        this.encoder = encoder;
        this.entityManager = entityManager;
    }

    @Transactional
    public void createFirstAdmin(String username, String password) {
        if (username == null || !username.matches("[a-zA-Z0-9._-]{3,100}")) {
            throw new IllegalArgumentException("El usuario debe tener entre 3 y 100 letras, números, puntos, guiones o guiones bajos.");
        }
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 12 caracteres y como máximo 72 bytes UTF-8.");
        }
        entityManager.createNativeQuery("SELECT id FROM admin_bootstrap_guard WHERE id = 1 FOR UPDATE")
                .getSingleResult();
        if (repository.count() != 0) {
            throw new IllegalStateException("Ya existe un administrador. Deshabilitá el alta inicial; no se modificaron credenciales.");
        }
        repository.saveAndFlush(new AdminUser(username.toLowerCase(Locale.ROOT), encoder.encode(password)));
    }
}
