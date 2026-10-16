package com.verduleria.gestion.security;

import com.verduleria.gestion.security.controller.AuthController;
import com.verduleria.gestion.security.entity.AdminUser;
import com.verduleria.gestion.security.repository.AdminUserRepository;
import com.verduleria.gestion.security.service.AdminUserDetailsService;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityConfigTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private static final String PASSWORD = "valid-test-password";

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var repository = context.getBean(AdminUserRepository.class);
        var encoder = context.getBean(PasswordEncoder.class);
        when(repository.findByUsernameIgnoreCase("admin"))
                .thenReturn(Optional.of(new AdminUser("admin", encoder.encode(PASSWORD))));
        AdminUser disabled = mock(AdminUser.class);
        when(disabled.getUsername()).thenReturn("disabled");
        when(disabled.getPasswordHash()).thenReturn(encoder.encode(PASSWORD));
        when(disabled.getRole()).thenReturn("ADMIN");
        when(disabled.isActive()).thenReturn(false);
        when(repository.findByUsernameIgnoreCase("disabled")).thenReturn(Optional.of(disabled));
    }

    @AfterEach
    void tearDown() { context.close(); }

    @Test
    void anonymousRequestsAndRolesAreChecked() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void loginPersistsSessionRotatesIdAndLogoutInvalidatesIt() throws Exception {
        MvcResult initial = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        MockHttpSession session = (MockHttpSession) initial.getRequest().getSession(false);
        String previousId = session.getId();
        CsrfToken token = (CsrfToken) initial.getRequest().getAttribute(CsrfToken.class.getName());
        mvc.perform(post("/api/auth/login").session(session)
                        .header(token.getHeaderName(), token.getToken())
                        .param("username", "admin").param("password", PASSWORD))
                .andExpect(status().isNoContent());
        assertNotEquals(previousId, session.getId());
        mvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/orders").session(session)
                        .header(token.getHeaderName(), token.getToken()))
                .andExpect(status().isForbidden());
        MvcResult refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        CsrfToken logoutToken = (CsrfToken) refreshed.getRequest().getAttribute(CsrfToken.class.getName());
        mvc.perform(post("/api/auth/logout").session(session)
                        .header(logoutToken.getHeaderName(), logoutToken.getToken()))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfIsRequiredForLoginLogoutAndMutations() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf().useInvalidToken())
                        .param("username", "admin").param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongUnknownAndDisabledCredentialsReturnSameError() throws Exception {
        for (String username : new String[]{"admin", "missing", "disabled"}) {
            mvc.perform(post("/api/auth/login").with(csrf()).param("username", username)
                            .param("password", username.equals("admin") ? "wrong" : PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"message\":\"Credenciales inválidas\"}"));
        }
    }

    @Test
    void passwordsAreSaltedAndVerified() {
        PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
        String first = encoder.encode(PASSWORD);
        assertNotEquals(first, encoder.encode(PASSWORD));
        assertTrue(encoder.matches(PASSWORD, first));
        assertFalse(encoder.matches("wrong-password", first));
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthController.class, AdminUserDetailsService.class, TestController.class})
    static class TestConfig {
        @Bean AdminUserRepository repository() { return mock(AdminUserRepository.class); }
    }
    @RestController
    static class TestController {
        @GetMapping("/actuator/health") String health() { return "ok"; }
        @GetMapping("/api/orders") String orders() { return "private"; }
        @PostMapping("/api/orders") String createOrder() { return "test-only"; }
    }
}
