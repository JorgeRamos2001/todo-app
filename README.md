# Todo App

Backend de gestión de tareas estilo Trello: tableros personales y colaborativos con columnas,
tareas, subtareas, comentarios, roles, invitaciones por correo y tiempo real vía WebSockets.

- Plan de desarrollo: [`docs/development-plan.md`](docs/development-plan.md)
- Guía para agentes: [`AGENTS.md`](AGENTS.md)

## Stack

Java 25 · Spring Boot 4.1.1 · PostgreSQL 17 · Flyway · Spring Security + JWT ·
WebSocket/STOMP · Testcontainers

## Requisitos

- JDK 25 (Temurin)
- Docker (PostgreSQL local y Testcontainers)

## Puesta en marcha

```bash
sdk use java 25.0.4-tem
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Health check: `curl http://localhost:8080/actuator/health`

## Tests

```bash
./mvnw -B verify
```

## Variables de entorno

Ver [`.env.example`](.env.example). Para desarrollo local puedes copiarlo a `.env`
(ignorado por git); los valores por defecto funcionan con el PostgreSQL de Docker Compose.

## Ramas y commits

`main` solo recibe PRs desde `develop`; el trabajo se hace en ramas `feature/*`, `fix/*` o
`chore/*` creadas desde `develop`. Conventional Commits y CI en verde obligatorio antes del merge.
