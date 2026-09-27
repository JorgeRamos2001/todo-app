# AGENTS.md

## Descripción

Backend de gestión de tareas estilo Trello: tableros personales y colaborativos con columnas,
tareas, subtareas, comentarios, roles, invitaciones por correo y tiempo real vía WebSockets.

Fuente de verdad: `docs/development-plan.md`. Ante dudas de alcance, diseño o prioridad, consultar ahí.

## Stack

- Java 25 (Temurin) + Spring Boot 4.1.1 (Web MVC, Bean Validation)
- Spring Security + JWT (jjwt 0.13.x) + OAuth2 Client (Google)
- Spring Data JPA + Hibernate + PostgreSQL 17 + Flyway
- WebSocket + STOMP + SockJS; correos con Resend (API HTTP vía `RestClient`)
- springdoc-openapi; tests: JUnit 5 + Mockito + Testcontainers (PostgreSQL)
- Docker Compose para infra local

## Comandos

| Acción                    | Comando                                              |
|---------------------------|------------------------------------------------------|
| Activar JDK 25            | `sdk use java 25.0.4-tem`                            |
| Levantar PostgreSQL local | `docker compose up -d`                               |
| Compilar + tests          | `./mvnw -B verify`                                   |
| Tests unitarios           | `./mvnw -B test`                                     |
| Ejecutar en dev           | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |

- Requiere Java 25 (con el 21 por defecto falla la compilación).
- Testcontainers requiere Docker en ejecución.

## Arquitectura

Monolito modular: cada módulo en `com.todo.<módulo>` con `controller/`, `service/`,
`repository/`, `domain/`, `dto/`. Módulos: `shared`, `auth`, `users`, `boards`, `columns`,
`tasks`, `invitations`, `mail`, `realtime`.

## Convenciones

- Errores: `ProblemDetail` + `GlobalExceptionHandler` centralizado.
- Flyway: `V{n}__descripcion.sql`; nunca editar migraciones ya aplicadas.
- Permisos: toda mutación pasa por `BoardPermissionService` (matriz en el plan, §4).
- Commits: Conventional Commits. Ramas `feature/*`, `fix/*`, `chore/*` desde `develop`;
  `main` solo recibe PRs desde `develop`.
- Secretos: nunca en el repo; `.env` local documentado en `.env.example`, GitHub Secrets en CI.

## Reglas de trabajo (críticas)

1. **No hacer commit, push ni PR sin visto bueno explícito del usuario.** Al terminar cada
   fase, avisar y explicar cómo verificarla; esperar aprobación (plan §5.5).
2. CI debe estar en verde antes de merge; sin aprobaciones requeridas (trabajo individual).
3. Si falta conocimiento (GitHub Actions, Testcontainers, STOMP, Resend, OAuth2...), usar la
   skill `find-skills` antes de improvisar.
4. Releases: PR `develop` → `main` + tag semántico `vX.Y.Z`.
5. Orden de fases: 0 → 1 → 2 → … → 7 (plan §6); cada fase depende de la anterior.
