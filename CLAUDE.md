# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Documentación: vault de Obsidian

Toda la documentación y el conocimiento del proyecto (back y front) vive en el vault **`C:\PokeFantasy\vault`** (no está en git). Este archivo solo contiene las reglas que hay que cumplir al programar.

- Punto de entrada: `vault/Home.md`. Contrato de la API: `20 Arquitectura/API REST.md`. Modelo de datos: `20 Arquitectura/Modelo de datos.md`. Una nota por feature en `50 Features/`, decisiones en `70 Decisiones/`, incidentes en `60 Operaciones/Gotchas.md`.
- Antes de tocar una feature, leer su nota. Al terminar (PR mergeado o listo), actualizar las notas afectadas: feature (`estado`, PRs), API REST, modelo de datos, gotchas.
- **"Añadir al roadmap"** = crear o actualizar la nota de la feature en `vault/50 Features/` desde `Plantillas/Plantilla Feature.md` con `estado: idea`. No implica implementación. `Features.base` es la vista del roadmap.
- Specs y planes de los skills siguen en `docs/superpowers/` de cada repo (el vault los enlaza).
- Si una nota contradice al código, manda el código: corregir la nota.

## Repos & Deploy

| Repo | Local path | Base branch | Deploy |
|------|-----------|-------------|--------|
| Backend | `C:\PokeFantasy\pokefantasy` | `develop` | Render (auto on push to `develop`) |
| Frontend | `C:\PokeFantasy\pokefantasy-web` | `main` | Netlify (auto on push to `main`) |

- GitHub org: https://github.com/Pokemons-Fantasy
- Production: https://pokefantasy.onrender.com

## Git workflow (mandatory)

1. **Revisar PRs abiertos** en ambos repos antes de crear ramas o tocar código:
   ```powershell
   $h = @{ Authorization = "Bearer $env:GITHUB_TOKEN"; Accept = "application/vnd.github+json" }
   Invoke-RestMethod "https://api.github.com/repos/Pokemons-Fantasy/pokefantasy-web/pulls?state=open" -Headers $h | Select number,title,@{n='branch';e={$_.head.ref}}
   Invoke-RestMethod "https://api.github.com/repos/Pokemons-Fantasy/pokefantasy/pulls?state=open"    -Headers $h | Select number,title,@{n='branch';e={$_.head.ref}}
   ```
   Si hay PRs abiertos, mencionarlos al usuario antes de continuar.
2. **Siempre invocar el skill `brainstorming`** antes de implementar cualquier feature nueva, aunque parezca simple.
3. Never push directly to `develop` (backend) or `main` (frontend): `feature/...` or `fix/...` → commit → push → PR. Backend PRs target `develop`; frontend PRs target `main`.
4. Si un cambio toca back y front, el front debe tolerar el back viejo (campos opcionales) y el back no romper el front viejo: así da igual el orden de los merges.

There is no `gh` CLI — create PRs via GitHub API:

```powershell
$token = $env:GITHUB_TOKEN   # set in your shell, never hardcode
$headers = @{ Authorization = "Bearer $token"; Accept = "application/vnd.github+json" }
$pr = @{ title = "..."; head = "feature/..."; base = "develop"; body = "..." } | ConvertTo-Json
Invoke-RestMethod -Uri "https://api.github.com/repos/Pokemons-Fantasy/pokefantasy/pulls" -Method Post -Headers $headers -Body $pr -ContentType "application/json; charset=utf-8"
```

Shell: PowerShell on Windows. Git Bash also available via Bash tool (use paths like `/c/PokeFantasy/...`).

## Build commands

Maven wrapper `./mvnw` from the **repo root** with `-f src/pom.xml` (el `pom.xml` padre está en `src/`). Maven is not on PATH. La CI (`.github/workflows/workflow.yml`, en cada PR y push a `develop`) ejecuta el primer comando y además construye la imagen Docker:

```bash
# Build + tests (unit, controllers, integration with Testcontainers if Docker is available) + coverage gate (80% JaCoCo)
./mvnw -B -ntp -f src/pom.xml clean verify

# Build without tests
./mvnw -B -ntp -f src/pom.xml clean package -DskipTests

# Application module tests only (fast, no infra/Redis/MongoDB needed)
./mvnw -B -ntp -f src/pom.xml test -pl application -am

# Single test class
./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=MyTestClass
```

- Antes de hacer push a un PR, `verify` (no solo `test`: `test` no ejecuta el gate de JaCoCo). Sin Docker los tests de integración se saltan: antes de un PR grande, ejecutarlos con Docker abierto.
- Infra local: `cd src && docker-compose up -d` (Mongo en replica set :27017, Redis :6379). Env var obligatoria `JWT_SECRET` (Base64, 256 bits). Resto de variables y guía de entorno local: `vault/60 Operaciones/Entorno local.md`.
- **Versiones**: Spring Boot se versiona **solo** con el parent `spring-boot-starter-parent` de `src/pom.xml`; no fijes versiones de artefactos `org.springframework.boot` en los POM.

## Reglas de arquitectura

Hexagonal, 5 módulos bajo `src/`: `domain` (entidades, repos, DTOs, puertos; sin Spring), `application` (commands, facades, lógica), `infrastructure` (adaptadores), `api-rest` (controllers), `boot`. Dependencias: `api-rest` → `application` → `domain` ← `infrastructure`.

**CQRS / Mediator, siempre en este orden** (carpeta `application/src/main/java/com/villu/pokefantasy/commands/{feature}/`):

1. **`XCommand`** — `record` que **debe implementar `Command`**. Si no, `SpringMediator` no lo registra (bug real).
2. **`XCommandHandler`** — `@Service` implementando `CommandHandler<XCommand, R>`.
3. **`XFacade`** — `mediator.send(new XCommand(...))`, un `send` por método.
4. **Controller** — inyecta solo la Facade. Nunca llames a un handler directamente.

**Transacciones y concurrencia**
- Cada comando corre en una **transacción MongoDB** (`SpringMediator` → `TransactionPort`) y se **reintenta hasta 5 veces** ante conflicto. Por tanto: relee de BD todo lo que valides; nada de efectos externos (HTTP, emails) dentro del handler; **no escribas compensaciones manuales**.
- Push FCM: `PushNotificationPort.send`, el envío real se difiere tras el commit.
- Para rechazar una operación **persistiendo** una limpieza previa, lanza `StaleOperationException` (confirma y devuelve 409).
- Las entidades principales tienen `@Version`: nunca pises un documento con datos viejos.

**Reglas de dominio**
- **Equipos**: `DraftEntity.picks` del último draft de la liga es la **única fuente de verdad**. Todo movimiento de equipo (robo, trade, swap, compra, liberación) va por **`TeamTransferService`** + `TeamOperation`; no repitas sus reglas en los handlers.
- **Resultados**: siempre por **`MatchResultService`** (monedas, marcador y eventos van juntos).
- **Tiempo real**: emite con **`RealtimeNotifier`**, nunca con los registros SSE directamente.
- **Ventanas de robo/swap**: hora de España; `JornadaWindowService` es la única fuente de verdad (`LEAGUE_ZONE = Europe/Madrid`). El front no recalcula fechas.
- **MongoDB**: entidad nueva con índices → añadirla a `INDEXED_ENTITIES` de `MongoIndexInitializer`. Cambios de esquema en documentos existentes → migración al arrancar (ejemplos: `VersionFieldMigration`, `UserNameLowerMigration`). Campos nuevos: opcionales (pueden venir `null`).
- **Admin de liga**: `LeagueAdminGuard.requireLeagueAdmin()`.

**Excepción → HTTP** (`ApiExceptionHandler`, respuestas `ProblemDetail` con `code` estable):

| Exception | HTTP | `code` |
|-----------|------|--------|
| `IllegalArgumentException` | 400 | `BAD_REQUEST` |
| `BadCredentialsException` | 401 | `INVALID_CREDENTIALS` |
| `ForbiddenOperationException` | 403 | `FORBIDDEN` |
| `IllegalStateException` / `StaleOperationException` | 409 | `CONFLICT` |
| `OptimisticLockingFailureException` | 409 | `CONCURRENT_MODIFICATION` |
| `DuplicateKeyException` | 409 | `DUPLICATE` |
| `TooManyAttemptsException` | 429 | `TOO_MANY_ATTEMPTS` |
| `Exception` | 500 | `INTERNAL_ERROR` |

**Seguridad**: sesión por cookies httpOnly (JWT 15 min + refresh en Redis). Públicos solo los de `SecurityConfig.PUBLIC_PATHS` + OpenAPI. CORS en `SecurityConfig.corsConfigurationSource()`: si se añade un dominio propio, actualizarlo. Detalle en `vault/20 Arquitectura/Seguridad y auth.md`.

## Tests

Gate JaCoCo 80 % (instrucciones y ramas) en `application`, `infrastructure` y `api-rest`.

- **Unitarios**: Mockito puro, sin contexto de Spring; se mockean puertos y repositorios (inyección por constructor).
- **Controladores**: MockMvc standalone con `ControllerTestSupport`.
- **Integración** (`boot/src/test/.../it/`): Testcontainers con Mongo **`withReplicaSet()`** (sin él no hay transacciones y los tests mienten) y Redis.
- Ternarios y null-checks defensivos cuentan como ramas: simplificarlos cuando sea seguro sale más barato que testear cada combinación.

## Rendimiento (Render free tier)

- Minimizar llamadas al backend: caché de React Query con `staleTime`.
- Sprites siempre desde CDN: `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/{id}.png`.
- No añadir endpoints si los datos ya vienen en uno existente.

## Frontend (reglas)

- `import type { X }` para interfaces y tipos puros: **Netlify falla si no**.
- Check de tipos: `./node_modules/.bin/tsc --noEmit` desde `pokefantasy-web/`. Antes de push, como la CI: `npm run lint`, `npm test`, `npm run api:check`, `npm run build`.
- **Tras cambiar un DTO, enum o endpoint del backend**: `npm run api:spec` (o `npm run api:spec -- <url>`) → `npm run api:types`; commitear `openapi.json` y `src/api/schema.d.ts`. Si `tsc` falla en `src/api/contract.ts`, un tipo escrito a mano ya no cuadra con el backend: corregirlo. Tipo de respuesta nuevo → añadir su entrada en `contract.ts`.
- Sin `VITE_API_URL` el front local apunta a **producción**: usar `pokefantasy-web/.env.local` con `VITE_API_URL=http://localhost:8080`.
