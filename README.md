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
cp .env.example .env   # rellena credenciales si las necesitas
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Health check: `curl http://localhost:8080/actuator/health`

## Tests

```bash
./mvnw -B verify
```

## Variables de entorno

Copia [`.env.example`](.env.example) a `.env` y rellena lo que necesites (por ejemplo las
credenciales de Google). `.env` está ignorado por git y **no hace falta exportar nada**:
Docker Compose lo lee automáticamente y el perfil `dev` de la app también
(`spring.config.import`). Los valores por defecto funcionan con el PostgreSQL de Docker Compose.

Para OAuth2 Google: descomenta `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET` en `.env` y registra
`http://localhost:8080/login/oauth2/code/google` como URI de redirección en Google Cloud.

## Ramas y commits

`main` solo recibe PRs desde `develop`; el trabajo se hace en ramas `feature/*`, `fix/*` o
`chore/*` creadas desde `develop`. Conventional Commits y CI en verde obligatorio antes del merge.
