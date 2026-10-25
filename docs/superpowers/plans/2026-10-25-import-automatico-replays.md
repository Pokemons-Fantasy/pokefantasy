# Import automático de replays y aviso de jornada completa: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un job registra solo el resultado de los partidos pendientes cuando sus replays de Showdown ya están subidos, y al completarse una jornada se avisa por push a toda la liga.

**Architecture:** Solo back. `ReplayAutoImportJob` (api-rest) orquesta como `WindowReminderJob`: lista candidatos (consulta), por cada uno pide a la facade la serie a importar (descargas fuera de transacción) y la registra con `ImportSeriesCommand` en modo sistema. La regla "serie completa" sale del handler a `ReplayCandidates` para compartirla. El aviso va en un servicio nuevo `JornadaCompletionNotifier`, llamado tras registrar un resultado.

**Tech Stack:** Spring Boot, `@Scheduled`, MongoDB, JUnit 5 + Mockito, Testcontainers.

**Spec:** `C:\PokeFantasy\vault\50 Features\Import automático de replays.md`. Código de partida: fase 3 de `Combates en Showdown` (`ReplayCandidates`, `ReplayImportService`, `ImportSeriesCommandHandler`, `ScheduleFacade.replayCandidates`).

## Global Constraints

- Rama `feature/import-automatico` desde `develop`, PR a `develop`. Revisar PRs abiertos antes. `export JAVA_HOME=/e/jdk-23.0.1`.
- Candidatos: partido `PENDING` de la **jornada activa** (`JornadaWindowService.getActiveJornada`), los dos jugadores con `showdownName` no nulo, y mercado cerrado: jornada sin `startDate`, o `JornadaWindowService.isLineupPeriodOpen(schedule, jornada, settings)`.
- Bo3: de los replays **seleccionables**, agrupar por `seriesId` no nulo; serie completa = partidas 1..n seguidas que forman 2-0 o 2-1. **Exactamente una** completa → importar; ninguna o ≥2 → nada.
- Bo1: el replay seleccionable **sin serie o con `gameNumber == 1`** de menor `uploadtime` con `uploadtime ≥ marketClosedEpoch` (sin fechas, cualquiera).
- Modo sistema: `ImportSeriesCommand.username == null` → salta solo la comprobación de jugador/admin; el resto de validaciones igual.
- Job: `@Scheduled(initialDelayString = "${schedule.replay-auto-import-ms:120000}", fixedDelayString = "${schedule.replay-auto-import-ms:1200000}")`. En `IntegrationTest` se desactiva con `schedule.replay-auto-import-ms=3600000`. Un fallo en un partido se registra (`log.warn`, sin ids de replay privados: `ReplayCandidates.redact`) y sigue con el siguiente.
- Aviso de jornada completa: al quedar **todos** los partidos de la jornada `COMPLETED` tras registrar un resultado (registro del admin, import manual y automático; **no** corregir ni deshacer), si `Jornada.completionNotified` no es `true`:
  - título `"Jornada <N> completa"`;
  - cuerpo `"Ya están todos los resultados. Se abre el mercado de la jornada <N+1>."` o, si es la última, `"Ya están todos los resultados: fin de la temporada."`;
  - `PushMessage.teams(leagueId, título, cuerpo)` a cada miembro con tokens; marcar `completionNotified = true`.
- Sin aviso por resultado individual.

## Review Focus

1. **Serie a medio subir** (solo G1 y G2 de un 1-1): no es completa → no se importa; cuando suben G3 la siguiente pasada la importa (test de `autoPick`, Task 2).
2. **Mismo partido importado a mano mientras corre el job:** el job relee en el handler, ve `COMPLETED` → 409, se registra en el log y sigue (test del job, Task 4).
3. **Showdown caído en una pasada:** `ShowdownUnavailableException` en un partido no para los demás (test del job).
4. **Deshacer y volver a registrar en una jornada ya avisada:** no repite el push (test del notifier, Task 3).
5. **Jornada con un partido de un miembro sin vincular:** ese partido no es candidato, los demás sí (test de la consulta, Task 1).

---

## Backend (`C:\PokeFantasy\pokefantasy`, rutas relativas a `src/`)

### Task 1: Candidatos del job y contexto en modo sistema

**Files:** `application/.../commands/schedule/ListAutoImportTargetsCommand.java` + `Handler`, `application/.../commands/schedule/replay/AutoImportTarget.java`, `ReplayImportService.java`, `replay/ReplayImportContext.java`, `PrepareReplayImportCommand.java` (+ handler), tests.

**Interfaces:**
- `record AutoImportTarget(String leagueId, String matchId)`.
- `record ListAutoImportTargetsCommand() implements Command` → `List<AutoImportTarget>`: recorre `scheduleRepository.findAll()`, liga por `leagueRepository.findById`, usuarios por `userRepository.findByUsernames` (ya trae `showdownName`).
- `ReplayImportContext` gana `Long marketClosedEpoch` (último componente; `JornadaWindowService.lineupOpensAt(jornada, settings)` en epoch de `Europe/Madrid`; `null` sin fecha). Actualizar las 3 construcciones de tests.
- `ReplayImportService.contextFor(league, schedule, matchId, username)`: `username == null` = sistema (sin comprobación de quién). `PrepareReplayImportCommand(leagueId, matchId, null)` lo usa el job; el handler no exige miembro si `username == null` (lee la liga con `leagueRepository.findById`).
- [ ] Tests: la consulta devuelve solo los partidos pendientes de la jornada activa con los dos vinculados y mercado cerrado (Review Focus 5; jornada sin fechas incluida; jornada futura excluida; liga sin calendario ignorada); `contextFor` con `null` no lanza `Forbidden` y sí `IllegalState` si el partido está completado; `marketClosedEpoch` correcto. FAIL → implementar → PASS. Commit `feat(auto-import): partidos candidatos y contexto del sistema`.

### Task 2: Regla de serie compartida y elección automática

**Files:** `replay/ReplayCandidates.java`, `ImportSeriesCommandHandler.java`, tests (`ReplayCandidatesTest`, `ImportSeriesCommandHandlerTest`).

**Interfaces:**
- `record SeriesResult(String winner, String loser, MatchScore score)` en `ReplayCandidates`.
- `static SeriesResult seriesResult(ReplayImportContext ctx, List<Evaluated> games)`: lo que hoy hacen `requireOneCompleteSeries` + `seriesScore` del handler (mismos mensajes); lanza `IllegalArgumentException`. El handler pasa a usarlo (sus tests existentes deben seguir en verde sin cambios).
- `static Optional<List<ShowdownReplay>> autoPick(ReplayImportContext ctx, List<ShowdownReplay> replays)`: reglas Bo3/Bo1 de Global Constraints.
- [ ] Tests de `autoPick`: una serie 2-1 completa entre partidas sueltas → esa serie en orden; serie a medio subir (1-1) → vacío (Review Focus 1); dos series completas → vacío; partida de otro formato dentro de una serie → la serie no es completa; Bo1: la primera tras el cierre (una anterior al cierre ignorada), sin fechas la primera; sin replays → vacío. FAIL → implementar → PASS; `ImportSeriesCommandHandlerTest` sigue verde. Commit `feat(auto-import): elegir la serie a registrar`.

### Task 3: Aviso de jornada completa

**Files:** `domain/.../entity/ScheduleEntity.java` (`Jornada.completionNotified`, `Boolean`), `application/.../commands/schedule/JornadaCompletionNotifier.java` (+ test), `RecordMatchResultCommandHandler.java`, `ImportSeriesCommandHandler.java` (+ sus tests).

**Interfaces:**
- `@Service JornadaCompletionNotifier(UserRepository, PushNotificationPort)` con `void notifyIfCompleted(LeagueEntity league, ScheduleEntity schedule, int roundNumber)`: textos y regla de Global Constraints; marca la jornada (la guarda quien llama, que ya hace `scheduleRepository.save`).
- Los dos handlers lo llaman tras `award` y antes de guardar el calendario. `CorrectMatchResultCommandHandler` **no**.
- [ ] Tests del notifier: último partido → push a cada miembro con tokens, texto con "jornada 4"; última jornada → "fin de la temporada"; quedan partidos → nada; ya avisada → nada (Review Focus 4); miembro sin usuario o sin tokens → se salta. Handlers: `verify(notifier).notifyIfCompleted(...)` en registro e import. FAIL → implementar → PASS. Commit `feat(schedule): avisar al completarse la jornada`.

### Task 4: Job y facade

**Files:** `ScheduleFacade.java` (+ test), `api-rest/.../ReplayAutoImportJob.java` (+ test), `boot/src/test/.../it/IntegrationTest.java` (propiedad), `boot/src/test/.../it/ReplayAutoImportIntegrationTest.java`.

**Interfaces:**
- `ScheduleFacade.autoImportTargets(): List<AutoImportTarget>` (un `send`).
- `ScheduleFacade.pickAutoImport(AutoImportTarget): Optional<List<ShowdownReplay>>`: `PrepareReplayImportCommand(…, null)`, búsqueda y descarga como `replayCandidates` (semana, máx. 15), `ReplayCandidates.autoPick`.
- `ScheduleFacade.importSeriesAsSystem(AutoImportTarget, List<ShowdownReplay>)`: `ImportSeriesCommand(leagueId, matchId, null, replays)`.
- `ReplayAutoImportJob.importReplays()`: lista; por cada uno `pick` → si hay, `importSeriesAsSystem`; `try/catch` por partido con `log.warn("Auto-import falló en {} / {}: {}", leagueId, matchId, e.getMessage())` y `log.info` al registrar.
- [ ] Tests: facade (pick con serie completa, sin replays, Showdown caído propaga), job (dos candidatos, el primero lanza `IllegalStateException` y el segundo se importa — Review Focus 2 y 3; lista que falla → no revienta). Integración: liga con 2 vinculados, mercado cerrado (jornada sin fechas), `ShowdownApiAdapter` mockeado con una serie 2-0 → `job.importReplays()` → partido `COMPLETED` 2-0 con `replayIds`, y al ser el último de la jornada `completionNotified == true`. FAIL → implementar → PASS.
- [ ] `./mvnw -B -ntp -f src/pom.xml clean verify` con Docker → BUILD SUCCESS. Commit, push, PR a `develop` (`feat: import automático de replays y aviso de jornada completa`, línea `🤖 Generated with [Claude Code](https://claude.com/claude-code)`).

### Task 5: Vault

- [ ] Nota `Import automático de replays` (`estado: en-curso`, PR, enlace a este plan), `20 Arquitectura/Flujos de datos.md` (job), `Notificaciones.md` (aviso de jornada), `Modelo de datos.md` (`Jornada.completionNotified`), y el `CLAUDE.md` del back: tabla de dueños con `JornadaCompletionNotifier` (push de jornada completa). Commit y push.
