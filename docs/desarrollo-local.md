# Desarrollo local en Windows

Abrir en Visual Studio Code la carpeta que contiene este README y `backend`.
Requisitos: JDK 21, Maven 3.6.3 o superior y PostgreSQL 17, instalado directamente
o mediante Docker Desktop con Docker Compose. Extensiones sugeridas: Extension Pack
for Java y Spring Boot Extension Pack.

## 1. Iniciar PostgreSQL con Docker

Desde la raíz del proyecto, en PowerShell:

```powershell
Copy-Item .env.example .env
code .env
```

Definir una contraseña propia en `POSTGRES_PASSWORD`. El archivo `.env` está
excluido de Git. No usar credenciales de producción en el entorno local.

```powershell
docker compose up -d db
docker compose ps
```

La base queda disponible solamente desde la computadora local. Los datos persisten
en el volumen `postgres_data`. `docker compose down` detiene los servicios;
no usar `down -v` si se quieren conservar los datos.

Alternativa sin Docker: crear una base `verduleria` y un usuario propio en una
instalación local de PostgreSQL. El usuario debe poder crear tablas y migraciones
en esa base. No hace falta ejecutar manualmente el SQL de Flyway.

## 2. Ejecutar el backend

En la terminal donde se ejecutará Java, definir las variables. Spring Boot **no lee
automáticamente** el `.env` usado por Compose. Si se cambiaron nombre, usuario o
puerto en Compose, usar esos mismos valores aquí.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/verduleria'
$env:DB_USER = 'verduleria'
$dbCredential = Read-Host 'Contraseña de PostgreSQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbCredential).Password
Set-Location backend
mvn spring-boot:run
```

Flyway aplica `V1__initial_schema.sql` al iniciar. La aplicación no recrea ni borra
tablas. Las futuras modificaciones deben ir en nuevas migraciones `V2__...sql`,
sin editar migraciones ya aplicadas a una base compartida.

Desde otra terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Resultado esperado: `status: UP`. Para crear el primer administrador y probar los
endpoints de autenticación, seguir [autenticación](autenticacion.md). No hay
credenciales predeterminadas; las rutas de negocio requieren rol ADMIN.

Si falta `DB_PASSWORD` o PostgreSQL no está disponible, el arranque fallará.
Verificar servicio, puerto, base y credenciales, sin compartir contraseñas ni logs
que contengan datos personales.

## 3. Pruebas y empaquetado

Desde `backend`:

```powershell
mvn test
mvn package
```

Las pruebas de seguridad se ejecutan sin base de datos. La prueba real de migración
se omite salvo que se defina `TEST_DB_URL`. Para ejecutarla, usar una **base de
pruebas**, donde el usuario pueda crear y eliminar esquemas:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/verduleria_test'
$env:TEST_DB_USER = $env:DB_USER
$env:TEST_DB_PASSWORD = $env:DB_PASSWORD
mvn test
```

La prueba crea un esquema aleatorio, aplica Flyway, verifica restricciones y elimina
ese esquema al terminar. La base `verduleria_test` debe existir previamente.

El JAR se ejecuta con las mismas variables de entorno:

```powershell
java -jar target/gestion-0.0.1-SNAPSHOT.jar
```

## Próxima etapa

El acceso del administrador ya está implementado; ver [autenticación](autenticacion.md).
La siguiente etapa será el frontend React con login y panel. El webhook público se
habilitará recién al implementar y verificar
la firma de Meta. Para producción faltan HTTPS, secretos del servidor, copias de
seguridad con restauración probada y configuración operativa.
