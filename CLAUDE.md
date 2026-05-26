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

Use the Maven wrapper `./mvnw` from the **repo root** (`C:\PokeFantasy\pokefantasy`). Maven is not on PATH:

```bash
# Build + tests + coverage gate (80% instruction & branch, JaCoCo)
./mvnw -B -ntp clean verify

# Build without tests (used by Docker)
./mvnw -B -ntp clean package -DskipTests

# Application module tests only (fast, no infra/Redis/MongoDB needed)
./mvnw -B -ntp test -pl application -am       # -am builds domain dependency first

# Single test class
./mvnw -B -ntp test -pl application -am -Dtest=MyTestClass
```

Start infrastructure before running locally:

```bash
cd src && docker-compose up -d   # MongoDB :27017, Redis :6379
```

Required env var: `JWT_SECRET` (Base64-encoded 256-bit key). Optional: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL`, `MONGODB_URI`.

On startup, `PokemonCacheLoader` (`@PostConstruct`) fetches all ~1300 Pokémon from PokeAPI into Redis — **app fails to start if PokeAPI is unreachable**.

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

## Exception → HTTP mapping (`ApiExceptionHandler`)

| Exception | HTTP |
|-----------|------|
| `IllegalArgumentException` | 400 |
| `IllegalStateException` | 409 |
| `ForbiddenOperationException` | 403 |
| `Exception` | 500 |

## Security

Stateless JWT. Public endpoints (no token required): `POST /v1/user`, `POST /v1/user/login`, `GET /actuator/health`. Everything else requires `Authorization: Bearer <token>`. `LeagueAdminGuard.requireLeagueAdmin()` guards admin-only operations — checks `LeagueRole.ADMIN` in the league's member list.

CORS is restricted to `https://*.netlify.app` and `localhost` — no wildcard origin (`SecurityConfig.java`). If you add a custom domain, update `corsConfigurationSource()`.

## Key entities

**`UserEntity`** (collection `users`): `id`, `name` (username), `password` (bcrypt), `roles`, `pokemons` (`List<Pokemons>` with `leagueId` field to separate by league).

**`LeagueEntity`** (collection `leagues`): `id`, `name`, `createdBy`, `status`, `members` (`List<LeagueMember{username, leagueRole}>`). `leagueRole` is per-league (ADMIN / USER), not global.

**`ClosedListEntity`** (collection `closed_list`): `leagueId`, `pokemonId`, `pokemonName`, `nominatedBy`, `tier` (S/A/B/C/D), `stats`, `types`, `sprite`.

**`DraftEntity`** (collection `draft`): `id`, `leagueId`, `status` (PENDING/IN_PROGRESS/COMPLETED/CANCELLED), `turnOrder`, `currentTurnIndex` (0-based), `currentRound` (starts at 1), `picks` (`List<DraftPick{username, pokemonName, pokemonId, round, pickedAt}>`), `@Version` (optimistic locking).

**Critical**: `TeamsPage` in the frontend derives teams from `draft.picks`, NOT from `user.getPokemons()`. Any operation that changes a player's team (swap, etc.) must update **both**: `user.getPokemons()` AND the corresponding `DraftPick` in `DraftEntity`.

## Repository methods

- `UserRepository`: `findByUsername`, `saveUser`, `updateUserWithPokemons`
- `LeagueRepository`: `findById`, `findByMemberUsername`, `addMember`, `removeMember`
- `ClosedListRepository`: `findAllByLeagueId`, `findByPokemonNameIgnoreCaseAndLeagueId`
- `DraftRepository`: `findActiveByLeagueId` (PENDING/IN_PROGRESS only), `findLatestByLeagueId` (any status), `save`

## Current endpoints

```
POST   /v1/user                                    register
POST   /v1/user/login                              login
GET    /v1/leagues                                 my leagues
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
GET    /v1/leagues/{id}/bench                      bench (unchosen pokemons)
POST   /v1/leagues/{id}/bench/swap                 bench swap (tier parity + net coin change)
POST   /v1/leagues/{id}/bench/buy                  buy bench pokémon with coins (round=0 sentinel)
POST   /v1/leagues/{id}/steal                      steal rival's pokémon
PUT    /v1/leagues/{id}/steal-price                raise own pokémon steal price
GET    /v1/leagues/{id}/my-coins                   own coin balance
GET    /actuator/health                            health check (public)
```

## Tests

The `application` module has a unit test suite (255 tests, pure Mockito, no Spring context). JaCoCo 80% gate runs on `mvn verify` at BUNDLE level across all modules.

**What's covered**: `CreateUserCommandHandler`, `LoginUserCommandHandler`, `StartDraftCommandHandler`, `DraftPickCommandHandler`, `GetDraftStatusCommandHandler`, `SwapWithBenchCommandHandler`, `StealPokemonCommandHandler`, `SetStealPriceCommandHandler`, `LeagueAdminGuard`.

**Pattern**: all handlers use constructor injection — mock the ports and repositories, test business logic directly. No `@SpringBootTest` needed.

`UserEntity.name` has `@Indexed(unique=true)` — MongoDB enforces uniqueness at DB level, not application level (no TOCTOU race).

## Performance constraints (Render free tier)

- Minimise backend calls — use React Query cache with `staleTime`.
- Pokémon sprites always from CDN: `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/{id}.png`
- Don't add new endpoints if data already comes from an existing one.
- GitHub Action pings `/actuator/health` every 10 min to prevent Render cold starts.

## Frontend notes

Stack: React 19 + Vite + TypeScript, TanStack React Query, Zustand (auth: `token` + `username` in localStorage under `auth-storage`), Axios with Bearer interceptor, React Router v7.

TypeScript: use `import type { X }` for pure interfaces/types — **Netlify build fails if you don't**.

TypeScript check (no emit): `./node_modules/.bin/tsc --noEmit` from `pokefantasy-web/`. Run `npm install` first if `node_modules` is missing.

CSS design tokens in `src/index.css`. Animation utilities: `.animate-in`, `.stagger` (staggered children). Loading primitives: `.spinner`, `.skeleton`, `.loading-text`. Space Mono font for numeric stats (`.stat-pill-value`).

## Roadmap

**Done**: JWT auth, closed list + tiers, draft + 10 Pokémon/player limit, production deploy, login/register/home UI, pool selection page, league system with per-league roles, cancel draft, expel/leave league (cleans draft picks + adjusts turn order), teams view (TeamsPage), bench system (1-for-1 swap), keep-alive ping (every 5 min with retry), Redis cache skip on startup if already populated, auto-tier assignment on draft start (BST-relative quintiles, S/A/B/C/D), tier badges in pool / draft / teams UI, league settings with per-tier coin prices (priceTierS/A/B/C/D), round-robin calendar (primera + segunda vuelta, auto-generated on draft completion), admin records match results, seed script for complete demo league (8 players, 128 pool, 80 picks, 48 bench), **coin balance per player** (private, grows with wins/losses), **paid bench swaps** (tier parity rule — can't trade up; net coin change when trading down), **Pokémon steal system** (steal price = priceTierX or custom raised by owner; victim receives 2×; stolen Pokémon locked until jornada ends), **admin manual tier adjustment** (bidirectional cascade — promotion bumps lowest-BST from each intermediate tier down; demotion bumps highest-BST up; tier counts always balanced; dedicated `/leagues/:leagueId/tiers` admin page with tier tabs and cascade modal; TeamsPage cleaned of all tier-adjust code), **sticky own-team panel in TeamsPage** (user's team pinned below header via `position: sticky`; collapsible toggle; rival teams scroll underneath; `max-height: 42vh` with internal scroll for large teams), **configurable tier percentages** (admin sets % of pool per tier S/A/B/C/D; must sum 100; persisted in `LeagueSettings`; applied at draft start AND on every settings save via `TierAssignmentService` — changing percentages immediately re-tiers the entire pool including already-drafted Pokémon; settings editable any time except during IN_PROGRESS draft), **player-to-player trades** (1-for-1 between two players with optional coin cost; A proposes their Pokémon + target Pokémon + optional coins, B accepts or rejects; executes only on accept; global pending-trades notification banner across all leagues — PRs #40/#41 backend, #24/#25 frontend), **steal/swap window unification** (backend `JornadaWindowService` is the single source of truth; `ScheduleResponse` exposes `stealWindowOpen`/`swapWindowOpen`; frontend consumes them without recomputing date arithmetic — PRs #42/#44 backend, #26 frontend), **original draft history** (`DraftEntity.draftHistory` snapshots each user's first pick, immutable against steals/swaps/trades; shown in the draft history view), **activity feed** (chronological log of all league events — steals, swaps, trades, match results, tier changes, coin movements; `/leagues/:id/activity` page, polling every 30 s; new `activity_events` MongoDB collection — PRs #47 backend, #27 frontend), **settings page two-column layout** (sticky sidebar with save button always visible, pending-changes list with old→new values, unsaved-changes indicator — PR #29 frontend), **steal/swap window banners** (TeamsPage always shows both steal and swap window state simultaneously — PR #28 frontend), **buy bench Pokémon with coins** (player spends coins to acquire an unclaimed bench Pokémon without giving up any of their own; price = priceTierX; same swap window; `DraftPick.round = 0` sentinel; `BENCH_PURCHASE` activity event; unified `BenchActionModal` with choose/buy views — PRs #51 backend, #30 frontend), **popup unificado en Pokémon rival** (`RivalActionModal` shown when steal window is open — PR #31 frontend), **exportar equipo a Pokémon Showdown** (botón "📋 Showdown"; copia al portapapeles en formato Showdown; solo especies, sin backend — PR #32 frontend), **TeamsPage refactor** (1503 → 696 líneas; 5 modales extraídos; utils `sprites.ts` + `tiers.ts` — PR #33 frontend), **sistema centralizado de toasts** (Zustand `toastStore`, `ToastContainer` fixed bottom-right, auto-dismiss, helper `extractErrorMessage`; ~15 `useState` de error migrados en 14 archivos — PR #34 frontend), **página de clasificación** (`GET /v1/leagues/{id}/standings` agrega W/L y monedas; `StandingsPage` con tabla Pos/Jugador/PJ/V/D/Monedas, medallas top-3, fila propia destacada — PR #53 backend, #35 frontend), **Pokémon detail card modal** (sprite grande, tipos con pastillas de color, stats base HP/Atk/Def/SpA/SpD/Spe, tier badge; ℹ️ on hover en pool, draft, teams y banca; transform layer en `getClosedList` para aplanar shape de PokéAPI — PR #37 frontend), **versión 1.0.0** (badge `v1.0.0` bottom-right en frontend; pom.xml sin SNAPSHOT — PRs #36 frontend, #56 backend), **fix spring-boot-maven-plugin** (version 4.0.2 en `boot/pom.xml` elimina warning de Maven en Render — PR #50 backend).

**Next — Frontend** (en orden de valor):

1. **Skeletons de carga** — sustituir todos los `<p>Cargando...</p>` por skeleton screens (filas grises animadas). Mejora la percepción de velocidad sin tocar el backend. Patrón: clase `.skeleton` ya existe en `index.css`; crear componente `SkeletonTable` y `SkeletonGrid` reutilizables.

2. **Confirmación de pick en el draft** — al clicar un Pokémon durante el draft, mostrar un mini-modal de confirmación ("¿Confirmas elegir X?") antes de llamar a `draftPick`. Evita picks accidentales. Solo frontend: añadir estado `pendingPick` en `DraftPage`, reutilizar el patrón de modal existente.

3. **Temporizador de turno en el draft** — cuenta atrás visible para el jugador en turno (configurable por el admin en LeagueSettings, p.ej. 120 s). Si expira, el turno pasa automáticamente. Requiere: (a) campo `turnTimerSeconds` en `LeagueSettings` + `LeagueSettingsEntity`; (b) lógica de auto-skip en `DraftPickCommandHandler` al consultar el tiempo transcurrido desde `currentTurnStartedAt`; (c) timer visual en `DraftPage` que consuma el campo `turnDeadline` que devuelva `getDraftStatus`.

4. **Filtro/búsqueda en TeamsPage** — barra de búsqueda por nombre de Pokémon y filtro por tier en la vista de equipos rival. Solo frontend: `useState` para el filtro, aplicar sobre el array de picks antes de renderizar las tarjetas.

5. **Modo claro / Dark-light toggle** — botón en el header para alternar entre el tema oscuro actual y un tema claro. Solo frontend: añadir clase `theme-light` al `<html>`, reemplazar los valores de `--bg`, `--surface`, `--text-*` con CSS custom properties que se sobreescriban bajo `.theme-light`. Persistir elección en `localStorage`.

6. **Página de perfil de jugador** — ruta `/leagues/:leagueId/player/:username` con: equipo actual, picks del draft histórico, W/L, monedas. No requiere endpoint nuevo; los datos vienen de `getDraftStatus` + `getStandings`. Solo frontend.

7. **Actualizaciones en tiempo real del draft (SSE)** — reemplazar el polling de 5 s en `DraftPage` por una conexión `EventSource` a un endpoint SSE del backend. Requiere: (a) backend SSE `GET /v1/leagues/{id}/draft/events`; (b) frontend sustituir `refetchInterval: 5000` por hook `useSSE`. Reduce latencia de turno a <1 s y elimina carga innecesaria.

**Next — Backend** (cuando se retome):

1. **SSE para el draft** — endpoint `GET /v1/leagues/{id}/draft/events` que emite un evento cada vez que cambia el estado del draft (pick realizado, draft completado). Usa `SseEmitter` de Spring. Dependencia del item #8 de frontend.

2. **Auto-skip de turno por timeout** — `@Scheduled` que cada N segundos comprueba si el jugador en turno lleva más de `turnTimerSeconds` sin picar y llama al mediator para pasar al siguiente. Dependencia del item #4 de frontend.

3. **Undo del último pick (admin)** — endpoint `DELETE /v1/leagues/{id}/draft/picks/last` que revierte el último pick: elimina el `DraftPick` de `DraftEntity.picks`, devuelve el Pokémon a la banca, retrocede `currentTurnIndex`. Solo si el draft está `IN_PROGRESS`.

4. **Link de invitación** — `POST /v1/leagues/{id}/invite` genera un token de un solo uso (TTL 48 h, guardado en Redis) que al visitarse auto-añade al usuario a la liga sin que el admin tenga que añadirlo manualmente.

5. **Endpoint de estadísticas de temporada** — `GET /v1/leagues/{id}/season-stats` agrega: MVP por jugador (Pokémon con más victorias según el historial de resultados), racha actual (streak), porcentaje de victorias. Alimentaría mejoras futuras de la página de standings.

**Next — Mobile** (cuando estén terminadas las mejoras de frontend):

1. **App móvil con Capacitor** — envolver el React existente en un contenedor nativo para generar apps instalables en iOS (App Store) y Android (Play Store). Reutiliza toda la codebase actual sin reescribir nada. Pasos: `npm install @capacitor/core @capacitor/cli @capacitor/ios @capacitor/android` → `npx cap init` → `npm run build && npx cap sync` → abrir proyectos Xcode / Android Studio y compilar. Prerequisito: responsive CSS completo (item #1 del bloque frontend). Requiere cuenta Apple Developer ($99/año) para publicar en App Store.
