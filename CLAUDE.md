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

**Done**: JWT auth, closed list + tiers, draft + 10 Pokémon/player limit, production deploy, login/register/home UI, pool selection page, league system with per-league roles, cancel draft, expel/leave league (cleans draft picks + adjusts turn order), teams view (TeamsPage), bench system (1-for-1 swap), keep-alive ping (every 5 min with retry), Redis cache skip on startup if already populated, auto-tier assignment on draft start (BST-relative quintiles, S/A/B/C/D), tier badges in pool / draft / teams UI, league settings with per-tier coin prices (priceTierS/A/B/C/D), round-robin calendar (primera + segunda vuelta, auto-generated on draft completion), admin records match results, seed script for complete demo league (8 players, 128 pool, 80 picks, 48 bench), **coin balance per player** (private, grows with wins/losses), **paid bench swaps** (tier parity rule — can't trade up; net coin change when trading down), **Pokémon steal system** (steal price = priceTierX or custom raised by owner; victim receives 2×; stolen Pokémon locked until jornada ends), **admin manual tier adjustment** (bidirectional cascade — promotion bumps lowest-BST from each intermediate tier down; demotion bumps highest-BST up; tier counts always balanced; clickable badges in TeamsPage).

**Next** (in order):
1. **Sticky own-team panel in TeamsPage** — when browsing rivals' teams or the bench, the current user's team stays pinned/visible so they can compare their Pokémon against opponents without scrolling back up. Especially important when the user is first in the list.
2. **Player-to-player trades** — 1-for-1 swap between two players with optional coin cost. Flow: player A proposes the trade (specifies their Pokémon + target Pokémon + optional coins); player B receives a notification and decides whether to accept or reject. Trade only executes if B accepts.
3. **Activity feed / movement panel** — chronological log of all league events visible to all members: steals, bench swaps, trades, match results, tier changes, coin movements. Each entry shows who did what, which Pokémon, and the timestamp.
4. **Settings page layout redesign** — current layout is a single long vertical scroll. Split into two columns: settings fields on the left, sticky sidebar on the right with the save button, a summary of pending changes, and an unsaved-changes indicator. No more scrolling to the bottom to save.
