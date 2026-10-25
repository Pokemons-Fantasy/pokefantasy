# Combates en Showdown, fase 2 (alineación del partido): plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada jugador declara a ciegas sus 6 Pokémon del partido; cuando los dos los han enviado se revelan y se bloquean, se avisa por push y el rival los ve en el calendario (con el enlace al paste en lista abierta).

**Architecture:** Back: las alineaciones viven en el `Match` del calendario (`player1Lineup` / `player2Lineup`), un comando `DeclareLineupCommand` valida periodo, propiedad y revelación, y el mapeo de `GetScheduleCommandHandler` decide qué ve cada uno. El paste de pokepast.es se descarga en la `ScheduleFacade` (fuera de la transacción, ADR-007) con `ShowdownPort.getPokepaste` y su comparación es un aviso, nunca bloquea. Front: tipos manuales + `contract.ts`, lógica pura en `utils/lineup.ts`, un modal para declarar y la visualización en `MatchRow`.

**Tech Stack:** igual que la fase 1 (Spring Boot + MongoDB, RestTemplate, JUnit 5 + Mockito + MockRestServiceServer, Testcontainers; React 19 + TS + React Query + Vitest).

**Spec:** `C:\PokeFantasy\vault\50 Features\Combates en Showdown.md` (Reglas de negocio y Fase 2). Plan anterior: `docs/superpowers/plans/2026-10-24-showdown-fase1-vinculacion-y-reto.md`.

## Global Constraints

- Ramas: back `feature/showdown-alineacion` desde `develop` (PR a `develop`); front `feature/showdown-alineacion` desde `main` (PR a `main`). Revisar PRs abiertos antes de crear ramas.
- JDK: `export JAVA_HOME=/e/jdk-23.0.1` antes de `./mvnw` (el del sistema es 17). Vitest con `rtk proxy npx vitest ...`.
- Compatibilidad: campos nuevos opcionales; el front tolera un back sin ellos (sin alineaciones = no se muestra nada nuevo).
- **Desviación del diseño (ruling):** se guarda `player1Lineup` / `player2Lineup` en vez de un mapa username → alineación, para que el anonimizado del borrado de cuenta (`ScheduleRepository.anonymizeUser`, que renombra `player1`/`player2`/`winnerUsername`) no deje claves con el nombre antiguo.
- Periodo de declaración: partido `PENDING` de la **jornada activa** (`JornadaWindowService.getActiveJornada`), jornada **con `startDate`**, y `now() >= max(getStealDeadline, getSwapDeadline)` de esa jornada (hora de `Europe/Madrid`).
- Tamaño: exactamente `min(6, draft.teamSize(username))` Pokémon distintos, todos de los picks del último draft del jugador (comparación sin mayúsculas).
- Revelada = las dos alineaciones existen. Mientras no esté revelada, el jugador puede reenviar la suya; revelada, 409.
- `teamSheet = OPEN`: `pasteUrl` obligatorio y `https://`. `CLOSED`: `pasteUrl` se ignora (se guarda `null`).
- Pokepaste: URL `^https://pokepast\.es/([a-z0-9]+)/?$` → `GET https://pokepast.es/{id}/json` → `{paste, title, author, notes}` (verificado en `pkmn/smogon` y el cliente de Showdown). Otras URLs https se guardan sin validar.
- Push al revelar, a los dos jugadores, con `PushMessage.lineups(leagueId, roundNumber)`: título "Alineaciones reveladas", texto "Ya puedes ver la alineación de <rival> en la jornada <N>.", `path` `/leagues/{id}/schedule`, `tag` `lineups-{matchId}`.
- Mensajes de error literales:
  - "No juegas este partido."
  - "Solo se puede declarar la alineación de un partido pendiente de la jornada actual."
  - "La alineación se declara con las fechas de la temporada configuradas."
  - "La alineación se puede declarar desde el cierre del mercado (<dd/MM HH:mm>)."
  - "Las alineaciones ya están reveladas."
  - "Elige <N> Pokémon distintos."
  - "'<nombre>' no es de tu equipo."
  - "Con lista abierta, añade el enlace a tu equipo (https)."
- Avisos de paste (se guardan en `pasteWarnings`, no bloquean):
  - "En el paste falta <nombre>."
  - "El paste tiene <especie>, que no está en la alineación."
  - "No se pudo leer el paste."
- Reglas del back y del front: las de la fase 1 (CQRS, efectos externos en la facade, JaCoCo 80 %, `verify` antes de push; front `lint`, `api:check`, `test`, `build`, glosario sin "stats", accesibilidad).

## Review Focus

1. **Reenvío del segundo jugador en carrera con el primero:** los dos envían a la vez; el mediator reintenta ante conflicto de `@Version` y el handler relee: uno queda revelado y el otro no puede pisarlo (test del handler en la Task 4: reenviar con la alineación ya revelada → 409).
2. **Pokémon que no es suyo:** se valida contra los picks del último draft releídos en el handler, no contra lo que mande el front (test en la Task 4). Si un admin cambia la fecha de inicio y el mercado se reabre tras declarar, la alineación ya guardada no se revalida: se acepta.
3. **Equipo de menos de 6:** se declaran todos los que tenga (test en la Task 4 con 4 picks).
4. **No participante viendo el calendario:** antes de revelar no ve ninguna alineación ni enlace; después ve las dos (test del mapeo en la Task 5).
5. **Paste con apodos, género y objeto** (`Kyu (Urshifu-Rapid-Strike) (M) @ Choice Band`): se extrae la especie correcta (test del parser en la Task 2).

---

## Backend (`C:\PokeFantasy\pokefantasy`, rutas relativas a `src/`)

### Task 1: Pokepaste en el puerto de Showdown

**Files:**
- Modify: `application/.../ports/ShowdownPort.java`, `infrastructure/.../adapters/ShowdownApiAdapter.java`
- Test: `infrastructure/src/test/.../adapters/ShowdownApiAdapterTest.java`

**Interfaces:**
- Produces: `Optional<String> getPokepaste(String pasteId)` en `ShowdownPort`: el texto `paste` del JSON; vacío si 404 o sin `paste`; `ShowdownUnavailableException` ante cualquier otro error.

- [ ] **Step 1: Tests** `getPokepaste_returnsPasteText` (`requestTo("https://pokepast.es/abc123/json")` → `{"paste":"Pikachu @ Light Ball\r\n","title":"t","author":"a","notes":""}` → `Optional.of("Pikachu @ Light Ball\r\n")`), `getPokepaste_notFound_empty`, `getPokepaste_serverError_throwsUnavailable`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest=ShowdownApiAdapterTest -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3:** Implementar con el mismo `RestTemplate` y manejo de errores que `findUser` (record privado `PasteJson(String paste)`).
- [ ] **Step 4:** Mismo comando → PASS. Commit `feat(showdown): leer pastes de pokepast.es`.

### Task 2: Parser y comparación del paste

**Files:**
- Create: `application/.../commands/schedule/lineup/PokepasteCheck.java`
- Test: `application/src/test/.../commands/schedule/lineup/PokepasteCheckTest.java`

**Interfaces:**
- Consumes: `ShowdownIds.toId` (fase 1).
- Produces:
  - `static Optional<String> PokepasteCheck.pasteId(String url)` (regex de Global Constraints).
  - `static List<String> PokepasteCheck.species(String pasteText)`: especie de la primera línea de cada bloque (bloques separados por línea en blanco; `\r\n` o `\n`). Regla: quitar lo que va desde ` @ `; quitar ` (M)` / ` (F)` finales; si queda `Apodo (Especie)`, la especie es lo de dentro del último paréntesis; si no, la línea entera, recortada.
  - `static List<String> PokepasteCheck.warnings(List<String> lineup, List<String> pasteSpecies)`: compara por `toId`; mensajes literales de Global Constraints, primero los que faltan (en el orden de la alineación) y luego los que sobran (en el orden del paste).

- [ ] **Step 1: Tests:**
  - `pasteId`: `"https://pokepast.es/abc123"` y `".../abc123/"` → `abc123`; `"http://pokepast.es/abc"`, `"https://example.com/x"` → vacío.
  - `species("Kyu (Urshifu-Rapid-Strike) (M) @ Choice Band\r\nAbility: Unseen Fist\r\n\r\nPikachu (F)\r\n\r\nGarchomp @ Life Orb\n- Earthquake")` → `["Urshifu-Rapid-Strike", "Pikachu", "Garchomp"]` (Review Focus 5).
  - `warnings(["urshifu-rapid-strike","pikachu","garchomp"], ["Urshifu-Rapid-Strike","Pikachu","Raichu"])` → `["En el paste falta garchomp.", "El paste tiene Raichu, que no está en la alineación."]`; iguales → `[]`.
- [ ] **Step 2:** `test -pl application -am -Dtest=PokepasteCheckTest` → FAIL.
- [ ] **Step 3:** Implementar (clase final con métodos estáticos, como `ShowdownIds`).
- [ ] **Step 4:** PASS. Commit `feat(lineup): comparar el paste con la alineación`.

### Task 3: Modelo de la alineación y push

**Files:**
- Modify: `domain/.../repository/entity/ScheduleEntity.java` (clase anidada `MatchLineup` y campos en `Match`), `domain/.../dto/PushMessage.java`
- Test: `domain` no tiene tests propios de entidades; `PushMessage.lineups` se prueba en la Task 4.

**Interfaces:**
- Produces:
  - `ScheduleEntity.MatchLineup { List<String> pokemonNames; String pasteUrl; List<String> pasteWarnings; Instant submittedAt; }` (`@Data @NoArgsConstructor @AllArgsConstructor`).
  - `Match.player1Lineup`, `Match.player2Lineup` (`MatchLineup`, `null` = no enviada) y `boolean lineupsRevealed()` → ambas no `null` (Spring Data mapea campos, no métodos, así que no se persiste).
  - `static PushMessage lineups(String leagueId, String matchId, String title, String body)` → `new PushMessage(title, body, "/leagues/" + leagueId + "/schedule", "lineups-" + matchId)`.

- [ ] **Step 1:** Añadir campos y factory con Javadoc en español. `./mvnw -B -ntp -f src/pom.xml clean package -DskipTests` → BUILD SUCCESS. Commit `feat(lineup): alineaciones en el partido`.

### Task 4: Declarar la alineación (comando, facade, endpoint)

**Files:**
- Create: `application/.../commands/schedule/lineup/DeclareLineupCommand.java`, `DeclareLineupCommandHandler.java`
- Create: `api-rest/.../request/schedule/DeclareLineupRequest.java` (`pokemonNames: List<String>`, `pasteUrl: String`) — ruta donde ya viva `RecordMatchResultRequest`.
- Modify: `application/.../commands/schedule/ScheduleFacade.java` (recibe `ShowdownPort`), `api-rest/.../ScheduleController.java`
- Test: `application/src/test/.../commands/schedule/lineup/DeclareLineupCommandHandlerTest.java`, `ScheduleFacadeTest` (o crearlo si no existe), `DraftScheduleUserControllersTest`

**Interfaces:**
- Consumes: Task 1-3; `JornadaWindowService` (`getActiveJornada`, `getStealDeadline`, `getSwapDeadline`, `now`), `MatchResultService.findMatch` y `findRoundNumber`, `DraftRepository.findLatestByLeagueId`, `LeagueMembershipGuard`, `UserRepository.findByUsername`, `PushNotificationPort`.
- Produces:
  - En `JornadaWindowService` (dueño de las ventanas, CLAUDE.md §2), con tests en `JornadaWindowServiceTest`: `Optional<LocalDateTime> lineupOpensAt(Jornada jornada, LeagueSettings settings)` (`max(steal, swap)`; vacío sin fecha) y `boolean isLineupPeriodOpen(ScheduleEntity schedule, Jornada jornada, LeagueSettings settings)` (jornada activa, con fecha y `now()` no anterior a `lineupOpensAt`). El handler y el mapeo de la Task 5 usan estos dos.
  - `record DeclareLineupCommand(String leagueId, String matchId, String username, List<String> pokemonNames, String pasteUrl, List<String> pasteWarnings) implements Command` → `Void`.
  - `ScheduleFacade.declareLineup(String leagueId, String matchId, List<String> pokemonNames, String pasteUrl, String requestingUsername)`: si `PokepasteCheck.pasteId(pasteUrl)` existe, `showdownPort.getPokepaste` → `warnings(...)`; vacío o `ShowdownUnavailableException` → `["No se pudo leer el paste."]`; envía el comando.
  - `PUT /v1/leagues/{leagueId}/schedule/matches/{matchId}/lineup` → 204.

- [ ] **Step 1: Tests del handler** (Mockito; `JornadaWindowService` real con `Clock` fijo si su constructor lo admite, si no mock de `now()`):
  - `declare_ok_savesLineupAndDoesNotReveal`: jornada activa con fecha, ahora después de los dos cierres, 6 picks propios → guarda `player1Lineup` con `submittedAt`, sin push.
  - `declare_secondPlayer_revealsAndPushesBoth`: rival ya tenía la suya → guarda y `pushNotificationPort.send` dos veces con `PushMessage.lineups(...)` (texto literal de Global Constraints).
  - `declare_resend_beforeReveal_overwrites`.
  - `declare_afterReveal_throwsConflict` (Review Focus 1, "Las alineaciones ya están reveladas.", `IllegalStateException`).
  - `declare_beforeMarketClose_throws` (mensaje con la fecha formateada `dd/MM HH:mm`).
  - `declare_jornadaWithoutDate_throws`, `declare_notActiveJornada_throws`, `declare_completedMatch_throws`, `declare_notPlayer_throws` (`ForbiddenOperationException` "No juegas este partido.").
  - `declare_wrongCount_throws` ("Elige 6 Pokémon distintos." con repetidos o 5), `declare_teamOfFour_requiresFour` (Review Focus 3), `declare_notOwnPokemon_throws` (Review Focus 2).
  - `declare_openSheetWithoutHttpsUrl_throws`; `declare_closedSheet_ignoresPasteUrl` (guarda `pasteUrl` `null`).
  - `commandType`.
- [ ] **Step 2: Tests de facade y controller:** facade con pokepast → pasa los avisos al comando; con URL no pokepast no llama al puerto; con `ShowdownUnavailableException` → aviso "No se pudo leer el paste.". Controller: `PUT .../lineup` con `{"pokemonNames":["a","b"],"pasteUrl":"https://x"}` → 204 y `verify(scheduleFacade).declareLineup("l1","m1",List.of("a","b"),"https://x",ME)`.
- [ ] **Step 3:** `test -pl application,api-rest -am -Dtest='DeclareLineupCommandHandlerTest,ScheduleFacadeTest,DraftScheduleUserControllersTest'` → FAIL.
- [ ] **Step 4:** Implementar. Orden de validación en el handler: participante → partido pendiente de la jornada activa → fecha → periodo → no revelada → tamaño y propiedad → lista abierta. Releer calendario y draft dentro del handler.
- [ ] **Step 5:** PASS. Commit `feat(lineup): declarar la alineación del partido`.

### Task 5: Visibilidad en el calendario

**Files:**
- Modify: `domain/.../response/ScheduleResponse.java`, `application/.../commands/schedule/GetScheduleCommandHandler.java`
- Test: test existente de `GetScheduleCommandHandler` (crear `GetScheduleCommandHandlerTest` si no existe)

**Interfaces:**
- Produces en `ScheduleResponse`:
  - `LineupResponse { List<String> pokemonNames; String pasteUrl; List<String> pasteWarnings; }`.
  - En `MatchResponse`: `boolean player1LineupSubmitted`, `boolean player2LineupSubmitted`, `LineupResponse player1Lineup`, `LineupResponse player2Lineup` (solo si revelada o si es la del que pide; si no, `null`), `boolean lineupOpen` (el que pide puede declarar ahora: mismas reglas que el handler, sin la de tamaño).
  - En `JornadaResponse`: `String lineupOpensAt` (de `JornadaWindowService.lineupOpensAt`, ISO local; `null` sin fecha).

- [ ] **Step 1: Tests:** jugador ve la suya antes de revelar y no la del rival; rival igual; no participante no ve ninguna antes y ve las dos después (Review Focus 4); `lineupOpen` true solo para participantes con periodo abierto (`isLineupPeriodOpen`) y no revelada; `lineupOpensAt` presente con fecha y `null` sin ella.
- [ ] **Step 2:** FAIL → implementar (pasar `requestingUsername` a `toResponse`) → PASS.
- [ ] **Step 3:** `test -pl application -am -Dtest='GetScheduleCommandHandlerTest,DeclareLineupCommandHandlerTest,JornadaWindowServiceTest'` → PASS. Commit `feat(lineup): alineaciones en el calendario según quién mira`.

### Task 6: Integración, verify y PR

**Files:**
- Create: `boot/src/test/.../it/LineupIntegrationTest.java`

- [ ] **Step 1:** Test de punta a punta: crear liga con 2 jugadores, draft completado con 6 picks cada uno, calendario con fecha pasada (cierres ya vencidos), los dos declaran → el calendario de cada uno muestra las dos alineaciones; un tercero ve las dos; reenviar → 409. Ver cómo arman ligas y drafts los IT existentes (`TransactionsIntegrationTest`, `QueriesIntegrationTest`) y reutilizar sus helpers; si no existen helpers para completar un draft, sembrar los documentos con `mongoTemplate` (ruling).
- [ ] **Step 2:** Con Docker, `./mvnw -B -ntp -f src/pom.xml clean verify` → BUILD SUCCESS.
- [ ] **Step 3:** Commit, push y PR a `develop` (`feat: alineación del partido a ciegas`), cuerpo con endpoint, campos y la línea `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

---

## Frontend (`C:\PokeFantasy\pokefantasy-web`)

### Task 7: Tipos

**Files:** `src/api/leagues.ts`, `src/api/contract.ts`, `openapi.json`, `src/api/schema.d.ts`

**Interfaces:**
- Produces: `interface Lineup { pokemonNames: string[]; pasteUrl?: string | null; pasteWarnings?: string[] }`; en `MatchDto`: `player1LineupSubmitted?`, `player2LineupSubmitted?`, `player1Lineup?: Lineup | null`, `player2Lineup?: Lineup | null`, `lineupOpen?: boolean`; en `JornadaDto`: `lineupOpensAt?: string | null`; `declareLineup(leagueId, matchId, pokemonNames: string[], pasteUrl?: string): Promise<void>`.
- [ ] Regenerar con el back de la rama (`API_DOCS_ENABLED=true SPRING_MAIN_LAZY_INITIALIZATION=true JWT_SECRET=<test> java -jar src/boot/target/boot-*.jar`, `npm run api:spec`, `npm run api:types`), añadir `Assert<FieldsMatch<Lineup, S['LineupResponse']>>` en `contract.ts`, `tsc` y `api:check` sin errores. Commit `feat(api): tipos de la alineación`.

### Task 8: Lógica pura (`utils/lineup.ts`)

**Files:** `src/utils/lineup.ts`, `src/utils/lineup.test.ts`

**Interfaces:**
- Produces:
  - `lineupSize(teamSize: number): number` → `Math.min(6, teamSize)`.
  - `myLineup(match, username)` / `rivalLineup(match, username)`: la `Lineup` del lado correspondiente o `null`.
  - `lineupState(match, username): 'none' | 'waiting-rival' | 'waiting-me' | 'revealed'` (`none`: ninguno envió; `waiting-rival`: yo sí, el rival no; `waiting-me`: el rival sí, yo no; `revealed`: los dos).
- [ ] Tests de cada función con los cuatro estados y con `username` no participante (`none`/`revealed` según los flags) → FAIL → implementar → PASS. Commit `feat(lineup): estado de la alineación`.

### Task 9: Modal "Declarar alineación"

**Files:** `src/components/schedule/LineupModal.tsx`, `LineupModal.test.tsx`

**Interfaces:**
- Consumes: `lineupSize`, `declareLineup`, `spriteUrl` (`utils/sprites`), `DraftPick` (para nombre e id del sprite).
- Produces: `LineupModal({ leagueId, matchId, myPicks: DraftPick[], current?: Lineup | null, teamSheet: TeamSheet, onClose })`.
- [ ] **Tests:** grid de mis Pokémon como casillas (`role="checkbox"` con el nombre como label y su sprite); con `N = lineupSize` elegidos no deja marcar más; "Enviar alineación" deshabilitado hasta tener `N`; con lista abierta pide "Enlace a tu equipo" (input `type="url"`) y no deja enviar sin `https://`; al enviar llama a `declareLineup` con los nombres en el orden elegido, invalida `['schedule', leagueId]`, toast "Alineación enviada"; error con `extractErrorMessage`; texto de ayuda "Tu rival no la verá hasta que los dos la hayáis enviado. Después no se puede cambiar."; `current` precarga la selección; foco y Escape como `PlayShowdownModal`.
- [ ] FAIL → implementar → PASS. Commit `feat(calendario): declarar la alineación`.

### Task 10: Alineaciones en el calendario, modal de Showdown y PR

**Files:** `src/components/schedule/MatchRow.tsx`, `src/components/schedule/MatchLineups.tsx` (+ test), `src/components/schedule/JornadaCard.tsx`, `src/pages/SchedulePage.tsx` (+ test), `src/components/schedule/PlayShowdownModal.tsx`

**Interfaces:**
- Consumes: Task 7-9; `draft-status` (ya en `SchedulePage`, ahora `enabled` también con el modal de alineación abierto) para nombre → id de sprite.
- [ ] **Tests:**
  - `MatchRow`: "Declarar alineación" (o "Cambiar alineación" si `waiting-rival`) solo con `match.lineupOpen`; texto de estado "Esperando a <rival>" / "<rival> ya la ha enviado" / nada.
  - `MatchLineups`: revelada → las dos alineaciones con sprites y nombre (`aria-label` "Alineación de <jugador>"), enlace "Ver equipo" al paste (`target="_blank"`, `rel="noopener noreferrer"`) si hay `pasteUrl`, y los `pasteWarnings` como lista de avisos; no revelada → solo la mía.
  - `SchedulePage`: el botón abre `LineupModal`; con jornada `lineupOpensAt` futuro y mi partido pendiente se muestra "Podrás declarar tu alineación desde <fecha>" (formato con `utils/dates`).
  - `PlayShowdownModal`: con mi alineación declarada, "Copiar mi equipo" copia solo esos Pokémon.
- [ ] FAIL → implementar → PASS. CSS mínimo en `index.css` con tokens existentes.
- [ ] `npm run lint`, `npm run api:check`, `npm test`, `npm run build` → todos en verde. Commit, push y PR a `main` (enlace al PR del back, línea `🤖 Generated with [Claude Code](https://claude.com/claude-code)`).

### Task 11: Vault

- [ ] Nota de la feature (fase 2 en la tabla de fases, PRs), `20 Arquitectura/API REST.md` (`PUT .../lineup`, campos del calendario), `Modelo de datos.md` (`MatchLineup`), `Flujos de datos.md` (declarar → revelar → push). Commit y push del vault.
