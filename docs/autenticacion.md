# Acceso del administrador

La conexión PostgreSQL existente no cambia. Flyway aplicará automáticamente
`V2__admin_bootstrap.sql` al arrancar; no hay que ejecutar SQL manualmente ni
modificar V1. El administrador se guarda en `admin_user`; solo se persiste el hash
BCrypt de su contraseña.

## Crear el primer administrador

Detener el backend anterior para liberar el puerto. En una terminal PowerShell
ubicada en `backend`, conservar las variables de conexión que ya funcionan.
Ejecutar este bloque completo:

```powershell
$env:APP_BOOTSTRAP_ENABLED = 'true'
$env:APP_BOOTSTRAP_USERNAME = Read-Host 'Elegí tu usuario administrador'
$bootstrapSecret = Read-Host 'Elegí una contraseña de al menos 12 caracteres' -AsSecureString
$env:APP_BOOTSTRAP_PASSWORD = [System.Net.NetworkCredential]::new('', $bootstrapSecret).Password
try {
    mvn spring-boot:run
} finally {
    Remove-Item Env:APP_BOOTSTRAP_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:APP_BOOTSTRAP_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:APP_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable bootstrapSecret -ErrorAction SilentlyContinue
}
```

El usuario admite 3–100 letras ASCII, números, puntos, guiones y guiones bajos.
Se guarda en minúsculas y se permite iniciar sesión sin distinguir mayúsculas.
La contraseña admite al menos 12 caracteres y como máximo 72 bytes UTF-8, límite
de BCrypt; no se recorta ni se registra. Elegir una contraseña larga y única.

Cuando termine el arranque, el administrador ya estará persistido. Detener con
Ctrl+C para que el bloque quite las variables temporales. Volver a iniciar con:

```powershell
mvn spring-boot:run
```

El alta inicial está deshabilitada por defecto. Si se la habilita cuando ya existe
cualquier administrador, el arranque falla sin sobrescribirlo. Una fila de bloqueo
en PostgreSQL serializa intentos simultáneos de creación. No existe endpoint de
registro público. No guardar estas variables de alta en archivos ni argumentos.

## Contrato HTTP

| Método y ruta | Resultado |
| --- | --- |
| `GET /api/auth/csrf` | Público; crea sesión si hace falta y devuelve `headerName`, `parameterName`, `token`. |
| `POST /api/auth/login` | Formulario URL encoded con `username` y `password`, cookie y token CSRF. Éxito: 204. |
| `GET /api/auth/session` | Sesión ADMIN: 200 con `username` y `roles`; sin sesión: 401. |
| `POST /api/auth/logout` | Requiere CSRF; invalida sesión, elimina cookie y devuelve 204. |

El login **no recibe JSON**. React enviará `URLSearchParams` con
`Content-Type: application/x-www-form-urlencoded` y conservará las cookies.
No hay tokens JWT ni contraseñas en las respuestas. Usuario desconocido, contraseña
incorrecta y cuenta inactiva responden el mismo 401 genérico.

CSRF permanece habilitado, también para login y logout. Obtener un nuevo token
después de iniciar o cerrar sesión; el anterior se elimina. La sesión cambia de
identificador al autenticar para prevenir fijación de sesión. La cookie ya estaba
configurada HttpOnly y SameSite=Lax; en el perfil `production` requiere HTTPS.
No se habilita CORS global. El frontend podrá usar un proxy de desarrollo para
compartir origen con `/api`.

Las rutas `/api/**` requieren rol ADMIN salvo los accesos públicos indicados.
Las rutas restantes se rechazan por defecto. El endpoint de salud sigue público.
Los endpoints de pedidos usados en las pruebas son controladores de prueba y no
se publican en la aplicación.

## Comprobar con PowerShell

Con el backend ejecutándose normalmente, abrir otra terminal y ejecutar:

```powershell
$baseUrl = 'http://localhost:8080'
$csrf = Invoke-RestMethod "$baseUrl/api/auth/csrf" -SessionVariable authSession
$headers = @{}
$headers[$csrf.headerName] = $csrf.token
$loginUser = Read-Host 'Usuario administrador'
$loginSecret = Read-Host 'Contraseña' -AsSecureString
$loginBody = @{
    username = $loginUser
    password = [System.Net.NetworkCredential]::new('', $loginSecret).Password
}
try {
    $result = Invoke-WebRequest "$baseUrl/api/auth/login" -Method Post `
        -WebSession $authSession -Headers $headers `
        -ContentType 'application/x-www-form-urlencoded' -Body $loginBody -UseBasicParsing
    $result.StatusCode # 204
} finally {
    $loginBody.Clear()
    Remove-Variable loginSecret -ErrorAction SilentlyContinue
}
Invoke-RestMethod "$baseUrl/api/auth/session" -WebSession $authSession
```

Resultado de sesión: tu usuario y `roles: ["ADMIN"]`. No se devuelve el hash.
Para cerrar sesión:

```powershell
$csrf = Invoke-RestMethod "$baseUrl/api/auth/csrf" -WebSession $authSession
$headers = @{}
$headers[$csrf.headerName] = $csrf.token
$result = Invoke-WebRequest "$baseUrl/api/auth/logout" -Method Post `
    -WebSession $authSession -Headers $headers -UseBasicParsing
$result.StatusCode # 204
Invoke-RestMethod "$baseUrl/api/auth/session" -WebSession $authSession
# La última llamada debe fallar con HTTP 401: la sesión ya está cerrada.
```

Enviar login sin el header CSRF debe devolver 403. Enviar contraseña incorrecta
con cookie y CSRF válidos debe devolver 401. Los errores HTTP aparecen como
excepciones en PowerShell; ese comportamiento es esperado en estas comprobaciones.

## Pruebas

Desde `backend`:

```powershell
mvn test
mvn package
```

Las pruebas sin PostgreSQL verifican login real con filtros Spring Security,
persistencia del contexto entre solicitudes, cambio del ID de sesión, logout,
rotación de CSRF, acceso por rol, cuentas inactivas, errores genéricos y BCrypt.
El repositorio se sustituye por un mock en esas pruebas. El alta inicial tiene
pruebas de validación, hash y rechazo si existe un administrador.

Las pruebas PostgreSQL están condicionadas a `TEST_DB_URL`, `TEST_DB_USER` y
`TEST_DB_PASSWORD`, como explica `desarrollo-local.md`. Comprueban ambas migraciones,
la fila de bloqueo y la unicidad de usuario sin distinguir mayúsculas. Una prueba
adicional levanta el contexto completo, intenta crear el primer administrador
desde dos transacciones concurrentes y autentica contra el repositorio real.
Cada prueba crea y elimina su propio esquema aleatorio. Usar una
base de pruebas separada; nunca cambiar la conexión operativa para ejecutar tests.

Las sesiones actuales viven en memoria del servidor: reiniciarlo cierra los accesos,
pero conserva administradores y pedidos. La desactivación de un usuario impide
nuevos logins; todavía no hay pantalla de administración ni revocación de sesiones
existentes. Recuperación de contraseña, límites de intentos y administración de
usuarios quedan fuera de esta entrega y deberán definirse antes de publicar.

Referencia utilizada para CSRF y renovación del token:
[Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
