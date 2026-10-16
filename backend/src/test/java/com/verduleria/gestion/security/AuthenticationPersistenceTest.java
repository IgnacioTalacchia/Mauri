package com.verduleria.gestion.security;

import com.verduleria.gestion.security.repository.AdminUserRepository;
import com.verduleria.gestion.security.service.AdminBootstrapService;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class AuthenticationPersistenceTest {
    private static final String SCHEMA = "auth_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "integration-test-password";
    @Autowired AdminBootstrapService bootstrap;
    @Autowired AdminUserRepository repository;
    @Autowired PasswordEncoder encoder;
    @Autowired WebApplicationContext context;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("app.bootstrap.enabled", () -> "false");
    }

    @AfterAll
    static void removeIsolatedSchema() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("TEST_DB_URL"),
                System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"));
             var statement = connection.createStatement()) {
            // Nombre generado internamente, nunca suministrado por el usuario.
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void concurrentBootstrapCreatesOneAdminAndDatabaseCredentialsAuthenticate() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> createAfterSignal(start, "first-owner"));
            Future<String> second = executor.submit(() -> createAfterSignal(start, "second-owner"));
            start.countDown();
            String firstOwner = first.get(30, TimeUnit.SECONDS);
            String secondOwner = second.get(30, TimeUnit.SECONDS);
            assertTrue((firstOwner == null) != (secondOwner == null));
            assertEquals(1, repository.count());
            String owner = firstOwner != null ? firstOwner : secondOwner;
            var saved = repository.findByUsernameIgnoreCase(owner).orElseThrow();
            assertNotEquals(PASSWORD, saved.getPasswordHash());
            assertTrue(encoder.matches(PASSWORD, saved.getPasswordHash()));

            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var login = mvc.perform(post("/api/auth/login").with(csrf())
                            .param("username", owner).param("password", PASSWORD))
                    .andExpect(status().isNoContent()).andReturn();
            var session = (MockHttpSession) login.getRequest().getSession(false);
            mvc.perform(get("/api/auth/session").session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(owner));
            mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                    .andExpect(status().isNoContent());
            assertTrue(session.isInvalid());
        }
    }

    private String createAfterSignal(CountDownLatch start, String username) throws InterruptedException {
        start.await();
        try {
            bootstrap.createFirstAdmin(username, PASSWORD);
            return username;
        } catch (IllegalStateException alreadyCreated) {
            return null;
        }
    }
}
