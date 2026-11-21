# Foro Académico UNTELS

Proyecto de Ingeniería de Software desarrollado
utilizando el framework Scrum.

## Descripción

Plataforma web para la comunidad académica UNTELS,
orientada a compartir consultas, respuestas,
recursos educativos, proyectos y tutorías.

## Tecnologías

- Frontend: React + Vite
- Backend: Java 21 + Spring Boot
- Base de datos: PostgreSQL
- Arquitectura: Microservicios
- Comunicación: API REST
- Seguridad: Spring Security + JWT

## Microservicios

### Auth Service
Registro, autenticación y gestión de usuarios.

### Forum Service
Publicaciones, comentarios en hilo, favoritos, votos, comunidades, biblioteca, acreditaciones y tutorías.

## Integraciones

- Google Books permite buscar libros y guardar una referencia al publicar un comentario.
- Forum invita a tutor y estudiante a un mismo evento de Google Calendar con Meet. La reserva se confirma en PostgreSQL. El envío al calendario se reintenta con el mismo identificador de evento.
- Auth envía los enlaces de recuperación por Resend.

## Estado

Código de las historias programadas en los cuatro sprints. La aprobación de las historias corresponde a la revisión del equipo.

## Ejecutar el proyecto

Se requieren Java 21, Node.js compatible con Vite 8 y dos bases PostgreSQL. Desde la raíz del proyecto, aplica cada script con el propietario de su base:

```sh
psql "$AUTH_DATABASE_URL" -v ON_ERROR_STOP=1 -f database/auth.sql
psql "$FORUM_DATABASE_URL" -v ON_ERROR_STOP=1 -f database/forum.sql
```

Los scripts admiten tanto bases nuevas como las tablas del MVP y se pueden volver a ejecutar. Los nombres de las cuentas de aplicación locales son `auth_app` y `forum_app`. Si la aplicación se conecta con otra cuenta, configura `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` y `SPRING_DATASOURCE_URL` (URL JDBC) por servicio y dale permisos sobre sus tablas y secuencias.

- **Auth** utiliza `JWT_SECRET` (32 bytes aleatorios o más, en Base64) y `AUTH_DB_PASSWORD`. Inicia en `auth-service` con `sh mvnw spring-boot:run`, puerto 8081.
- **Forum** utiliza `FORUM_DB_PASSWORD`, `AUTH_SERVICE_URL` y `GOOGLE_BOOKS_API_KEY`. Inicia en `forum-service` con `sh mvnw spring-boot:run`, puerto 8082. Configura la clave de Books solo en el servidor.
- **Frontend** se inicia desde `frontend` con `npm ci` y `npm run dev`. Vite reenvía las llamadas a los servicios. En Vercel, las rutas están en `frontend/vercel.json`.
- Antes de publicar, aplica las dos migraciones en las bases del entorno, despliega los servicios de Render y después la interfaz de Vercel. El inicio desde inactividad debe medirse directamente en esos despliegues.

**Moderación.** Las cuentas nuevas se registran como `USER`. La bandeja aparece para `MODERATOR` y `ADMIN`. Para la demostración usa una cuenta de prueba previamente autorizada con uno de esos roles en Auth. El perfil editable comprende nombre, carrera o especialidad y presentación. El administrador gestiona las acreditaciones desde Tutorías, los aportes desde Biblioteca y los permisos desde Administración.

**Prueba local.** Con los dos servicios en ejecución y bases de ensayo, ejecuta `python3 tests/sprint_acceptance.py`. Crea cuentas y datos nuevos en cada ejecución. Para incluir la bandeja, configura `FORO_TEST_MOD_EMAIL` y `FORO_TEST_MOD_PASSWORD` de un moderador de pruebas. Para verificar Books con respuestas controladas, inicia `python3 tests/books_fixture.py`, arranca Forum con `BOOKS_BASE_URL=http://127.0.0.1:18090` y ejecuta las pruebas con `FORO_BOOKS_FIXTURE=1`. El doble de pruebas permite reproducir éxito, cero resultados y caída del proveedor. La comprobación con el proveedor real utiliza su URL predeterminada y la clave del proyecto.


Para repetir el recorrido de interfaz, inicia el frontend y el servicio de libros de prueba. Con Python Playwright y Chromium instalados, ejecuta `python3 tests/browser_acceptance.py`. Puedes indicar el ejecutable con `CHROMIUM_PATH`. El recorrido usa sesiones de navegador independientes y comprueba que la respuesta de la segunda cuenta se conserva cuando el autor retira la consulta. Para las comprobaciones de compilación, ejecuta `sh mvnw verify` en cada servicio y `npm run lint` y `npm run build` en `frontend`.

## Correo y calendario

| Servicio | Variables de entorno | Configuración |
|---|---|---|
| Auth | `RESET_MAIL_KEY`, `RESET_MAIL_FROM`, `PUBLIC_FRONTEND_URL` | Clave de Resend, remitente verificado y URL HTTPS pública del frontend. El enlace vence en 20 minutos y permite un solo cambio de contraseña. |
| Forum | `CALENDAR_CLIENT_ID`, `CALENDAR_CLIENT_SECRET`, `CALENDAR_REFRESH_TOKEN`, `CALENDAR_ID` | Cuenta organizadora con acceso OAuth a Calendar, permiso para escribir eventos y crear Meet. El calendario predeterminado es `primary`. Los participantes reciben invitaciones en el correo de su cuenta. |

## Pruebas de las historias incorporadas

En bases de ensayo, usa un administrador ya autorizado y configura `FORO_TEST_ADMIN_EMAIL` y `FORO_TEST_ADMIN_PASSWORD` para el proceso de pruebas. El guion crea las demás cuentas. Ejecuta `python3 tests/books_fixture.py` y `python3 tests/provider_fixture.py` en procesos separados. El segundo proceso admite `FORO_TEST_CALENDAR_RETRY=1` para simular un evento creado con respuesta fallida.

Para Auth apunta `RESET_MAIL_URL` a `http://127.0.0.1:18091/mail`, define un remitente de prueba, una clave ficticia y la URL local del frontend. Para Forum usa `BOOKS_BASE_URL=http://127.0.0.1:18090`, `CALENDAR_TOKEN_URL=http://127.0.0.1:18091/token` y `CALENDAR_API_URL=http://127.0.0.1:18091` con valores OAuth ficticios. En las pruebas establece `CALENDAR_POLL_MS=300` y `CALENDAR_RETRY_SECONDS=1`. Ejecuta `python3 tests/academic_acceptance.py` con los servicios en marcha. El guion verifica permisos, privacidad, revisión de materiales, reserva simultánea, invitaciones y recuperación de cuenta.
