# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Documentación: vault de Obsidian

Toda la documentación y el conocimiento del proyecto (back y front) vive en el vault **`C:\PokeFantasy\vault`**, versionado en el repo privado [`Pokemons-Fantasy/pokefantasy-vault`](https://github.com/Pokemons-Fantasy/pokefantasy-vault) (commits directos a `main`; convenciones en `vault/CLAUDE.md`). Este archivo solo contiene las reglas que hay que cumplir al programar.

- Punto de entrada: `vault/Home.md`. Contrato de la API: `20 Arquitectura/API REST.md`. Modelo de datos: `20 Arquitectura/Modelo de datos.md`. Flujos de datos: `20 Arquitectura/Flujos de datos.md`. Una nota por feature en `50 Features/`, decisiones en `70 Decisiones/`, incidentes en `60 Operaciones/Gotchas.md`.
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

## Arquitectura

### 1. Qué hay en el sistema

```
Web (Netlify) / Android (Capacitor)
   │  REST + cookies jwt/refresh            ▲ SSE (draft, usuario) · FCM push
   ▼                                        │
api-rest ─► application ─► domain ◄─ infrastructure ─► MongoDB Atlas (replica set) · Redis · PokeAPI · Firebase
(controllers, jobs)  (commands, servicios, puertos)    (adaptadores)
```

5 módulos Maven bajo `src/`: `domain` (entidades con anotaciones de Spring Data, interfaces de repositorio, DTOs, excepciones; sin lógica), `application` (commands, facades, servicios de dominio, puertos), `infrastructure` (adaptadores), `api-rest` (controllers, jobs `@Scheduled`, filtros), `boot` (arranque, config, tests de integración). Diagrama completo: `vault/20 Arquitectura/Visión general.md`.

### 2. Quién es responsable de qué (un solo dueño por responsabilidad)

| Responsabilidad | Dueño |
|---|---|
| Transacción, reintentos y métricas de cada comando | `SpringMediator` (+ `TransactionPort`, `CommandMetricsPort`) |
| Movimientos de equipo (robo, trade, swap, compra, liberar): mercado abierto, bloqueo 7 días, cobro, banca | `TeamTransferService` + `TeamOperation` |
| Ventanas de robo / swap (hora de Madrid) | `JornadaWindowService` |
| Registrar / corregir / deshacer resultados, monedas por partido y sus eventos | `MatchResultService` |
| Precio de un tier | `TierPricingService` |
| Reparto de tiers del pool | `TierAssignmentService` |
| Calendario round-robin | `RoundRobinScheduler` |
| Turnos vencidos del draft (cliente y job) | `DraftTurnTimeoutService` |
| Push de turno / de cierre de ventana | `DraftTurnNotifier` / `WindowReminderService` |
| Admin de liga / pertenencia a liga | `LeagueAdminGuard` / `LeagueMemberService` |
| Emitir SSE (vía Redis Pub/Sub) | `RealtimeNotifier` (api-rest) |
| Enviar push | `PushNotificationPort` |
| Sesión (JWT + refresh), límite de login | `JwtAuthFilter` + `AuthCookies` + `RefreshTokenPort`, `LoginAttemptPort` |
| Excepción → HTTP (`ProblemDetail`) | `ApiExceptionHandler` |
| Índices / migraciones de esquema | `MongoIndexInitializer` / clases `*Migration` |
| Caché de Pokémon | `PokemonCacheLoader` |

Si una regla ya tiene dueño, se usa el dueño; no se reimplementa en un handler.

### 3. Decisiones intencionadas (no "arreglarlas")

Parecen raras o mejorables pero son a propósito. Antes de cambiarlas, leer la nota en `vault/70 Decisiones/` y preguntar.

- `DraftTurnTimeoutService` **inyecta `DraftPickCommandHandler` directamente**: única excepción a "nunca llamar handlers directamente", evita una dependencia circular con el mediator (ADR-005).
- **Los equipos son solo `draft.picks`** del último draft; `UserEntity` no guarda equipos (ADR-006).
- **Un comando = una transacción Mongo con hasta 5 reintentos**; sin compensaciones manuales (ADR-007).
- **SSE por Redis Pub/Sub** y emitido desde `api-rest` tras el commit, no desde los handlers (ADR-008).
- **JWT de 15 min + refresh en Redis**, renovación transparente en el filtro; fail-closed si Redis cae (ADR-009).
- **Ventanas evaluadas en `Europe/Madrid`** aunque Render corra en UTC (ADR-003).
- **Bloqueo por timestamp** (`lockedUntil`, 7 días), no por jornada (ADR-004).
- **Tipos del front generados desde OpenAPI**: cambiar un DTO obliga a regenerarlos en el front (ADR-010).
- Deshacer un resultado devuelve **las monedas que se dieron**, aunque el saldo quede negativo.

### 4. Qué puede tocar qué

Dirección: `api-rest` → `application` → `domain` ← `infrastructure`; `boot` depende de todos.

**CQRS / Mediator, siempre en este orden** (carpeta `application/src/main/java/com/villu/pokefantasy/commands/{feature}/`):

1. **`XCommand`** — `record` que **debe implementar `Command`**. Si no, `SpringMediator` no lo registra (bug real).
2. **`XCommandHandler`** — `@Service` implementando `CommandHandler<XCommand, R>`.
3. **`XFacade`** — `mediator.send(new XCommand(...))`, un `send` por método.
4. **Controller** — inyecta solo la Facade.

Prohibido:
- Llamar a un handler desde otro sitio que no sea el mediator (salvo la excepción de ADR-005).
- Que un controller use repositorios, servicios de dominio o handlers.
- Que `domain` o `application` dependan de `infrastructure` o de adaptadores concretos (usar puertos); lógica de negocio en `domain` o en controllers.
- Usar `SseEmitterRegistry` / `UserSseEmitterRegistry` directamente (usa `RealtimeNotifier`).
- Efectos externos (HTTP, emails) dentro de un handler; compensaciones manuales.
- Fijar versiones de artefactos `org.springframework.boot`.

### 5. Cómo se mueven los datos

Escritura: `Controller` → `XFacade` → `SpringMediator` (abre transacción) → `XCommandHandler` → servicios de dominio → repositorios → **commit** → el controller emite SSE con `RealtimeNotifier` (Redis Pub/Sub → cada instancia → `EventSource` del cliente, que invalida su query de React Query y refresca). Los push se piden en el handler con `PushNotificationPort` y se envían tras el commit.
Lectura: igual, con un comando de consulta. Los jobs (`DraftTurnTimeoutJob`, `WindowReminderJob`) entran por la Facade como un controller.
Flujos completos (robo, pick del draft, resultado): `vault/20 Arquitectura/Flujos de datos.md`.

### 6. Qué no se puede romper nunca

- **Secretos fuera del repo** (`JWT_SECRET`, `FIREBASE_SERVICE_ACCOUNT_JSON`, `google-services.json`, `.env.local`); errores 500 sin detalles internos.
- **Una sola fuente de verdad**: equipos en `draft.picks`, ventanas en `JornadaWindowService`, resultados y sus monedas en `MatchResultService`.
- **Todo cambio de estado es un comando**: pasa por el mediator, en transacción, y es seguro ante reintentos (relee lo que valida).
- **Nunca pisar datos ajenos**: `@Version` en las entidades principales; nada de `save()` con datos leídos antes de la transacción.
- **Compatibilidad**: campos nuevos opcionales; el front viejo sigue funcionando con el back nuevo y viceversa.
- **Contrato de errores**: `ProblemDetail` con `code` estable (el front depende de `code` y `message`).
- **Gate de cobertura** 80 % y CI en verde antes de mergear.
- Ningún patrón nuevo (librería, capa, estilo) sin una razón escrita en `vault/70 Decisiones/`.

### 7. Dónde va el código nuevo

| Qué | Dónde |
|---|---|
| Caso de uso nuevo | `application/.../commands/{feature}/` (Command + Handler + Facade) y controller en `api-rest` |
| Regla compartida por varios casos de uso | En su dueño (tabla 2); si no existe, un servicio en `application` con nombre de la responsabilidad |
| Integración externa | Puerto en `application/.../ports/` + adaptador en `infrastructure` (excepción histórica: `PushNotificationPort` y los repositorios viven en `domain/.../repository/`) |
| Entidad nueva con índices | `domain/.../repository/entity/` + añadirla a `INDEXED_ENTITIES` de `MongoIndexInitializer` |
| Cambio de esquema en documentos existentes | Migración al arrancar en `infrastructure/.../migration/` (ver `VersionFieldMigration`) |
| Tarea periódica | Job `@Scheduled` en `api-rest` que llama a una Facade (pool de 3 hilos compartido) |
| Evento del activity feed | Valor nuevo en `ActivityEventType` (y el front lo contempla, ver ADR-010) |
| Excepción de negocio nueva | Mapeo en `ApiExceptionHandler` con `code` estable |
| Tests | Unitario junto al handler; controller con `ControllerTestSupport`; integración en `boot/src/test/.../it/` |

### 8. Cuándo parar y preguntar

Si una tarea obliga a romper una regla de las secciones 3, 4 o 6, a cambiar una decisión intencionada, o a crear una segunda forma de hacer algo que ya tiene dueño:

**PARAR** → nombrar la regla o decisión en conflicto → explicar qué afecta (datos, endpoints, front, tests) → proponer el cambio más pequeño que no la rompa → esperar respuesta del usuario antes de implementar.

También parar ante: cambios de contrato de la API que rompan al front actual, migraciones que borren o transformen datos de producción, y cualquier cambio en seguridad (sesión, CORS, rutas públicas).

## Errores y seguridad

**Excepción → HTTP** (`ApiExceptionHandler`, respuestas `ProblemDetail` con `code` estable):

| Exception | HTTP | `code` |
|-----------|------|--------|
| `IllegalArgumentException` | 400 | `BAD_REQUEST` |
| `BadCredentialsException` | 401 | `INVALID_CREDENTIALS` |
| `ForbiddenOperationException` | 403 | `FORBIDDEN` |
| `IllegalStateException` / `StaleOperationException` (confirma y luego 409) | 409 | `CONFLICT` |
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

## Cambios que afectan al frontend

Las reglas del front están en `C:\PokeFantasy\pokefantasy-web\CLAUDE.md`. Desde el backend basta con recordar: **tras cambiar un DTO, enum o endpoint**, en el front `npm run api:spec` → `npm run api:types`, commitear `openapi.json` y `src/api/schema.d.ts`, y arreglar `src/api/contract.ts` si `tsc` falla.
