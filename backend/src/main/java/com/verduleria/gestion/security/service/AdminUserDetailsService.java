package com.verduleria.gestion.security.service;

import com.verduleria.gestion.security.repository.AdminUserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {
    private final AdminUserRepository repository;

    public AdminUserDetailsService(AdminUserRepository repository) { this.repository = repository; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var admin = repository.findByUsernameIgnoreCase(username.strip())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
        return User.withUsername(admin.getUsername()).password(admin.getPasswordHash())
                .roles(admin.getRole()).disabled(!admin.isActive()).build();
    }
}
