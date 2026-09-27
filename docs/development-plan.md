# Plan de Desarrollo — Todo App (clon de Trello)

> Aplicación backend de gestión de tareas estilo Trello: tableros personales y colaborativos con columnas, tareas, subtareas, comentarios, roles, invitaciones por correo y actualizaciones en tiempo real vía WebSockets.

---

## 1. Stack tecnológico

| Capa              | Tecnología                                            |
|-------------------|-------------------------------------------------------|
| Lenguaje / Runtime| Java 25                                               |
| Framework         | Spring Boot 4.1.1                                     |
| API REST          | Spring Web (MVC) + Bean Validation                    |
| Seguridad         | Spring Security + JWT (jjwt 0.13.x)                   |
| OAuth             | Spring Boot OAuth2 Client (Google)                    |
| Persistencia      | Spring Data JPA + Hibernate                           |
| Base de datos     | PostgreSQL 17 + Flyway (migraciones)                  |
| Tiempo real       | Spring WebSocket + STOMP + SockJS (fallback)          |
| Correos           | Resend (API HTTP vía `RestClient`)                    |
| Docs API          | springdoc-openapi (versión compatible con Boot 4)    |
| Tests             | JUnit 5 + Mockito + Testcontainers (PostgreSQL)       |
| Dev infra         | Docker Compose (PostgreSQL local)                     |

## 2. Arquitectura — Monolito modular

```
com.todo
├── shared/        → config, errores (ProblemDetail), utilidades
├── auth/          → registro, login, JWT, OAuth Google, refresh tokens
├── users/         → perfil, búsqueda por correo
├── boards/        → tableros, membresías, permisos, log de actividad
├── columns/       → columnas del tablero
├── tasks/         → tareas, subtareas, comentarios, asignación
├── invitations/   → invitaciones por correo + aceptar/rechazar
├── mail/          → interfaz MailSender + implementación Resend
└── realtime/      → config WebSocket, interceptores, publicador de eventos
```

Cada módulo se organiza en `controller/`, `service/`, `repository/`, `domain/`, `dto/`.

## 3. Modelo de datos (ER)

- **users** — id, name, email (unique), password (nullable si es Google), provider (LOCAL / GOOGLE), created_at
- **boards** — id, title, description, type (PERSONAL / COLLABORATIVE), owner_id, version (locking optimista)
- **board_members** — id, board_id, user_id, role (OWNER / ADMIN / MEMBER), unique(board_id, user_id)
- **board_columns** — id, board_id, name, position
- **tasks** — id, column_id, title, description, position, assignee_id (nullable, FK users), created_by, created_at, updated_at
- **subtasks** — id, task_id, title, done, position
- **comments** — id, task_id, author_id, content, created_at
- **board_activities** — id, board_id, actor_id, action (enum), entity_type, entity_id, details (JSONB), created_at
- **invitations** — id, board_id, inviter_id, invitee_email, role, token (unique), status (PENDING / ACCEPTED / REJECTED / EXPIRED), expires_at
- **refresh_tokens** — id, user_id, token_hash, expires_at, revoked

## 4. Matriz de permisos (definitiva)

| Acción                                  | Owner | Admin | Member |
|-----------------------------------------|:-----:|:-----:|:------:|
| Ver tablero                             | ✅    | ✅    | ✅     |
| Editar / eliminar tablero               | ✅    | ❌    | ❌     |
| Ver log de actividad                    | ✅    | ❌    | ❌     |
| Invitar usuarios                        | ✅    | ✅ (solo rol member) | ❌ |
| Remover usuarios                        | ✅ (todos) | ✅ (solo members) | ❌ |
| Cambiar roles                           | ✅    | ❌    | ❌     |
| CRUD columnas                           | ✅    | ✅    | ❌     |
| Crear / eliminar tareas                 | ✅    | ✅    | ❌     |
| Asignar / reasignar tareas              | ✅    | ✅    | ❌     |
| Editar tarea asignada (título, desc)    | ✅    | ✅    | ✅ (solo la suya) |
| Mover tarea de columna                  | ✅    | ✅    | ✅ (solo la suya) |
| CRUD subtareas                          | ✅    | ✅    | ✅ (solo en su tarea asignada) |
| Comentar cualquier tarea                | ✅    | ✅    | ✅     |
| Eliminar comentarios                    | ✅    | ✅    | propios |

- Los permisos se centralizan en un `BoardPermissionService` (policy layer) invocado por todos los servicios antes de mutar.
- Tableros **personales**: solo el creador, sin invitaciones ni roles.

## 5. Gestión del repositorio y flujo de trabajo

### 5.1 Uso de skills (`find-skills`)
- Durante todo el desarrollo: si no se tiene conocimiento suficiente sobre cómo implementar algo (tecnología, patrón o integración no dominada), se debe usar la skill **`find-skills`** para buscar una skill que enseñe a hacerlo, en lugar de improvisar.
- Ejemplos de aplicación: configuración de GitHub Actions, branch protection vía `gh api`, Testcontainers, STOMP WebSockets, Resend, OAuth2 con Google.

### 5.2 Estrategia de ramas
- `main` — únicamente código 100% estable y verificado; solo recibe merges vía PR desde `develop`.
- `develop` — rama de integración; destino de todos los PRs de features/fixes.
- Ramas de trabajo — `feature/...`, `fix/...`, `chore/...` nacen de `develop`.
- Releases: PR de `develop` → `main` + tag semántico `vX.Y.Z`.

### 5.3 Setup inicial del repositorio (GitHub)
1. `git init` + `.gitignore` profesional + commit inicial con la estructura del proyecto.
2. Crear el repo en GitHub (`gh repo create`) y hacer push de `main` + `develop`.
3. Configurar reglas de protección en `main` y `develop` (vía `gh api` rulesets):
   - Prohibido el push directo — todo cambio entra por PR.
   - Checks de CI obligatorios en verde antes de permitir el merge.
   - Prohibido force push y borrado de ramas.
   - Sin aprobaciones requeridas (trabajo individual).

### 5.4 CI — `.github/workflows/ci.yaml`
- Triggers: `push` a `main`/`develop` y `pull_request` hacia `main`/`develop`.
- Jobs: checkout → `setup-java` (Java 25, Temurin) → caché de Maven → `./mvnw -B verify` (compila + tests unitarios + integración con Testcontainers usando el Docker del runner de Ubuntu).
- Se crea en la Fase 0 para que todo PR tenga checks desde el primer día.

### 5.5 Flujo por fase (revisión antes de commit)
- Al terminar cada fase, **no** se hace commit ni PR automáticamente.
- Se avisa al usuario que la fase terminó, junto con cómo verificarla (comandos de tests, cómo probar los endpoints, etc.).
- El usuario revisa; **solo con su visto bueno** se hace commit (Conventional Commits), push y PR a `develop` (CI verde → merge).
- Si algo no está bien, se corrige **antes** de crear cualquier commit — sin commits innecesarios ni basura en el historial.

### 5.6 Mejores prácticas de repo profesional
- Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`).
- Plantilla de PR en `.github/pull_request_template.md`.
- `CODEOWNERS` documentando la propiedad del código.
- Secretos jamás en el repo: `.env.example` documentado, CI usa GitHub Secrets, credenciales reales solo en local.
- README con setup y versionado semántico con tags.

## 6. Fases de desarrollo

### Fase 0 — Setup (base)
- Dependencias en `pom.xml`, Docker Compose con PostgreSQL, `application.yaml` (perfiles dev/prod), Flyway V1, health check, estructura de paquetes, convención de errores ProblemDetail + `GlobalExceptionHandler`.
- Setup del repositorio: `git init` + `.gitignore`, creación del repo en GitHub, push de `main` + `develop`, configuración de reglas de protección y creación de `ci.yaml` (ver sección 5: Gestión del repositorio y flujo de trabajo).

### Fase 1 — Auth y usuarios
- Registro (nombre, correo, contraseña con BCrypt), login → JWT access (15 min) + refresh (7 días, hash en BD).
- OAuth Google: `/oauth2/authorization/google` → callback → crear/vincular usuario por email → emitir JWT propio (handler custom).
- `SecurityConfig`, filtro JWT, endpoints `/api/v1/auth/*`.
- Tests de integración de auth (Testcontainers).

### Fase 2 — Tableros y membresías
- CRUD `/api/v1/boards`; al crear un board se genera automáticamente `board_member` OWNER.
- `BoardPermissionService` con la matriz completa + tests unitarios de la matriz (crítico).
- Tableros personales bloquean invitaciones.

### Fase 3 — Columnas, tareas, subtareas, comentarios
- CRUD anidado `/api/v1/boards/{boardId}/columns`, `/columns/{columnId}/tasks`, `/tasks/{taskId}/subtasks`, `/tasks/{taskId}/comments`.
- Ordenamiento por `position` (reordenado al mover/insertar), validación de asignado (debe ser miembro del tablero, solo un asignado por tarea).

### Fase 4 — Invitaciones por correo
- `GET /api/v1/users?email=` (búsqueda), `POST /api/v1/boards/{id}/invitations` genera token único con expiración (48 h).
- `MailSender` (interfaz) + `ResendMailSender` (HTML con link `accept`/`reject` y endpoints `POST /api/v1/invitations/{token}/accept|reject`).
- Al aceptar: crea `board_member` con el rol de la invitación.

### Fase 5 — Log de actividad
- `BoardActivityService`: cada mutación registra evento (Spring Application Events desacoplados: `task.moved`, `member.joined`, `invitation.accepted`, etc. con detalles en JSONB).
- `GET /api/v1/boards/{id}/activities` paginado — **solo Owner**.

### Fase 6 — Tiempo real (WebSockets)
- STOMP sobre WebSocket; autenticación JWT en frame CONNECT (interceptor).
- Topics: `/topic/boards/{boardId}` — interceptor autoriza suscripción solo a miembros.
- Publicador central (`RealtimePublisher`) emite DTOs de eventos al mutar: `TASK_MOVED`, `SUBTASK_CREATED`, `MEMBER_JOINED`, etc.
- Preparado para SockJS como fallback.

### Fase 7 — Hardening, docs y entregables finales
- springdoc-openapi + Swagger UI, seed data (`DataSeeder`), README (setup, endpoints, roles, variables de entorno), tests E2E de flujos críticos (invitación completa, permisos, WebSocket), `docker compose` con la app empaquetada.

## 7. Endpoints principales (resumen)

```
POST   /api/v1/auth/register | /login | /refresh
GET    /oauth2/authorization/google  → callback → JWT

GET    /api/v1/users?email=...

POST   /api/v1/boards                      (crear, con type)
GET    /api/v1/boards                      (mis tableros)
GET    /api/v1/boards/{id}                 (tablero completo: columnas+tareas+miembros)
PATCH  /api/v1/boards/{id}                 DELETE ...
GET    /api/v1/boards/{id}/members         DELETE .../members/{memberId}

POST   /api/v1/boards/{id}/columns          PATCH/DELETE .../columns/{colId}
POST   .../columns/{colId}/tasks            PATCH/DELETE .../tasks/{taskId}
POST   .../tasks/{taskId}/assignee         PATCH .../tasks/{taskId}   (move/edit)
POST   .../tasks/{taskId}/subtasks          PATCH/DELETE .../subtasks/{id}
POST   .../tasks/{taskId}/comments          DELETE .../comments/{id}

POST   /api/v1/boards/{id}/invitations      GET .../invitations (salidas)
POST   /api/v1/invitations/{token}/accept   POST /api/v1/invitations/{token}/reject
GET    /api/v1/invitations (entradas, por email)

GET    /api/v1/boards/{id}/activities       (solo Owner, paginado)

WS     /ws  (STOMP, JWT en CONNECT)  → subscribe /topic/boards/{id}
```

## 8. Supuestos confirmados / decisiones tomadas

- JWT propio (jjwt) también para usuarios de Google (OAuth solo para identidad).
- Refresh tokens hasheados en BD → revocables.
- Invitación expira a las 48 h; rechazarla es permanente (se puede reinvitar).
- Member solo edita/mueve/agrega subtareas **en su tarea asignada**; comenta en cualquier tarea.
- Eliminación de comentarios: propios (author), Owner/Admin cualquiera.
- Sin aprobaciones requeridas en PRs — solo CI en verde (trabajo individual).
- Cada fase se notifica al terminar; commit/push/PR solo con el visto bueno del usuario (evita commits innecesarios).
- Orden de implementación sugerido: Fase 0 → 1 → 2 → … (cada fase depende de la anterior).
