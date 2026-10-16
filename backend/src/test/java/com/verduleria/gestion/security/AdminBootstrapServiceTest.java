package com.verduleria.gestion.security;

import com.verduleria.gestion.security.entity.AdminUser;
import com.verduleria.gestion.security.repository.AdminUserRepository;
import com.verduleria.gestion.security.service.AdminBootstrapService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapServiceTest {
    private final AdminUserRepository repository = mock(AdminUserRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AdminBootstrapService service = new AdminBootstrapService(repository, encoder, entityManager);

    @BeforeEach
    void setUp() {
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);
    }

    @Test
    void createsOnlyHashedCredentialsAfterAcquiringLock() {
        service.createFirstAdmin("Owner", "a-long-test-password");
        var captor = ArgumentCaptor.forClass(AdminUser.class);
        var ordered = inOrder(entityManager, repository);
        ordered.verify(entityManager).createNativeQuery(contains("FOR UPDATE"));
        ordered.verify(repository).count();
        ordered.verify(repository).saveAndFlush(captor.capture());
        var admin = captor.getValue();
        assertEquals("owner", admin.getUsername());
        assertTrue(encoder.matches("a-long-test-password", admin.getPasswordHash()));
        assertTrue(admin.isActive());
        assertEquals("ADMIN", admin.getRole());
    }

    @Test
    void refusesToOverwriteExistingAdmin() {
        when(repository.count()).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.createFirstAdmin("owner", "a-long-test-password"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingWeakOrTooLongCredentials() {
        for (String password : new String[]{null, "short", " ".repeat(12), "a".repeat(73), "á".repeat(37)}) {
            assertThrows(IllegalArgumentException.class, () -> service.createFirstAdmin("owner", password));
        }
        for (String username : new String[]{null, "ab", "owner name", "a".repeat(101)}) {
            assertThrows(IllegalArgumentException.class, () -> service.createFirstAdmin(username, "a-long-test-password"));
        }
        verifyNoInteractions(repository, entityManager);
    }
}
