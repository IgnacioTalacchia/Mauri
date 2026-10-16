package com.verduleria.gestion.security;

import com.verduleria.gestion.security.service.AdminBootstrapService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminBootstrapService service;
    private final Environment environment;

    public AdminBootstrapRunner(AdminBootstrapService service, Environment environment) {
        this.service = service;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.createFirstAdmin(environment.getProperty("app.bootstrap.username"),
                environment.getProperty("app.bootstrap.password"));
    }
}
