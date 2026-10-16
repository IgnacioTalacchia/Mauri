# Mauri · Gestión de verdulería

Sistema de pedidos con Java 21, Spring Boot, PostgreSQL y, en etapas posteriores,
React + TypeScript e integración con WhatsApp Business Cloud API.

## Estado actual

Backend inicial con esquema versionado por Flyway y autenticación de administradores
persistidos en PostgreSQL. Incluye alta inicial controlada, login/logout por sesión,
BCrypt, CSRF y pruebas. Todavía no hay endpoints de negocio, frontend ni conexión
con Meta. No se crean usuarios ni productos ficticios.

## Estructura

```text
backend/
  pom.xml
  src/main/java/com/verduleria/gestion/
    GestionApplication.java
    security/SecurityConfig.java
  src/main/resources/
    application.yml
    db/migration/V1__initial_schema.sql
  src/test/java/com/verduleria/gestion/
docs/
  arquitectura.md
  desarrollo-local.md
compose.yaml
.env.example
```

Empezá por [la guía de ejecución local](docs/desarrollo-local.md).
El diseño y las siguientes etapas están en [arquitectura](docs/arquitectura.md).
Para crear el administrador y probar el login, seguir [autenticación](docs/autenticacion.md).
