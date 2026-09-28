# Todo App

Backend de gestión de tareas estilo Trello: tableros personales y colaborativos con columnas,
tareas, subtareas, comentarios, roles, invitaciones por correo, log de actividad y tiempo real
vía WebSockets.

- Plan de desarrollo: [`docs/development-plan.md`](docs/development-plan.md)
- Guía para agentes: [`AGENTS.md`](AGENTS.md)

## Stack

Java 25 · Spring Boot 4.1.1 · PostgreSQL 17 · Flyway · Spring Security + JWT (jjwt) ·
OAuth2 Google · WebSocket + STOMP + SockJS · Resend · springdoc-openapi · Testcontainers

## Requisitos

- JDK 25 (Temurin)
- Docker (PostgreSQL local, Testcontainers y despliegue)
- Maven Wrapper incluido (`./mvnw`)

## Puesta en marcha (dev)

```bash
sdk use java 25.0.4-tem
cp .env.example .env   # rellena credenciales si las necesitas
docker compose up -d   # levanta solo PostgreSQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- Health: `curl http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`
- Seed (solo perfil `dev` y BD vacía): `demo@example.com` / `password123` con tableros,
  columnas, tareas, subtareas y un comentario de ejemplo.

## Endpoints principales

| Método | Ruta | Notas |
|---|---|---|
| POST | `/api/v1/auth/register` · `/login` · `/refresh` | Público; devuelve access + refresh |
| GET | `/oauth2/authorization/google` | OAuth2 Google → redirect con JWT |
| GET | `/api/v1/users?email=` | Búsqueda exacta de usuarios |
| POST/GET | `/api/v1/boards` | Crear (con `type`) / mis tableros |
| GET/PATCH/DELETE | `/api/v1/boards/{id}` | Detalle (columnas+tareas+miembros) / editar / borrar |
| GET/DELETE | `/api/v1/boards/{id}/members[/{memberId}]` | Listar / remover miembros |
| GET | `/api/v1/boards/{id}/activities` | Log de actividad, paginado, **solo Owner** |
| POST/GET | `/api/v1/boards/{id}/columns` | Columnas |
| PATCH/DELETE | `/api/v1/boards/{id}/columns/{columnId}` | Renombrar/reordenar / borrar |
| POST/GET | `/api/v1/columns/{columnId}/tasks` | Tareas de la columna |
| PATCH/DELETE | `/api/v1/tasks/{taskId}` | Editar/mover / borrar |
| POST | `/api/v1/tasks/{taskId}/assignee` | Asignar (debe ser miembro) |
| POST/GET | `/api/v1/tasks/{taskId}/subtasks` | Subtareas |
| PATCH/DELETE | `/api/v1/tasks/{taskId}/subtasks/{subtaskId}` | Editar/reordenar / borrar |
| POST/GET | `/api/v1/tasks/{taskId}/comments` | Comentarios |
| DELETE | `/api/v1/tasks/{taskId}/comments/{commentId}` | Borrar (propios o Owner/Admin) |
| POST/GET | `/api/v1/boards/{id}/invitations` | Invitar / invitaciones enviadas |
| GET | `/api/v1/invitations` | Invitaciones recibidas (por email) |
| POST | `/api/v1/invitations/{token}/accept` · `/reject` | Aceptar / rechazar |

Los errores se devuelven como `ProblemDetail` (`application/problem+json`).

## Roles y permisos

Owner gestiona todo; Admin gestiona columnas/tareas y puede invitar/remover solo `MEMBER`;
Member edita/mueve/subtareas **de su tarea asignada** y comenta en cualquiera. La matriz completa
está en [`docs/development-plan.md`](docs/development-plan.md) §4 y se aplica en
`BoardPermissionService`.

## Tiempo real (STOMP)

- Endpoint SockJS: `/ws` (STOMP). En el frame **CONNECT** envía
  `Authorization: Bearer <access-token>`; sin token la conexión se rechaza.
- Suscripción: `/topic/boards/{boardId}` — solo miembros del tablero.
- Cada mutación emite un `RealtimeEvent` (`type`, `boardId`, `entityType`, `entityId`,
  `actorId`, `payload`, `occurredAt`): `TASK_MOVED`, `SUBTASK_CREATED`, `MEMBER_JOINED`, etc.

## Tests

```bash
./mvnw -B verify
```

Incluye tests unitarios (matriz de permisos, Resend) y de integración con Testcontainers
(PostgreSQL real): auth, tableros, tareas, invitaciones, actividades, tiempo real y un flujo E2E
completo de colaboración.

## Variables de entorno

Copia [`.env.example`](.env.example) a `.env` y rellena lo que necesites. `.env` está ignorado
por git y **no hace falta exportar nada**: Docker Compose lo lee automáticamente y el perfil
`dev` de la app también (`spring.config.import`).

| Variable | Descripción |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Datasource del perfil dev (por defecto `todo/todo`) |
| `JWT_SECRET` | Secreto HS256 (mín. 32 bytes). **Obligatorio en prod** |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | OAuth2 Google (opcionales en dev) |
| `OAUTH2_REDIRECT_URI` | Destino tras el login con Google |
| `RESEND_API_KEY`, `MAIL_FROM` | Envío de correos; sin API key se registran en logs |
| `INVITATION_ACCEPT_URL`, `INVITATION_REJECT_URL` | Links usados en el correo de invitación |

Notas de correo: Resend exige un **dominio verificado** para enviar a terceros. De momento no hay
dominio registrado, por lo que los correos no se envían y quedan en logs; el token de la
invitación es accesible vía `GET /api/v1/boards/{id}/invitations`. Al verificar un dominio y
definir `RESEND_API_KEY`/`MAIL_FROM`, el envío funciona sin cambios de código.

## Docker (app empaquetada)

```bash
docker compose --profile full up -d --build
```

Levanta PostgreSQL + la app (perfil `prod`) en `http://localhost:8080`. Sin el perfil `full`,
`docker compose up -d` solo arranca PostgreSQL para el desarrollo local.

## Ramas y commits

`main` solo recibe PRs desde `develop`; el trabajo se hace en ramas `feature/*`, `fix/*` o
`chore/*` creadas desde `develop`. Conventional Commits y CI en verde obligatorio antes del merge.
Los releases se publican con PR `develop` → `main` + tag semántico `vX.Y.Z`.
