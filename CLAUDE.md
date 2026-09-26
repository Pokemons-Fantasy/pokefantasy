# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repos & Deploy

| Repo | Local path | Base branch | Deploy |
|------|-----------|-------------|--------|
| Backend | `C:\PokeFantasy\pokefantasy` | `develop` | Render (auto on push to `develop`) |
| Frontend | `C:\PokeFantasy\pokefantasy-web` | `main` | Netlify (auto on push to `main`) |

- GitHub org: https://github.com/Pokemons-Fantasy
- Production: https://pokefantasy.onrender.com

## Git workflow (mandatory)

### Antes de empezar cualquier tarea de implementación

1. **Revisar PRs abiertos** en ambos repos antes de crear ramas o tocar código:
   ```powershell
   $h = @{ Authorization = "Bearer $env:GITHUB_TOKEN"; Accept = "application/vnd.github+json" }
   Invoke-RestMethod "https://api.github.com/repos/Pokemons-Fantasy/pokefantasy-web/pulls?state=open" -Headers $h | Select number,title,@{n='branch';e={$_.head.ref}}
   Invoke-RestMethod "https://api.github.com/repos/Pokemons-Fantasy/pokefantasy/pulls?state=open"    -Headers $h | Select number,title,@{n='branch';e={$_.head.ref}}
   ```
   Si hay PRs abiertos, mencionarlos al usuario antes de continuar.

2. **"Añadir al roadmap"** significa editar `CLAUDE.md` **y** `docs/DIAGRAMS.md` (entidades, flujo de negocio y tabla de estado). Ambos archivos se actualizan siempre juntos. No implica implementación.

3. **Siempre invocar el skill `brainstorming`** antes de implementar cualquier feature nueva, aunque parezca simple.

---

Never push directly to `develop` (backend) or `main` (frontend). Always:

```
feature/... or fix/... → commit → push → PR
```

Backend PRs target `develop`; frontend PRs target `main`. There is no `gh` CLI — create PRs via GitHub API:

```powershell
$token = $env:GITHUB_TOKEN   # set in your shell, never hardcode
$headers = @{ Authorization = "Bearer $token"; Accept = "application/vnd.github+json" }
$pr = @{ title = "..."; head = "feature/..."; base = "develop"; body = "..." } | ConvertTo-Json
Invoke-RestMethod -Uri "https://api.github.com/repos/Pokemons-Fantasy/pokefantasy/pulls" -Method Post -Headers $headers -Body $pr -ContentType "application/json; charset=utf-8"
```

Shell: PowerShell on Windows. Git Bash also available via Bash tool (use paths like `/c/PokeFantasy/...`).

## Build commands

Use the Maven wrapper `./mvnw` from the **repo root** (`C:\PokeFantasy\pokefantasy`) with `-f src/pom.xml` (el `pom.xml` padre está en `src/`; el wrapper fija Maven 3.9.12). Maven is not on PATH. La CI (`.github/workflows/workflow.yml`, en cada PR y push a `develop`) ejecuta exactamente el primer comando y además construye la imagen Docker:

```bash
# Build + tests (unit, controllers, integration with Testcontainers if Docker is available) + coverage gate (80% JaCoCo)
./mvnw -B -ntp -f src/pom.xml clean verify

# Build without tests
./mvnw -B -ntp -f src/pom.xml clean package -DskipTests

# Application module tests only (fast, no infra/Redis/MongoDB needed)
./mvnw -B -ntp -f src/pom.xml test -pl application -am       # -am builds domain dependency first

# Single test class
./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=MyTestClass
```

**Versiones**: Spring Boot se versiona **solo** con el parent `spring-boot-starter-parent` de `src/pom.xml`; no fijes versiones de artefactos `org.springframework.boot` en los POM.

**Docker** (`Dockerfile` en la raíz, lo usa Render): build multi-stage con `./mvnw`; la capa de dependencias (`dependency:go-offline`) solo se rehace si cambia algún `pom.xml`. La imagen final corre como usuario `app` (no root) con `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` (heap ≈ 384 MB en los 512 MB de Render; ~240 MB en uso tras arrancar).

Start infrastructure before running locally:

```bash
cd src && docker-compose up -d   # MongoDB :27017, Redis :6379
```

Required env var: `JWT_SECRET` (Base64-encoded 256-bit key). Optional: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL`, `MONGODB_URI`, `SENTRY_DSN` (errores 500 a Sentry), `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (logs JSON).

`PokemonCacheLoader` carga los ~1300 Pokémon de PokeAPI en Redis **en segundo plano** (`@Scheduled`: al arrancar y cada minuto; si la clave `all_pokemons` ya existe no hace nada). Si PokeAPI no responde la app arranca igual y lo reintenta cada minuto; también se recupera sola si Redis se vacía. Mientras la caché esté vacía, nominar devuelve 409 ("aún se está cargando"). Los jobs `@Scheduled` comparten un pool de 3 hilos (`spring.task.scheduling.pool.size`).

## Architecture

5-module Maven multi-module, hexagonal architecture. All source under `src/`:

```
domain/         → Entities, repository interfaces, DTOs, port interfaces (no Spring)
application/    → Command/Mediator, Facades, business logic
infrastructure/ → Port implementations (MongoDB, Redis, JWT, PokeAPI)
api-rest/       → REST controllers + request/response DTOs
boot/           → Entry point, application.yml, assembles all modules
```

Dependency direction: `api-rest` → `application` → `domain` ← `infrastructure`. `boot` depends on all.

## CQRS / Mediator pattern — always follow this order

Every use case needs these four pieces, in this order:

1. **`XCommand`** — Java `record` that **must implement `interface Command`**. If it doesn't, `SpringMediator` won't register it (this caused a real bug).
2. **`XCommandHandler`** — `@Service` implementing `CommandHandler<XCommand, R>`.
3. **`XFacade`** — calls `mediator.send(new XCommand(...))`. One `send` per method.
4. **Controller** — injects the Facade only.

Folder: `application/src/main/java/com/villu/pokefantasy/commands/{feature}/`

`SpringMediator` auto-discovers all `CommandHandler` beans via constructor injection. Never call handlers directly.

### Transacciones y concurrencia

- `SpringMediator` ejecuta **cada comando dentro de una transacción MongoDB** (`TransactionPort` → `MongoTransactionAdapter`). Si algo falla, se deshacen todas las escrituras del comando: **no escribas compensaciones manuales**.
- Ante conflictos (`OptimisticLockingFailureException` o `TransientTransactionError`) el comando completo se **reintenta hasta 5 veces** con espera aleatoria creciente (50 ms·2ⁿ) → los handlers deben releer de BD todo lo que validan (ya lo hacen) y no tener efectos externos antes del commit. Push FCM se difiere a `afterCommit` automáticamente.
- `@Version` en `LeagueEntity`, `UserEntity`, `DraftEntity`, `ScheduleEntity`, `TradeEntity`, `ClosedListEntity`: un `save()` con datos desactualizados falla en vez de pisar cambios ajenos. Los updates atómicos (`$push`, `$addToSet`…) también incrementan `version`. `VersionFieldMigration` inicializa `version: 0` en documentos antiguos al arrancar.
- Para rechazar una operación **persistiendo** una limpieza previa (p. ej. cancelar un trade obsoleto) lanza `StaleOperationException`: la transacción se confirma y luego se devuelve 409.
- Requiere replica set (Atlas lo es). En local `docker-compose` levanta un replica set de un nodo. Contra un Mongo standalone los comandos corren sin transacción (WARN en el log al primer comando).

### Tiempo real (SSE) con varias instancias

- Para emitir un evento SSE usa **`RealtimeNotifier`** (`draftUpdated(leagueId)`, `notifyUser(username, event, data)`), nunca los registros directamente. Publica vía `RealtimeEventPort` → `RedisRealtimeEventAdapter` en el canal Redis `pokefantasy:realtime`; cada instancia está suscrita y entrega el evento a sus conexiones locales (`SseEmitterRegistry` / `UserSseEmitterRegistry` implementan `RealtimeEventPort.Listener`). Así funciona aunque el cliente esté conectado a otra instancia.
- Si Redis no acepta la publicación, el evento se entrega solo en la instancia local (con una sola instancia no se pierde nada). La suscripción la arranca `RealtimeSubscriptionStarter` en segundo plano (reintenta cada 10 s): la app arranca aunque Redis esté caído, y luego el contenedor se resuscribe solo.
- `spring.data.redis.timeout`/`connect-timeout` = 2 s: sin ello Lettuce espera 60 s por comando y, con Redis caído, cada petición que lo toca se colgaba un minuto.

### Zona horaria

Los deadlines de robo/swap (`stealWindowCloseDay/Time`, `swapWindowCloseDay/Time`) son hora de pared española: `JornadaWindowService` los evalúa en `JornadaWindowService.LEAGUE_ZONE` (`Europe/Madrid`, con horario de verano), no en la zona del servidor (Render corre en UTC).

## Exception → HTTP mapping (`ApiExceptionHandler`)

Todos los errores salen como `ProblemDetail` (`application/problem+json`): `status`, `title`, `detail` + `code` (estable, para que el cliente distinga casos) + `message` (= `detail`; es lo que lee `extractErrorMessage` en el frontend). Extiende `ResponseEntityExceptionHandler`, así que los errores de Spring MVC salen con su código real (405, 400, 404, 415…; `code` = nombre del estado) en vez de 500.

| Exception | HTTP | `code` |
|-----------|------|--------|
| `IllegalArgumentException` | 400 | `BAD_REQUEST` |
| `BadCredentialsException` | 401 | `INVALID_CREDENTIALS` |
| `ForbiddenOperationException` | 403 | `FORBIDDEN` |
| `IllegalStateException` / `StaleOperationException` (commits, then) | 409 | `CONFLICT` |
| `OptimisticLockingFailureException` | 409 | `CONCURRENT_MODIFICATION` |
| `DuplicateKeyException` | 409 | `DUPLICATE` |
| `TooManyAttemptsException` | 429 (+ `Retry-After`) | `TOO_MANY_ATTEMPTS` |
| Errores de Spring MVC | 400/404/405/415… | `METHOD_NOT_ALLOWED`, `BAD_REQUEST`… |
| `Exception` | 500 | `INTERNAL_ERROR` (sin detalles internos) |

## Security

Stateless JWT. Public endpoints (no token required): `POST /v1/user`, `POST /v1/user/login`, `POST /v1/user/logout`, `GET /actuator/health`. Todo lo demás (incluidos los SSE `/draft/events` y `/users/events`) exige sesión.

- **Sesión** (`JwtAuthFilter`): dos cookies httpOnly `SameSite=None; Secure` (`AuthCookies`). `jwt` = JWT de acceso de **15 min** (`jwt.expiration-ms`); `refresh` = token opaco aleatorio guardado en Redis como `refresh:<sha256>` → username (`RefreshTokenPort` → `RefreshTokenRedisAdapter`), caduca tras **30 días sin uso** (`jwt.refresh-expiration-days`). Si el JWT falta o caducó y el refresh es válido, el filtro emite un JWT nuevo en esa misma respuesta: el frontend no hace nada para renovar. `POST /v1/user/logout` revoca el refresh en Redis (el JWT emitido muere solo en ≤ 15 min). Si Redis falla, no hay refresco (fail-closed).
- **Búsqueda de usuarios** (`GET /v1/users/search?q=&leagueId=`): `leagueId` obligatorio y solo para admins de esa liga (es el autocompletado de "añadir miembro").
- **Draft**: `POST /draft/auto-pick` y `GET /draft/events` exigen ser miembro de la liga. `SseEmitterRegistry` admite como mucho 3 conexiones por usuario y liga (cierra la más antigua).

- **Registro** (`CreateUserCommandHandler`): username `^[A-Za-z0-9_-]{3,20}$`; contraseña ≥ 8 caracteres y ≤ 72 bytes (límite de bcrypt). Solo se valida al registrarse: los usuarios existentes siguen entrando.
- **Límite de intentos de login** (`LoginUserCommandHandler` + `LoginAttemptPort` → `LoginAttemptRedisAdapter`, claves `login-fail:*` en Redis): 5 fallos por usuario o 30 por IP en 15 min → 429 durante la ventana, aunque la contraseña sea correcta. Un login correcto limpia el contador del usuario. IP = primera entrada de `X-Forwarded-For` (la pone Render). Si Redis falla, no bloquea (fail-open). Everything else requires `Authorization: Bearer <token>`. `LeagueAdminGuard.requireLeagueAdmin()` guards admin-only operations — checks `LeagueRole.ADMIN` in the league's member list.

CORS is restricted to `https://*.netlify.app` and `localhost` — no wildcard origin (`SecurityConfig.java`). If you add a custom domain, update `corsConfigurationSource()`.

## Key entities

**`UserEntity`** (collection `users`): `id`, `name` (username), `password` (bcrypt), `role`, `fcmTokens`. **No guarda equipos** (el antiguo `pokemons` se eliminó; `DropUserPokemonsMigration` lo borra de Mongo al arrancar).

**`LeagueEntity`** (collection `leagues`): `id`, `name`, `createdBy`, `status`, `members` (`List<LeagueMember{username, leagueRole}>`). `leagueRole` is per-league (ADMIN / USER), not global.

**`ClosedListEntity`** (collection `closed_list`): `leagueId`, `pokemonId`, `pokemonName`, `nominatedBy`, `tier` (S/A/B/C/D), `stats`, `types`, `sprite`.

**`DraftEntity`** (collection `draft`): `id`, `leagueId`, `status` (PENDING/IN_PROGRESS/COMPLETED/CANCELLED), `turnOrder`, `currentTurnIndex` (0-based), `currentRound` (starts at 1), `picks` (`List<DraftPick{username, pokemonName, pokemonId, round, pickedAt}>`), `@Version` (optimistic locking).

**Critical**: `DraftEntity.picks` (del último draft de la liga) es la **única fuente de verdad de los equipos**. Cualquier operación que cambie un equipo (steal, trade, swap, buy, release) solo modifica los `DraftPick`. Para "qué está en la banca" usa `draft.ownedPokemonNames()` y para el tamaño de un equipo `draft.teamSize(username)`; un draft `CANCELLED` no deja a nadie con Pokémon.

**Movimientos de equipo** (robo, trade, swap, compra, liberación): usa `TeamTransferService` (abrir mercado = draft completado + calendario + liga + ventana de `TeamOperation`, bloqueo de 7 días `TRANSFER_LOCK`, cobro de monedas, banca). No repitas esas reglas en los handlers.

**Resultados de partidos** (`MatchResultService`, compartido por `RecordMatchResultCommandHandler` y `CorrectMatchResultCommandHandler`): al registrar se guardan en el `Match` las monedas dadas (`winnerCoins`/`loserCoins`); corregir o deshacer devuelve **esas** (en resultados antiguos sin ellas, las de los ajustes actuales) y el saldo puede quedar negativo si ya se gastaron. Deja un evento `MATCH_RESULT_REVERTED` y un `COIN_REVOKED` por jugador con lo retirado (contrapartida de los `COIN_EARNED`, para que el historial de monedas cuadre con el saldo); clasificación y estadísticas se recalculan solas desde el calendario.

## Repository methods

- `UserRepository`: `findByUsername`, `saveUser`, `addFcmToken`, `removeFcmToken`
- `LeagueRepository`: `findById`, `findByMemberUsername`, `addMember`, `removeMember`
- `ClosedListRepository`: `findAllByLeagueId`, `findByPokemonNameIgnoreCaseAndLeagueId`
- `DraftRepository`: `findActiveByLeagueId` (PENDING/IN_PROGRESS only), `findLatestByLeagueId` (any status), `save`

## Current endpoints

```
POST   /v1/user                                    register
POST   /v1/user/login                              login
GET    /v1/leagues/my                              my leagues
POST   /v1/leagues                                 create league
GET    /v1/leagues/{id}                            league detail
POST   /v1/leagues/{id}/members                    add member
DELETE /v1/leagues/{id}/members/{username}         expel/leave
GET    /v1/leagues/{id}/closed-list                pokemon pool
POST   /v1/leagues/{id}/closed-list/nominate       nominate pokemon
DELETE /v1/leagues/{id}/closed-list/nominate/{name}
POST   /v1/leagues/{id}/draft/start                start draft
POST   /v1/leagues/{id}/draft/pick                 make pick
GET    /v1/leagues/{id}/draft                      draft status
DELETE /v1/leagues/{id}/draft                      cancel draft
POST   /v1/leagues/{id}/draft/auto-pick             auto-pick random pokemon when turn timer expires (el cliente lo lanza al llegar a 0; además `DraftTurnTimeoutJob` lo hace en servidor cada 15 s por si nadie tiene la app abierta)
GET    /v1/leagues/{id}/bench                      bench (unchosen pokemons)
POST   /v1/leagues/{id}/bench/swap                 bench swap (tier parity + net coin change)
POST   /v1/leagues/{id}/bench/buy                  buy bench pokémon with coins (round=0 sentinel)
POST   /v1/leagues/{id}/steal                      steal rival's pokémon
PUT    /v1/leagues/{id}/steal-price                raise own pokémon steal price
GET    /v1/leagues/{id}/my-coins                   own coin balance
GET    /v1/leagues/{id}/trades?history=50           my trades: all pending + latest N resolved (max 200)
POST   /v1/leagues/{id}/schedule/matches/{matchId}/result   record result (admin)
PUT    /v1/leagues/{id}/schedule/matches/{matchId}/result   correct winner of a recorded match (admin)
DELETE /v1/leagues/{id}/schedule/matches/{matchId}/result   undo result → PENDING, coins returned (admin)
GET    /actuator/health                            health check (public)
```

## Tests

`./mvnw -B -ntp -f src/pom.xml clean verify` ejecuta todo con umbral JaCoCo del 80 % (instrucciones y ramas) en `application`, `infrastructure` y `api-rest` (`lombok.config` excluye el código generado por Lombok).

- **Unitarios** (`application`, `infrastructure`): Mockito puro, sin contexto de Spring. Los handlers usan inyección por constructor: se mockean puertos y repositorios.
- **Controladores** (`api-rest`): MockMvc standalone con las fachadas mockeadas (`ControllerTestSupport`: `ApiExceptionHandler` real + usuario autenticado).
- **Integración** (`boot/src/test/.../it/*IntegrationTest`): la app completa por HTTP contra **Mongo en replica set y Redis reales con Testcontainers** (base `IntegrationTest`; se saltan si no hay Docker). Cubren sesión/refresh/logout, límite de login, ProblemDetail, rollback de transacciones, concurrencia sin actualizaciones perdidas, SSE vía Redis Pub/Sub, índices y OpenAPI. Ojo: en Testcontainers 2 el replica set es opcional (`withReplicaSet()`); sin él no hay transacciones.

`UserEntity.name` tiene índice único (lo crea `MongoIndexInitializer`, ver abajo): la unicidad la garantiza MongoDB, no la aplicación.

## MongoDB: índices

`MongoIndexInitializer` crea al arrancar los índices de las anotaciones (`@Indexed`, `@CompoundIndex`) de las entidades de `INDEXED_ENTITIES`; si uno falla (p. ej. el único de `users.name` con duplicados) lo registra como ERROR sin tumbar el arranque. **No** se usa `auto-index-creation` (en Boot 4 la clave es `spring.data.mongodb.auto-index-creation`; la antigua `spring.mongodb.…` se ignoraba y no se creaba ningún índice). Si añades una entidad con índices, añádela a `INDEXED_ENTITIES`.

## Observabilidad

- **Request id**: `RequestIdFilter` (`X-Request-Id` entrante o generado) → MDC `requestId`, cabecera de respuesta, cada línea de log y `requestId` en los errores ProblemDetail.
- **Métricas**: `SpringMediator` mide cada comando → timer `pokefantasy.commands` (`command`, `outcome` = success/rejected/error, `exception`). `/actuator/metrics` solo para el rol global `ADMIN`.
- **Logs JSON**: `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`.
- **Sentry**: con `SENTRY_DSN`, los logs ERROR (los 500) llegan a Sentry; sin DSN no hace nada.
- **API docs**: OpenAPI en `/v3/api-docs`, Swagger UI en `/swagger-ui.html` (públicos). La CI publica `openapi.json` como artefacto. El frontend genera sus tipos a partir de él (ver *Frontend notes → Tipos de la API*).

## Performance constraints (Render free tier)

- Minimise backend calls — use React Query cache with `staleTime`.
- Pokémon sprites always from CDN: `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/{id}.png`
- Don't add new endpoints if data already comes from an existing one.
- GitHub Action pings `/actuator/health` every 10 min to prevent Render cold starts.

## Frontend notes

Stack: React 19 + Vite + TypeScript, TanStack React Query, Zustand (auth: `token` + `username` in localStorage under `auth-storage`), Axios with Bearer interceptor, React Router v7.

TypeScript: use `import type { X }` for pure interfaces/types — **Netlify build fails if you don't**.

TypeScript check (no emit): `./node_modules/.bin/tsc --noEmit` from `pokefantasy-web/`. Run `npm install` first if `node_modules` is missing.

**Tipos de la API** (tras cambiar un DTO, un enum o un endpoint en el backend):
1. `npm run api:spec` descarga la spec del backend local (`http://localhost:8080/v3/api-docs`) a `openapi.json` (o `npm run api:spec -- <url>`, p. ej. la de producción). El backend sirve la spec sin Mongo ni Redis si se arranca con `SPRING_MAIN_LAZY_INITIALIZATION=true`.
2. `npm run api:types` regenera `src/api/schema.d.ts` (no editar a mano). Se commitean los dos ficheros; la CI del frontend falla (`npm run api:check`) si no corresponden.
3. `src/api/contract.ts` compara en compilación los tipos escritos a mano de `src/api/*.ts` con los del esquema: si el backend renombra o quita un campo, o un enum gana un valor que el frontend no contempla, `tsc` falla ahí. Si añades un tipo de respuesta nuevo, añade su entrada.

CSS design tokens in `src/index.css`. Animation utilities: `.animate-in`, `.stagger` (staggered children). Loading primitives: `.spinner`, `.skeleton`, `.loading-text`. Space Mono font for numeric stats (`.stat-pill-value`).

## Roadmap

**Done**: JWT auth, closed list + tiers, draft + 10 Pokémon/player limit, production deploy, login/register/home UI, pool selection page, league system with per-league roles, cancel draft, expel/leave league (cleans draft picks + adjusts turn order), teams view (TeamsPage), bench system (1-for-1 swap), keep-alive ping (every 5 min with retry), Redis cache skip on startup if already populated, auto-tier assignment on draft start (BST-relative quintiles, S/A/B/C/D), tier badges in pool / draft / teams UI, league settings with per-tier coin prices (priceTierS/A/B/C/D), round-robin calendar (primera + segunda vuelta, auto-generated on draft completion), admin records match results, seed script for complete demo league (8 players, 128 pool, 80 picks, 48 bench), **coin balance per player** (private, grows with wins/losses), **paid bench swaps** (tier parity rule — can't trade up; net coin change when trading down), **Pokémon steal system** (steal price = priceTierX or custom raised by owner; victim receives 2×; stolen Pokémon locked until jornada ends), **admin manual tier adjustment** (bidirectional cascade — promotion bumps lowest-BST from each intermediate tier down; demotion bumps highest-BST up; tier counts always balanced; dedicated `/leagues/:leagueId/tiers` admin page with tier tabs and cascade modal; TeamsPage cleaned of all tier-adjust code), **sticky own-team panel in TeamsPage** (user's team pinned below header via `position: sticky`; collapsible toggle; rival teams scroll underneath; `max-height: 42vh` with internal scroll for large teams), **configurable tier percentages** (admin sets % of pool per tier S/A/B/C/D; must sum 100; persisted in `LeagueSettings`; applied at draft start AND on every settings save via `TierAssignmentService` — changing percentages immediately re-tiers the entire pool including already-drafted Pokémon; settings editable any time except during IN_PROGRESS draft), **player-to-player trades** (1-for-1 between two players with optional coin cost; A proposes their Pokémon + target Pokémon + optional coins, B accepts or rejects; executes only on accept; global pending-trades notification banner across all leagues — PRs #40/#41 backend, #24/#25 frontend), **steal/swap window unification** (backend `JornadaWindowService` is the single source of truth; `ScheduleResponse` exposes `stealWindowOpen`/`swapWindowOpen`; frontend consumes them without recomputing date arithmetic — PRs #42/#44 backend, #26 frontend), **original draft history** (`DraftEntity.draftHistory` snapshots each user's first pick, immutable against steals/swaps/trades; shown in the draft history view), **activity feed** (chronological log of all league events — steals, swaps, trades, match results, tier changes, coin movements; `/leagues/:id/activity` page, polling every 30 s; new `activity_events` MongoDB collection — PRs #47 backend, #27 frontend), **settings page two-column layout** (sticky sidebar with save button always visible, pending-changes list with old→new values, unsaved-changes indicator — PR #29 frontend), **steal/swap window banners** (TeamsPage always shows both steal and swap window state simultaneously — PR #28 frontend), **buy bench Pokémon with coins** (player spends coins to acquire an unclaimed bench Pokémon without giving up any of their own; price = priceTierX; same swap window; `DraftPick.round = 0` sentinel; `BENCH_PURCHASE` activity event; unified `BenchActionModal` with choose/buy views — PRs #51 backend, #30 frontend), **popup unificado en Pokémon rival** (`RivalActionModal` shown when steal window is open — PR #31 frontend), **exportar equipo a Pokémon Showdown** (botón "📋 Showdown"; copia al portapapeles en formato Showdown; solo especies, sin backend — PR #32 frontend), **TeamsPage refactor** (1503 → 696 líneas; 5 modales extraídos; utils `sprites.ts` + `tiers.ts` — PR #33 frontend), **sistema centralizado de toasts** (Zustand `toastStore`, `ToastContainer` fixed bottom-right, auto-dismiss, helper `extractErrorMessage`; ~15 `useState` de error migrados en 14 archivos — PR #34 frontend), **página de clasificación** (`GET /v1/leagues/{id}/standings` agrega W/L y monedas; `StandingsPage` con tabla Pos/Jugador/PJ/V/D/Monedas, medallas top-3, fila propia destacada — PR #53 backend, #35 frontend), **Pokémon detail card modal** (sprite grande, tipos con pastillas de color, stats base HP/Atk/Def/SpA/SpD/Spe, tier badge; ℹ️ on hover en pool, draft, teams y banca; transform layer en `getClosedList` para aplanar shape de PokéAPI — PR #37 frontend), **versión 1.0.0** (badge `v1.0.0` bottom-right en frontend; pom.xml sin SNAPSHOT — PRs #36 frontend, #56 backend), **fix spring-boot-maven-plugin** (version 4.0.2 en `boot/pom.xml` elimina warning de Maven en Render — PR #50 backend), **SSE notificaciones usuarios** (robo + trade propuesto en tiempo real; `UserSseEmitterRegistry`; endpoint autenticado `GET /v1/users/events`; `useNotificationSse` reemplaza polling 30s con fallback polling 120s si SSE se cierra — PRs #72 backend, #62 frontend), **fix PORT env var** (Render inyecta `PORT`; `application.yml` usa `${PORT:8080}` — PR #73 backend), **JWT httpOnly cookie** (`SameSite=None; Secure`; CORS `allowCredentials(true)`; `JwtAuthFilter` lee cookie primero, Bearer header como fallback; `POST /v1/user/logout` limpia cookie; SSE usa `@AuthenticationPrincipal`; `withCredentials: true` en Axios + EventSource; `authStore` solo persiste `username` — PRs #74 backend, #63 frontend), **versión 1.1.0** (badge `v1.1.0`; `versionCode 2 / versionName "1.1"` en Android; Dockerfile usa wildcard `boot-*.jar` — PRs #75 backend, directo a main frontend), **skeletons de carga** (componentes `SkeletonTable` y `SkeletonGrid` reutilizables; sustituyen los `<p>Cargando...</p>` en todas las páginas), **confirmación de pick en el draft** (modal `pendingPick` en `DraftPage` antes de llamar `draftPick` — evita picks accidentales), **temporizador de turno en el draft** (`turnTimerSeconds` en `LeagueSettings`; `currentTurnStartedAt` en `DraftEntity`; `turnDeadline` en `DraftStatusResponse`; cuenta atrás en `DraftPage`; `POST /auto-pick` con `AutoPickDraftCommandHandler` inyectando `DraftPickCommandHandler` directamente para evitar dependencia circular con `SpringMediator`; 348 tests — PRs #58 backend, #40 frontend), **diseño responsive para móvil** (`own-team-panel-header` reestructurado en info/actions groups; clase `.pokemon-grid-modal` corrige overflow de grids en modales; `modal-actions` wrappea en pantallas estrechas; header-right compacto en mobile), **filtro de rivales en TeamsPage** (input de nombre + pills S/A/B/C/D multiselect encima de los equipos rivales; equipos con 0 resultados se ocultan; contador de rivales/pokémon visibles; botón limpiar; equipo propio y banca no afectados — PR #43 frontend), **dark/light toggle** (botón ☀️/🌙 en todos los headers via componente `PageHeader` compartido; CSS vars bajo `.theme-light` en `<html>`; persistido en `localStorage`; flash prevention con inline script en `index.html` — PR #44 frontend), **página de perfil de jugador** (ruta `/leagues/:leagueId/players/:username`; avatar + stats W/L/PJ/monedas; equipo actual en grid; historial original del draft si difiere del equipo actual; `MyProfilePage` en `/profile` con ligas del usuario; `PageHeader` compartido con back customizable — PR #45 frontend), **actualizaciones en tiempo real del draft (SSE)** (`SseEmitterRegistry` con `Map<leagueId, List<SseEmitter>>`; heartbeat cada 30 s contra timeout de Render; endpoint público `GET /v1/leagues/{id}/draft/events`; `DraftController` llama `broadcastUpdate` tras pick/start/cancel/auto-pick; frontend reemplaza `refetchInterval: 5000` por `EventSource` con fallback polling 10 s si SSE se cierra; 367 tests — PRs #59 backend, #46 frontend), **gestión de miembros** (link de invitación de un solo uso TTL 48h en Redis — admin genera token desde `LeagueDetailPage`, cualquier usuario autenticado lo canjea en `/invite/:token`; autocomplete de username al añadir miembro con debounce 300ms, regex MongoDB `^prefix` case-insensitive, excluye ya-miembros; `InviteRepository` + `GenerateInviteLink` + `RedeemInvite` + `SearchUsers` commands/handlers; 19 tests nuevos — PRs #60 backend, #47 frontend).

**endpoint de estadísticas de temporada** (`GET /v1/leagues/{id}/season-stats`; por jugador: wins, losses, played, winPct, currentStreak positivo/negativo, mvpPokemon (primer pick de draftHistory); ordenado por wins DESC; 375 tests — PR #61 backend), **bloqueo temporal de robos/trades** (`DraftPick.lockedUntilRound: Integer` → `lockedUntil: Instant`; al robar o aceptar trade: `lockedUntil = now + 7 días`; check puro de timestamp sin depender del estado de jornada; `ProposeTradeCommandHandler` ya no inyecta `ScheduleRepository`; migración automática — docs con `lockedUntilRound` se leen como desbloqueados; tooltip muestra fecha exacta DD/MM HH:MM; 378 tests — PRs #62 backend, #48 frontend), **app Android (Capacitor)** (Capacitor 7, `appId: com.pokefantasy.app`, `androidScheme: https`; `@capacitor/clipboard` reemplaza `navigator.clipboard`; APK compilable desde Android Studio en Windows), **push notifications FCM** (Firebase Admin SDK 9.4.2; `PushNotificationPort` + `FirebasePushNotificationAdapter`; `POST /v1/users/push-token`; `UserEntity.fcmTokens` con `$addToSet`/`$pull`; dispatch en robo y trade propuesto; token refresh en login via `useEffect` en `App.tsx`; `deleteToken()` en `MainActivity.java` para invalidar caché en reinstalación; `google-auth-library-credentials:1.29.0` explícito para evitar conflict con Spring Boot 4.0.2 BOM — gotcha documentado en sección Mobile), **safe area Android/iOS** (`env(safe-area-inset-top)` en `.page-header`; `calc(60px + env(...))` en `.own-team-panel` sticky; `env(safe-area-inset-bottom)` en `body`).

**Deuda técnica** (sin deuda técnica activa)

**Next** (sin pendientes activos — proyecto completo en v1.1.0)

**Mobile — DONE** (Capacitor wraps the existing React app — zero rewrite. iOS fuera de scope hasta tener Mac + Apple Developer account):

1. ~~**Proyecto Firebase**~~ — **DONE**. Proyecto `pokefantasy-5920a` en Firebase Console. Service account JSON en `FIREBASE_SERVICE_ACCOUNT_JSON` env var de Render.
2. ~~**CORS para Capacitor**~~ — **DONE** (PR #64). `capacitor://localhost` añadido a `allowedOriginPatterns`.
3. ~~**Capacitor setup + Android**~~ — **DONE**. `@capacitor/core`, `@capacitor/cli`, `@capacitor/android`, `@capacitor/clipboard`. `capacitor.config.ts` (`appId: com.pokefantasy.app`, `webDir: dist`, `androidScheme: https`). Scripts `cap:sync` y `cap:open`. APK compilable desde Android Studio.
4. ~~**Push notifications — backend**~~ — **DONE**. `PushNotificationPort` (domain interface), `FirebasePushNotificationAdapter` (firebase-admin 9.4.2, `sendEachForMulticast`, cleanup de tokens UNREGISTERED/INVALID_ARGUMENT), `POST /v1/users/push-token` (CQRS `RegisterPushTokenCommand`), `UserEntity.fcmTokens` (`$addToSet`). Disparado en robo (`StealPokemonCommandHandler`) y trade propuesto (`ProposeTradeCommandHandler`). **Gotcha crítico**: Spring Boot 4.0.2 BOM pinea `google-auth-library-credentials` a `1.23.0` pero `firebase-admin` necesita `1.29.0` (contiene `CredentialTypeForMetrics`). Fix: dependency explícita `google-auth-library-credentials:1.29.0` en `infrastructure/pom.xml`.
5. ~~**Push notification client — frontend**~~ — **DONE**. `@capacitor/push-notifications` v8. `main.tsx`: `requestPermissions` → `register()` → listener `registration` usa `apiClient.post('/v1/users/push-token')` (cookie httpOnly automática). `App.tsx`: `useEffect` sobre `username` de Zustand llama `register()` al iniciar sesión (evita race condition si la app se abre sin sesión). `MainActivity.java`: `FirebaseMessaging.deleteToken()` en primer arranque via `SharedPreferences` flag `fcm_token_reset_v1` para forzar token fresco tras reinstalación. `android/app/build.gradle`: `proguard-android-optimize.txt` (AGP 9.2.1 eliminó `proguard-android.txt`) + `firebase-messaging:24.1.1`.
6. ~~**Safe area Android**~~ — **DONE**. `viewport-fit=cover` ya estaba en `index.html`. Añadido en `index.css`: `padding-top: env(safe-area-inset-top)` en `.page-header`; `top: calc(60px + env(safe-area-inset-top))` en `.own-team-panel` (sticky); `padding-bottom: env(safe-area-inset-bottom)` en `body`. Header light-theme background fix incluido.
7. ~~**App icons + splash screen**~~ — **DONE**.
8. ~~**Deep links (invite)**~~ — **DONE**.
9. ~~**Status bar color**~~ — **DONE**.
10. ~~**Haptics**~~ — **DONE**. `@capacitor/haptics@8.0.2`. `ImpactStyle.Medium` en pick de draft (`DraftPage.tsx`) y aceptar trade (`TradesModal.tsx`); `ImpactStyle.Heavy` en confirmar robo (`TeamsPage.tsx`). Disparo en `onSuccess` de cada mutación. `@capacitor/status-bar@8.0.2`. `useTheme.ts` llama `StatusBar.setStyle` + `StatusBar.setBackgroundColor` en el `useEffect` existente. Dark: `#0a0a0f` + `Style.Dark`; Light: `#f4f4f8` + `Style.Light`. Se aplica en mount y en cada toggle. Custom scheme `pokefantasy://`. Intent-filter en `AndroidManifest.xml` para scheme `pokefantasy://`. `@capacitor/app` instalado. `DeepLinkHandler` en `App.tsx` escucha `appUrlOpen` y `getLaunchUrl` para navegar al path correcto (app en ejecucion y arranque en frio). `LeagueDetailPage` genera `pokefantasy://invite/{token}` en native y `${origin}/invite/{token}` en web. Icono "PF" bold monospace dorado `#fbbf24` sobre negro `#0a0a0f`. Script `scripts/generate-assets.mjs` (sharp + SVG) genera `resources/icon.png` (1024×1024) y `resources/splash.png` (2732×2732). `@capacitor/assets generate --android` distribuye a todos los densities (87 assets). `values/colors.xml` con `colorSplashBackground`; `values-v31/styles.xml` con `windowSplashScreenBackground` + `windowSplashScreenAnimatedIcon` para splash nativo Android 12+. Regenerar con `npm run generate:assets`.

**Notas operativas Mobile**:
- FCM **no funciona** en emuladores Android Studio estándar — requiere imagen con Google Play o dispositivo físico.
- Flujo deploy: `npm run build` → `npm run cap:sync` → Android Studio → Build APK.
- `google-services.json` (de Firebase Console) debe estar en `android/app/` — no se commitea al repo.

**Mobile — completo**. Sin pendientes.
