# Combates en Showdown, fase 3 (import de la serie): plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cualquiera de los dos jugadores (o el admin) registra el resultado de un partido marcando los replays de Showdown de la serie; el back los vuelve a descargar, valida y registra ganador, marcador y monedas como hoy.

**Architecture:** `ShowdownPort` gana `searchReplays` y `getReplay`; un parser puro (`ReplayLog`) saca jugadores, ganador, serie, número de partida y Team Preview del log. La `ScheduleFacade` descarga (fuera de la transacción, ADR-007) y pasa los replays ya parseados a dos comandos: uno de consulta (candidatos) y otro que registra con `MatchResultService.award`. Front: un modal que lista los candidatos agrupados por serie y un enlace a los replays en el partido completado.

**Tech Stack:** igual que las fases 1 y 2.

**Spec:** `C:\PokeFantasy\vault\50 Features\Combates en Showdown.md` (Reglas de negocio y Fase 3). Planes anteriores en `docs/superpowers/plans/2026-10-2*-showdown-*.md`.

## Hallazgos de la investigación (2026-10-25, replays públicos reales)

- `https://replay.pokemonshowdown.com/search.json?user=<id>&user2=<id>` → hasta 51 replays públicos, más recientes primero: `{id, format, players[2], uploadtime (epoch s), private, rating}`. `format` a veces es el id y a veces el nombre: no se usa.
- `https://replay.pokemonshowdown.com/<id>.json` → `{id, formatid, format, players, log, uploadtime, private}`. `formatid` es el formato **base** (`gen9doublescustomgame`); las reglas custom van en líneas `|rule|`.
- **Una partida = un replay.** En una serie con `Best Of`, el log trae `|uhtml|bestof|<h2><strong>Game N</strong> of <a href="/game-bestof3-<formato>-<serieId>">a best-of-3</a></h2>`: número de partida y id de la serie.
- Team Preview: `|poke|pN|<Especie>, L50, M|` (los que se llevan) y `|teamsize|pN|<N>` (los que se eligen). Ganador: `|win|<nombre>`; empate: `|tie`.
- El orden p1/p2 puede cambiar entre partidas de la misma serie, y los ids pueden llevar prefijo de servidor (`dawn-`, `china-`): se compara por `toID` del nombre, nunca por posición ni prefijo.

## Global Constraints

- Ramas: back `feature/showdown-import` desde `develop` (PR a `develop`); front `feature/showdown-import` desde `main` (PR a `main`). Revisar PRs abiertos antes.
- JDK `export JAVA_HOME=/e/jdk-23.0.1`; vitest con `rtk proxy npx vitest ...`.
- Quién: los dos jugadores del partido o un admin de la liga (`LeagueRole.ADMIN`); el resto, `ForbiddenOperationException` "Solo los jugadores del partido o un admin pueden importar el resultado."
- Requisitos: partido `PENDING`; los dos jugadores con cuenta de Showdown vinculada ("<jugador> no ha vinculado su cuenta de Showdown.").
- Ventana de candidatos: replays con `uploadtime` ≥ lunes 00:00 (`Europe/Madrid`) de la semana ISO de `jornada.startDate`; sin fecha, sin filtro. Como mucho los 15 más recientes se descargan enteros.
- Un replay es **seleccionable** si: tiene exactamente los dos `showdownId` del partido, `formatid` (sin lo que vaya tras `@@@`) igual al formato base de la liga (`gen9doublescustomgame` / `gen9customgame`), es de la ventana, tiene ganador (no `|tie`) y no está en `replayIds` de otro partido de la liga. Si no, `selectable=false` con uno de estos motivos literales: "Otro formato", "Empate", "Ya usado en otro partido", "Fuera de la semana de la jornada", "No son los jugadores del partido".
- Serie válida al importar: `bestOf=1` → exactamente 1 replay, sin marcador. `bestOf=3` → 2 replays con el mismo ganador (2-0) o 3 con 2-1 (el ganador gana 2, el otro 1). Si no: "Para una serie al mejor de 3 marca 2 partidas del mismo ganador o 3 con un 2-1." / "Marca una partida."
- Replay privado: `?url=` acepta `https://replay.pokemonshowdown.com/<id>` (con o sin `.json`), id `[a-z0-9-]+` (los privados terminan en `-<contraseña>pw`); cualquier otra URL → 400 "Pega el enlace de un replay de Showdown.".
- Avisos (no bloquean), por replay: alineación declarada vs `|poke|` del jugador (comparación `toID`): "<jugador> llevó <especie>, que no está en su alineación." Se guardan en `Match.importWarnings` (máx. 12, como `PokepasteCheck`).
- Guardado: `Match.replayIds` (ids en orden de partida), `Match.importWarnings`; `MatchResultService.revoke` los limpia; corregir los mantiene. Registro con `MatchResultService.award` (mismas monedas y eventos que hoy).
- Showdown caído → 502 `SHOWDOWN_UNAVAILABLE` (como en la fase 1).
- Reglas de cada repo: las de las fases anteriores (CQRS, efectos externos en la facade, JaCoCo 80 %, front sin decidir permisos, accesibilidad, glosario).

## Review Focus

1. **Dos importaciones a la vez del mismo partido:** el handler relee el calendario; la segunda ve `COMPLETED` → 409 "El resultado de este partido ya se ha registrado." (test del handler, Task 4).
2. **Replay de otro partido de la liga reutilizado:** bloqueado al listar y otra vez al importar, releyendo `replayIds` de todos los partidos (Task 4).
3. **Jugadores en orden distinto en cada partida de la serie** y nombres con mayúsculas/espacios: el ganador se traduce por `toID` (test del parser y del handler).
4. **Replays manipulados por el cliente:** el import nunca usa datos del front salvo los ids; el facade vuelve a descargar (test de la facade).
5. **Jornada sin fecha:** sin filtro de semana, el resto igual (test de candidatos).

---

## Backend (`C:\PokeFantasy\pokefantasy`, rutas relativas a `src/`)

### Task 1: Replays en el puerto de Showdown

**Files:** `application/.../ports/ShowdownPort.java`, `application/.../ports/ShowdownReplaySummary.java`, `application/.../ports/ShowdownReplay.java`, `infrastructure/.../adapters/ShowdownApiAdapter.java`, test `ShowdownApiAdapterTest`.

**Interfaces:**
- `record ShowdownReplaySummary(String id, List<String> players, long uploadtime)`.
- `record ShowdownReplay(String id, String formatid, List<String> players, String log, long uploadtime)`.
- `List<ShowdownReplaySummary> searchReplays(String userId1, String userId2)` (`search.json?user=&user2=`; lista vacía si 404).
- `Optional<ShowdownReplay> getReplay(String replayId)` (`/<id>.json`; vacío si 404).
- Errores como `findUser`: `ShowdownUnavailableException`.

- [ ] Tests con `MockRestServiceServer` (éxito, 404, 500 para cada método) → FAIL → implementar → PASS. Commit `feat(showdown): buscar y leer replays`.

### Task 2: Parser del log (`ReplayLog`)

**Files:** `application/.../commands/schedule/replay/ReplayLog.java` + test.

**Interfaces:**
- `record ReplayLog(String p1, String p2, String winner, boolean tie, String seriesId, Integer gameNumber, Map<String, List<String>> broughtBy)` con `static ReplayLog parse(String log)`.
  - `p1`/`p2`: nombre de `|player|pN|<nombre>|...` (la primera aparición con nombre).
  - `winner`: `|win|<nombre>`; `tie`: hay `|tie`.
  - `seriesId` y `gameNumber`: del `|uhtml|bestof|` (regex `Game (\d+)</strong> of <a href="/game-bestof\d+-([a-z0-9-]+)"`); `null` si no hay.
  - `broughtBy`: nombre del jugador → especies de sus `|poke|pN|<Especie>, ...|` (antes de la primera coma).
- `static String baseFormat(String formatid)` → lo anterior a `@@@`.

- [ ] Tests con los logs reales de los Hallazgos (Bo3 de `gen9vgc2026regfbo3` y custom game), un empate y un log sin `bestof` → FAIL → implementar → PASS (Review Focus 3). Commit `feat(import): leer el log de un replay`.

### Task 3: Candidatos (consulta)

**Files:** `application/.../commands/schedule/ListReplayCandidatesCommand.java` + `Handler`; `domain/.../response/ReplayCandidateResponse.java`; `ScheduleFacade.replayCandidates`; `ScheduleController` `GET /{leagueId}/schedule/matches/{matchId}/replays[?url=]`; tests.

**Interfaces:**
- La facade: comprueba quién y vinculación con un comando de consulta ligero (`PrepareReplayImportCommand` → `ReplayImportContext(String player1Id, String player2Id, String player1, String player2, String baseFormat, Long fromEpoch, Set<String> usedReplayIds, MatchLineup p1Lineup, MatchLineup p2Lineup, int bestOf)`), descarga (`searchReplays` + `getReplay` de los 15 más recientes, o solo el de `url`) y construye la respuesta con una función pura `ReplayCandidates.evaluate(ReplayImportContext, List<ShowdownReplay>) : List<ReplayCandidateResponse>`.
- `ReplayCandidateResponse { String id; String url; long uploadtime; String winnerUsername; String seriesId; Integer gameNumber; boolean selectable; String reason; List<String> warnings; }` (`winnerUsername` = usuario de la liga, no el nombre de Showdown).
- Orden: por serie (más reciente primero) y dentro por `gameNumber`; los sin serie al final por `uploadtime`.

- [ ] Tests de `ReplayCandidates.evaluate` (cada motivo de no seleccionable, avisos de alineación, jornada sin fecha — Review Focus 5) y del comando de contexto (no jugador ni admin → 403; sin vincular → 400; partido completado → 409; `usedReplayIds` de otros partidos). Facade: con `url` solo descarga ese replay; URL inválida → 400. Controller → 200. FAIL → implementar → PASS. Commit `feat(import): candidatos de replays del partido`.

### Task 4: Importar la serie

**Files:** `ImportSeriesCommand` + `Handler`; `ScheduleEntity.Match` (`List<String> replayIds`, `List<String> importWarnings`); `MatchResultService.revoke` (limpiarlos); `ScheduleFacade.importSeries`; `ScheduleController` `POST .../import {replayIds}` (request en `domain/request/schedule`); `ScheduleResponse.MatchResponse` (`replayIds`, `importWarnings`); `GetScheduleCommandHandler`; tests.

**Interfaces:**
- La facade descarga cada id con `getReplay` (ninguno → 400 "No se encontró el replay <id>.") y envía `ImportSeriesCommand(String leagueId, String matchId, String username, List<ParsedReplay> replays)` con `ParsedReplay(String id, String formatid, long uploadtime, ReplayLog log)`.
- El handler relee calendario, liga y usuarios; reconstruye el contexto; valida cada replay con `ReplayCandidates` (todos seleccionables), sin ids repetidos, y la serie según `bestOf`; registra con `award(…, MatchScore)` (`null` en Bo1) y guarda `replayIds` y los avisos.

- [ ] Tests del handler: Bo3 2-0 y 2-1 (ganador por `toID` con jugadores en orden distinto), Bo1, 2 partidas con ganadores distintos → 400, 4 replays → 400, replay no seleccionable → 400 con su motivo, partido ya completado → 409 (Review Focus 1), replay ya usado en otro partido releído en el handler (Review Focus 2), ids repetidos → 400; `revoke` limpia `replayIds`/`importWarnings`; facade vuelve a descargar (Review Focus 4); mapeo del calendario expone `replayIds` e `importWarnings` a todos. FAIL → implementar → PASS. Commit `feat(import): registrar el resultado desde los replays`.

### Task 5: Integración, verify y PR

- [ ] `ReplayImportIntegrationTest` (Docker): liga con 2 jugadores vinculados, `ShowdownApiAdapter` mockeado con 3 replays de una serie 2-1 → `GET .../replays` los lista seleccionables → `POST .../import` → partido `COMPLETED`, marcador 2-1, monedas dadas, `replayIds` en el calendario; reimportar → 409.
- [ ] `./mvnw -B -ntp -f src/pom.xml clean verify` → BUILD SUCCESS. Commit, push, PR a `develop` (`feat: importar el resultado desde los replays de Showdown`).

---

## Frontend (`C:\PokeFantasy\pokefantasy-web`)

### Task 6: Tipos y lógica pura

**Files:** `src/api/leagues.ts` (+ `contract.ts`, `openapi.json`, `schema.d.ts`), `src/utils/replays.ts` + test.

**Interfaces:**
- `ReplayCandidate` (campos de `ReplayCandidateResponse`); `MatchDto.replayIds?`, `MatchDto.importWarnings?`; `getReplayCandidates(leagueId, matchId, url?)`, `importSeries(leagueId, matchId, replayIds)`.
- `utils/replays.ts`: `groupBySeries(candidates): { seriesId: string | null; games: ReplayCandidate[] }[]`; `seriesSummary(selected, bestOf, players): { winner: string; score: string } | { error: string }` con las mismas reglas y textos que el back (solo para el resumen en vivo; el back valida); `replayUrl(id)` → `https://replay.pokemonshowdown.com/<id>`.

- [ ] Regenerar tipos con el back de la rama; tests de `groupBySeries` y `seriesSummary` (2-0, 2-1, Bo1, inválidas) → FAIL → implementar → PASS. Commit `feat(api): tipos e import de replays`.

### Task 7: Modal "Importar de Showdown" y calendario

**Files:** `src/components/schedule/ImportSeriesModal.tsx` + test; `MatchRow.tsx` (botón "Importar de Showdown" con `mine || isAdmin` y partido pendiente; en completados, "Replays" con enlaces numerados "Partida 1/2/3" y los `importWarnings`); `SchedulePage.tsx` (+ test); CSS.

- [ ] Tests del modal: carga candidatos (skeleton mientras); agrupa por serie con "Marcar la serie"; casillas deshabilitadas con su motivo (`aria-describedby`); resumen en vivo "<jugador> gana 2-1" o el error; "Registrar resultado" deshabilitado si el resumen es error; campo "¿No aparece? Pega el enlace del replay" que añade el candidato; al importar llama a `importSeries`, invalida `schedule`, `my-coins`, `standings`, `season-stats`, `activity`, toast "Resultado registrado"; error con `extractErrorMessage`; Showdown caído → mensaje del back. Calendario: botón solo para jugadores o admin en pendientes; enlaces a replays en completados (`target="_blank"`, `rel="noopener noreferrer"`). FAIL → implementar → PASS.
- [ ] Verificar en el navegador con el back simulado (`mock-api` del `launch.json`, ampliado con los endpoints nuevos) en escritorio y móvil.
- [ ] `lint`, `api:check`, `test`, `build` → verde. Commit, push, PR a `main`.

### Task 8: Vault

- [ ] Nota de la feature (fase 3, PRs, hallazgos de los replays Bo3 y cerrar la pregunta abierta), `API REST.md` (2 endpoints, campos del calendario), `Modelo de datos.md` (`replayIds`, `importWarnings`), `Flujos de datos.md` (import). Commit y push.
