# Bloqueo hasta fin de jornada Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un Pokémon que entra en un equipo (robo, trade, compra o swap) durante la jornada N queda bloqueado hasta que la jornada N tenga todos sus resultados, en lugar de 7 días.

**Architecture:** `DraftPick.lockedThroughJornada` guarda la jornada activa al fichar. El bloqueo se deriva del calendario (`JornadaWindowService`): bloqueado mientras esa jornada tenga algún partido no `COMPLETED`. El back expone `locked` ya calculado (ADR-003: el front no deduce estados de jornada). Una migración convierte los `lockedUntil` vigentes usando la fecha del último `MATCH_RESULT` de la jornada anterior.

**Tech Stack:** Spring Boot + MongoDB (back, base `develop`), React + TS + Vitest (front, base `main`).

**Spec:** diseño aprobado en chat el 2026-10-20 (opción A); se documenta en el vault como ADR-011 (Task 6).

## Global Constraints

- Back: rama `feature/bloqueo-jornada` desde `origin/develop`. Front: `feature/bloqueo-jornada` desde `origin/main`.
- PRs y consultas a GitHub solo con la integración de GitHub (sin token ni API REST).
- Cada comando corre en transacción Mongo; sin efectos externos en handlers. Command debe implementar `Command`.
- Mensaje de error exacto: `'<pokemonName>' está bloqueado hasta que termine la jornada <N>.`
- Chip del front: texto `🔒 J<N>`, `title` `Bloqueado hasta que termine la jornada <N>`.
- Front tolera back viejo: si `locked` no viene, usar la regla antigua de `lockedUntil`.
- Tras cambiar el back, regenerar tipos del front: `npm run api:spec` + `npm run api:types`. `import type` para tipos.
- Tests del front con `rtk proxy npx vitest run` (rtk oculta la salida de vitest); typecheck con `npx tsc -p tsconfig.app.json --noEmit`.
- Build back: `./mvnw -f src/pom.xml verify` desde la raíz del repo.

## Review Focus

- Deshacer un resultado de la jornada N vuelve a bloquear lo fichado en N (test en Task 1).
- Pick sin `lockedThroughJornada` (draft original, o migrado como libre) → nunca bloqueado (Task 1).
- `lockedThroughJornada` apunta a una jornada que ya no existe en el calendario (calendario regenerado) → libre (Task 1).
- Migración sobre un pick fichado **después** del último resultado de la jornada anterior → sigue bloqueado hasta fin de la activa; fichado **antes** → libre (Task 4).
- Liga sin `MATCH_RESULT` de la jornada anterior (o jornada activa 1) → migración conservadora: bloquea hasta la activa (Task 4).

---

### Task 1: Regla de bloqueo en dominio y `TeamTransferService`

**Files:**
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/entity/DraftPick.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/schedule/JornadaWindowService.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/team/TeamTransferService.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/team/TeamTransferServiceTest.java`, `.../commands/schedule/JornadaWindowServiceTest.java`

**Interfaces:**
- Produces:
  - `DraftPick.lockedThroughJornada: Integer` (null = libre). `lockedUntil` se mantiene en la entidad solo para la migración (Task 4) y se marca `@Deprecated`; nada nuevo lo escribe.
  - `JornadaWindowService.isJornadaCompleted(ScheduleEntity schedule, int roundNumber): boolean` — true si la jornada no existe o todos sus partidos están `COMPLETED` (reutiliza `allCompleted`).
  - `TeamTransferService.Market(DraftEntity draft, LeagueEntity league, ScheduleEntity schedule)`.
  - `TeamTransferService.isLocked(DraftPick pick, ScheduleEntity schedule): boolean`
  - `TeamTransferService.requireUnlocked(DraftPick pick, ScheduleEntity schedule)` — 409 (`IllegalStateException`) con el mensaje de Global Constraints.
  - `TeamTransferService.currentJornada(ScheduleEntity schedule): int` — `roundNumber` de `getActiveJornada`; `IllegalStateException("La temporada ha terminado")` si no hay.
  - `transfer(DraftPick pick, String newOwner, int jornada, Instant now)`, `addFromBench(DraftEntity, String, ClosedListEntity, int jornada, Instant now)`, `replaceWithBench(DraftEntity, DraftPick, ClosedListEntity, int jornada)` — ponen `lockedThroughJornada = jornada` y `lockedUntil = null`.
  - Se elimina `TRANSFER_LOCK`.

- [ ] **Step 1: Tests que fallan** en `TeamTransferServiceTest`:
  - `isLocked_jornadaPendiente_true`: pick `lockedThroughJornada=2`, jornada 2 con un partido PENDING → true.
  - `isLocked_jornadaCompletada_false`: jornada 2 toda COMPLETED → false.
  - `isLocked_resultadoDeshecho_vuelveABloquear`: misma pick; jornada 2 COMPLETED → false; se pone un partido a PENDING → true.
  - `isLocked_sinJornada_false`: `lockedThroughJornada=null` → false.
  - `isLocked_jornadaInexistente_false`: `lockedThroughJornada=9` en calendario de 2 jornadas → false.
  - `requireUnlocked_bloqueado_mensaje`: lanza `IllegalStateException` con mensaje `'Lapras' está bloqueado hasta que termine la jornada 2.`
  - `transfer_bloqueaHastaJornadaIndicada`: tras `transfer(pick, "brock", 3, now)` → `lockedThroughJornada == 3`, `lockedUntil == null`, `username == "brock"`, `customStealPrice` conservado.
  - Equivalentes para `addFromBench` y `replaceWithBench` (`lockedThroughJornada == 3`).
  - En `JornadaWindowServiceTest`: `isJornadaCompleted_pendiente_false`, `isJornadaCompleted_completada_true`, `isJornadaCompleted_noExiste_true`.
- [ ] **Step 2:** `./mvnw -f src/pom.xml -pl application -am test -Dtest='TeamTransferServiceTest,JornadaWindowServiceTest'` → FAIL (no compila).
- [ ] **Step 3:** Implementar las firmas de Interfaces. `openMarket` ya carga el `schedule`: lo devuelve en `Market`. Actualizar los javadocs de `DraftPick` y de la clase.
- [ ] **Step 4:** Las firmas nuevas rompen la compilación de los handlers: Task 1 no se commitea sola. Pasar directamente a Task 2; los tests de Task 1 se verifican en el Step 4 de Task 2.

### Task 2: Handlers

**Files:**
- Modify: `commands/steal/StealPokemonCommandHandler.java`, `commands/trade/RespondToTradeCommandHandler.java`, `commands/trade/ProposeTradeCommandHandler.java`, `commands/bench/BuyFromBenchCommandHandler.java`, `commands/bench/SwapWithBenchCommandHandler.java`, `commands/bench/ReleasePokemonCommandHandler.java` (todos bajo `src/application/src/main/java/com/villu/pokefantasy/`)
- Test: los `*CommandHandlerTest` correspondientes

**Interfaces:**
- Consumes: Task 1 (`Market.schedule()`, `currentJornada`, `requireUnlocked(pick, schedule)`, nuevas firmas de `transfer`/`addFromBench`/`replaceWithBench`).

- [ ] **Step 1: Tests que fallan** (uno por handler, en su test existente):
  - Steal: robo en jornada activa 2 → pick robado con `lockedThroughJornada == 2`; robar un pick con `lockedThroughJornada=2` y jornada 2 pendiente → 409 con el mensaje exacto.
  - RespondToTrade (aceptar): ambos picks con `lockedThroughJornada == jornada activa`.
  - ProposeTrade: proponer con un pick bloqueado → 409. Inyecta `ScheduleRepository`; sin calendario → se trata como no bloqueado (el draft completo siempre genera calendario, pero no romper).
  - Buy / Swap: el que entra lleva `lockedThroughJornada == jornada activa`.
  - Release / Swap: liberar o entregar un pick bloqueado → 409.
  - Sustituir en estos tests las aserciones sobre `lockedUntil` / `TRANSFER_LOCK`.
- [ ] **Step 2:** `./mvnw -f src/pom.xml -pl application -am test` → FAIL.
- [ ] **Step 3:** Pasar `market.schedule()` y `teamTransferService.currentJornada(market.schedule())` a las llamadas. `ProposeTradeCommandHandler` añade `ScheduleRepository` al constructor y lee el calendario con `findByLeagueId`.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** Commit (Task 1 + 2) `feat(equipos): bloqueo hasta que termine la jornada`.

### Task 3: API (`DraftPickResponse`)

**Files:**
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/response/DraftPickResponse.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/draft/GetDraftStatusCommandHandler.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/draft/DraftPickCommandHandler.java:117` (constructor de 7 args; sin bloqueo)
- Test: `GetDraftStatusCommandHandlerTest` (crear si no existe, siguiendo el estilo de los otros handler tests)

**Interfaces:**
- Consumes: `TeamTransferService.isLocked(pick, schedule)` de Task 1.
- Produces: `DraftPickResponse.lockedThroughJornada: Integer`, `DraftPickResponse.locked: boolean`. Se quita `lockedUntil` de la respuesta.

- [ ] **Step 1: Tests que fallan:** draft COMPLETED, pick con `lockedThroughJornada=2`, jornada 2 pendiente → `locked == true`, `lockedThroughJornada == 2`; jornada 2 completada → `locked == false`; draft IN_PROGRESS sin calendario → `locked == false` en todos.
- [ ] **Step 2:** Ejecutar → FAIL.
- [ ] **Step 3:** El handler inyecta `ScheduleRepository` y `TeamTransferService`; carga el calendario solo si el draft está COMPLETED. `mapPicks` recibe el `schedule` (nullable).
- [ ] **Step 4:** `./mvnw -f src/pom.xml verify` → BUILD SUCCESS (suite completa + ITs).
- [ ] **Step 5:** Commit `feat(api): locked y lockedThroughJornada en los picks`.

### Task 4: Migración `JornadaLockMigration`

**Files:**
- Create: `src/infrastructure/src/main/java/com/villu/pokefantasy/migration/JornadaLockMigration.java`
- Test: integración en `src/boot/src/test/java/com/villu/pokefantasy/it/` (seguir el patrón de los IT existentes con Mongo) o unit test con `MongoTemplate` real del IT.

**Interfaces:**
- Consumes: `DraftEntity`, `ScheduleEntity`, `ActivityEventEntity` (`type`, `roundNumber`, `createdAt`, `leagueId`) vía `MongoTemplate`, como `ClearStaleTiersMigration` (`@Component`, `@PostConstruct`, idempotente, log si modifica).

Regla por cada draft con algún pick con `lockedUntil != null`:
- Pick con `lockedUntil <= now` → `lockedUntil = null`, `lockedThroughJornada = null`.
- Pick con `lockedUntil > now`: `fichadoEn = lockedUntil - 7 días`; `A` = jornada activa del calendario de la liga.
  - Si `A > 1` y existe `MATCH_RESULT` de la liga con `roundNumber = A-1`: `cierreAnterior = max(createdAt)`. `fichadoEn < cierreAnterior` → libre (null); si no → `lockedThroughJornada = A`.
  - Si `A == 1`, no hay eventos de `A-1`, o no hay jornada activa → `lockedThroughJornada = A` (o null si temporada terminada).
  - En todos los casos `lockedUntil = null`. Guardar el draft incrementando `version`.

- [ ] **Step 1: Tests que fallan:** (a) jornada activa 3, último `MATCH_RESULT` de J2 hace 1 h, pick con `lockedUntil = now + 1d` (fichado hace 6 días) → `lockedThroughJornada == null`; (b) pick con `lockedUntil = now + 7d - 10 min` (fichado tras cerrar J2) → `lockedThroughJornada == 3`; (c) sin eventos de J2 → `3`; (d) `lockedUntil` en el pasado → ambos null; (e) segunda ejecución no modifica nada.
- [ ] **Step 2:** Ejecutar → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `./mvnw -f src/pom.xml verify` → BUILD SUCCESS.
- [ ] **Step 5:** Commit `chore(migracion): lockedUntil a lockedThroughJornada`. Push y PR a `develop` (integración de GitHub).

### Task 5: Front

**Files:**
- Modify: `src/utils/teams.ts`, `src/utils/teams.test.ts`, `src/components/teams/LockChip.tsx`, `src/components/teams/PokemonCard.tsx`, `src/components/teams/SwapModal.tsx`, `src/components/teams/OwnTeamPanel.tsx`, `src/components/teams/RivalTeamsList.tsx`, `src/api/pokemons.ts`, `src/api/schema.d.ts` (regenerado), tests de `PokemonCard`, `SwapModal`, `benchSwap`.

**Interfaces:**
- Consumes: `DraftPick.locked?: boolean`, `DraftPick.lockedThroughJornada?: number | null` (Task 3).
- Produces: `isPickLocked(pick: Pick<DraftPick, 'locked' | 'lockedUntil'>): boolean` — `pick.locked` si viene; si no, regla antigua de `lockedUntil`. `LockChip({ jornada }: { jornada: number | null | undefined })`. Se eliminan `lockRemaining` y `lockedUntilLabel`; `useNow` se quita de los componentes que solo lo usaban para el chip (se mantiene en `MarketStatus`).

- [ ] **Step 1:** Con el back de Task 3 en local, `npm run api:spec` y `npm run api:types`; añadir los campos a `api/pokemons.ts`.
- [ ] **Step 2: Tests que fallan:**
  - `teams.test.ts`: `isPickLocked({ locked: true })` → true; `{ locked: false, lockedUntil: futuro }` → false; `{ lockedUntil: futuro }` (back viejo) → true.
  - `PokemonCard.test.tsx`: pick `{ locked: true, lockedThroughJornada: 3 }` muestra `🔒 J3` con `title` `Bloqueado hasta que termine la jornada 3`; con `locked: false` no hay chip.
  - `SwapModal.test.tsx` / `benchSwap.test.ts`: un pick con `locked: true` no se puede entregar.
- [ ] **Step 3:** `rtk proxy npx vitest run src/utils src/components/teams` → FAIL.
- [ ] **Step 4:** Implementar. Comprobar si `TeamsPage` vuelve a pedir el draft al llegar el SSE de resultado; si no lo hace, el desbloqueo se ve al recargar (aceptado, no se añade en este plan).
- [ ] **Step 5:** `rtk proxy npx vitest run` → PASS; `npx tsc -p tsconfig.app.json --noEmit` → sin errores.
- [ ] **Step 6:** Commit `feat(equipos): chip de bloqueo por jornada`. Push y PR a `main`.

### Task 6: Vault

**Files (en `C:\PokeFantasy\vault`, `git pull` antes, commit directo a `main`):**
- Create: `70 Decisiones/ADR-011 Bloqueo hasta fin de jornada.md` (desde `Plantillas/Plantilla Decisión.md`): contexto (7 días desalineados con las ventanas), decisión, migración, consecuencias (vuelve `ScheduleRepository` a `ProposeTradeCommandHandler`; deshacer resultado rebloquea).
- Modify: `ADR-004 Bloqueo por timestamp.md` → `estado: reemplazada`, enlace a ADR-011.
- Modify: `50 Features/Robos.md`, `Banca.md`, `Trades.md`, `20 Arquitectura/Modelo de datos.md`, `API REST.md` (campos `locked`, `lockedThroughJornada`), PRs en `prs_back`/`prs_front`.
- Modify: sección de arquitectura del `CLAUDE.md` del back si alguna respuesta cambia.

- [ ] **Step 1:** Editar notas.
- [ ] **Step 2:** `git add -A`, `git commit -F <archivo>`, `git push`.
