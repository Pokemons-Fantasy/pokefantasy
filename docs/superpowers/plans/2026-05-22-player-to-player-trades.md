# Player-to-player trades — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que un jugador proponga a otro un intercambio 1×1 de Pokémon con monedas opcionales; el otro acepta o rechaza, y el intercambio solo se ejecuta si acepta.

**Architecture:** Nueva colección Mongo `trades` con su `TradeEntity` y `TradeRepository`. Cuatro casos de uso CQRS (propose / respond / cancel / get) siguiendo el patrón Command + Handler + Facade + Controller. La ejecución del intercambio reutiliza la mecánica de `StealPokemonCommandHandler` (transferencia de `DraftPick`, monedas, sincronización de `user.getPokemons()`, bloqueo por jornada). Frontend: capa API + componentes React Query en `TeamsPage`.

**Tech Stack:** Java 17, Spring Boot, MongoDB (`MongoTemplate`), JUnit 5 + Mockito; React 19 + TypeScript + Vite + TanStack React Query + Axios.

**Spec de referencia:** `docs/superpowers/specs/2026-05-22-player-to-player-trades-design.md`

**Patrones a copiar (leer antes de empezar):**
- Handler con lógica de transferencia: `application/.../commands/steal/StealPokemonCommandHandler.java`
- Repositorio + adaptador: `domain/.../repository/ScheduleRepository.java` + `infrastructure/.../repository/ScheduleRepositoryImpl.java`
- Test de handler: `application/.../commands/steal/StealPokemonCommandHandlerTest.java` y `.../commands/league/UpdateLeagueSettingsCommandHandlerTest.java`
- Facade / Controller: `application/.../commands/steal/StealFacade.java` + `api-rest/.../StealController.java`

**Convenciones del proyecto:**
- Cada `Command` es un `record` que **implementa `interface Command`** (si no, `SpringMediator` no lo registra).
- `Command` y `CommandHandler` viven en `application/.../commands/trade/`.
- Excepciones → HTTP: `IllegalArgumentException` 400, `IllegalStateException` 409, `ForbiddenOperationException` 403.
- Build: `./mvnw -B -ntp test -pl application -am` (tests rápidos) y `./mvnw -B -ntp clean verify` (gate JaCoCo 80%).
- Commits por tarea. Rama `feature/player-trades` (ya creada, contiene el spec).

---

## FASE 1 — BACKEND (repo `pokefantasy`, rama `feature/player-trades`, PR → `develop`)

### Task 1: `TradeStatus` enum + `TradeEntity`

**Files:**
- Create: `src/domain/src/main/java/com/villu/pokefantasy/dto/TradeStatus.java`
- Create: `src/domain/src/main/java/com/villu/pokefantasy/repository/entity/TradeEntity.java`

Datos puros (enum + POJO de persistencia): sin test unitario, igual que `DraftStatus`/`DraftEntity`.

- [ ] **Step 1: Crear el enum `TradeStatus`**

```java
package com.villu.pokefantasy.dto;

public enum TradeStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED
}
```

- [ ] **Step 2: Crear `TradeEntity`**

```java
package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.TradeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "trades")
public class TradeEntity {
    @Id
    private String id;
    private String leagueId;
    private String proposer;
    private String responder;
    private String proposerPokemonName;
    private int proposerPokemonId;
    private String responderPokemonName;
    private int responderPokemonId;
    private int coinsOffered;
    private TradeStatus status;
    private Instant createdAt;
    private Instant resolvedAt;
}
```

- [ ] **Step 3: Compilar el módulo domain**

Run: `./mvnw -B -ntp compile -pl domain`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/domain/src/main/java/com/villu/pokefantasy/dto/TradeStatus.java src/domain/src/main/java/com/villu/pokefantasy/repository/entity/TradeEntity.java
git commit -m "feat: add TradeStatus enum and TradeEntity"
```

---

### Task 2: `TradeRepository` puerto + adaptador Mongo

**Files:**
- Create: `src/domain/src/main/java/com/villu/pokefantasy/repository/TradeRepository.java`
- Create: `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/TradeRepositoryImpl.java`

El adaptador es fino (delega en `MongoTemplate`) — sin test unitario, igual que `ScheduleRepositoryImpl`.

- [ ] **Step 1: Crear el puerto `TradeRepository`**

```java
package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.TradeEntity;

import java.util.List;
import java.util.Optional;

public interface TradeRepository {
    TradeEntity save(TradeEntity trade);
    Optional<TradeEntity> findById(String id);
    /** Trades donde el usuario es proposer O responder, en cualquier estado. */
    List<TradeEntity> findByLeagueIdAndParticipant(String leagueId, String username);
    /** Trades PENDING de la liga (para el auto-cancelado de propuestas en conflicto). */
    List<TradeEntity> findPendingByLeagueId(String leagueId);
}
```

- [ ] **Step 2: Crear el adaptador `TradeRepositoryImpl`**

```java
package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TradeRepositoryImpl implements TradeRepository {

    private final MongoTemplate mongoTemplate;

    public TradeRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public TradeEntity save(TradeEntity trade) {
        try {
            return mongoTemplate.save(trade);
        } catch (Exception e) {
            log.error("Error saving trade: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<TradeEntity> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, TradeEntity.class));
    }

    @Override
    public List<TradeEntity> findByLeagueIdAndParticipant(String leagueId, String username) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId)
                .orOperator(
                        Criteria.where("proposer").is(username),
                        Criteria.where("responder").is(username)));
        return mongoTemplate.find(query, TradeEntity.class);
    }

    @Override
    public List<TradeEntity> findPendingByLeagueId(String leagueId) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId)
                .and("status").is(TradeStatus.PENDING));
        return mongoTemplate.find(query, TradeEntity.class);
    }
}
```

- [ ] **Step 3: Compilar domain + infrastructure**

Run: `./mvnw -B -ntp compile -pl domain,infrastructure -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/domain/src/main/java/com/villu/pokefantasy/repository/TradeRepository.java src/infrastructure/src/main/java/com/villu/pokefantasy/repository/TradeRepositoryImpl.java
git commit -m "feat: add TradeRepository port and Mongo adapter"
```

---

### Task 3: DTOs de response y request

**Files:**
- Create: `src/domain/src/main/java/com/villu/pokefantasy/response/TradeResponse.java`
- Create: `src/domain/src/main/java/com/villu/pokefantasy/request/trade/ProposeTradeRequest.java`
- Create: `src/domain/src/main/java/com/villu/pokefantasy/request/trade/RespondToTradeRequest.java`

POJOs Lombok — sin test.

- [ ] **Step 1: Crear `TradeResponse`**

```java
package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TradeResponse {
    private String id;
    private String leagueId;
    private String proposer;
    private String responder;
    private String proposerPokemonName;
    private int proposerPokemonId;
    private String responderPokemonName;
    private int responderPokemonId;
    private int coinsOffered;
    private String status;        // "PENDING" | "ACCEPTED" | "REJECTED" | "CANCELLED"
    private String createdAt;     // ISO instant
    private String resolvedAt;    // ISO instant, null mientras PENDING
}
```

- [ ] **Step 2: Crear `ProposeTradeRequest`**

```java
package com.villu.pokefantasy.request.trade;

import lombok.Data;

@Data
public class ProposeTradeRequest {
    private String responder;
    private String proposerPokemonName;
    private String responderPokemonName;
    private Integer coinsOffered;   // nullable → el controller lo trata como 0
}
```

- [ ] **Step 3: Crear `RespondToTradeRequest`**

```java
package com.villu.pokefantasy.request.trade;

import lombok.Data;

@Data
public class RespondToTradeRequest {
    private boolean accept;
}
```

- [ ] **Step 4: Compilar domain**

Run: `./mvnw -B -ntp compile -pl domain`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/domain/src/main/java/com/villu/pokefantasy/response/TradeResponse.java src/domain/src/main/java/com/villu/pokefantasy/request/trade/
git commit -m "feat: add trade request and response DTOs"
```

---

### Task 4: `ProposeTradeCommand` + handler (TDD)

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/ProposeTradeCommand.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/ProposeTradeCommandHandler.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/trade/ProposeTradeCommandHandlerTest.java`

- [ ] **Step 1: Crear el record `ProposeTradeCommand`**

```java
package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

public record ProposeTradeCommand(
        String leagueId,
        String proposer,
        String responder,
        String proposerPokemonName,
        String responderPokemonName,
        int coinsOffered
) implements Command {}
```

- [ ] **Step 2: Escribir el test que falla**

Crear `ProposeTradeCommandHandlerTest`. Estructura Mockito idéntica a `StealPokemonCommandHandlerTest` (`@ExtendWith(MockitoExtension.class)`, mocks por constructor). Mocks: `TradeRepository`, `DraftRepository`, `LeagueRepository`, `ScheduleRepository`.

Helpers de test:
- `completedDraftWithPicks(...)` — un `DraftEntity` con `status = COMPLETED` y una lista de `DraftPick` (`new DraftPick(username, name, id, round, Instant.now(), null, null)`).
- `leagueWith(String... usernames)` — `LeagueEntity` con `members` = `LeagueMember(username, LeagueRole.USER, 1000)` cada uno.

Test ancla (escribir completo):

```java
@Test
void handle_validProposal_savesPendingTrade() {
    DraftEntity draft = new DraftEntity();
    draft.setStatus(DraftStatus.COMPLETED);
    draft.setPicks(new ArrayList<>(List.of(
            new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null),
            new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null))));
    when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

    LeagueEntity league = new LeagueEntity();
    league.setMembers(List.of(
            new LeagueMember("ash", LeagueRole.USER, 1000),
            new LeagueMember("brock", LeagueRole.USER, 1000)));
    when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
    when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.empty());

    handler.handle(new ProposeTradeCommand("l1", "ash", "brock", "pikachu", "onix", 150));

    ArgumentCaptor<TradeEntity> captor = ArgumentCaptor.forClass(TradeEntity.class);
    verify(tradeRepository).save(captor.capture());
    TradeEntity saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(TradeStatus.PENDING);
    assertThat(saved.getProposer()).isEqualTo("ash");
    assertThat(saved.getResponder()).isEqualTo("brock");
    assertThat(saved.getProposerPokemonName()).isEqualTo("pikachu");
    assertThat(saved.getProposerPokemonId()).isEqualTo(25);
    assertThat(saved.getResponderPokemonName()).isEqualTo("onix");
    assertThat(saved.getResponderPokemonId()).isEqualTo(95);
    assertThat(saved.getCoinsOffered()).isEqualTo(150);
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getResolvedAt()).isNull();
}
```

Resto de tests (mismo patrón Mockito; cada fila es un `@Test`):

| Método | Arrange | Act | Assert |
|--------|---------|-----|--------|
| `handle_proposerEqualsResponder_throwsIllegalArgument` | — (no hace falta stub) | `handle(cmd con proposer="ash", responder="ASH")` | `IllegalArgumentException`, mensaje contiene "ti mismo" |
| `handle_negativeCoins_throwsIllegalArgument` | — | `handle(cmd con coinsOffered=-1)` | `IllegalArgumentException`, contiene ">= 0" |
| `handle_draftNotCompleted_throwsIllegalState` | `findLatestByLeagueId` devuelve draft `IN_PROGRESS` | `handle(validCmd)` | `IllegalStateException`, contiene "draft is completed" |
| `handle_noDraft_throwsIllegalState` | `findLatestByLeagueId` → `Optional.empty()` | `handle(validCmd)` | `IllegalStateException` |
| `handle_leagueNotFound_throwsIllegalArgument` | draft COMPLETED ok; `leagueRepository.findById` → empty | `handle(validCmd)` | `IllegalArgumentException`, contiene "League not found" |
| `handle_responderNotMember_throwsIllegalArgument` | draft ok; liga solo con "ash" | `handle(validCmd con responder="brock")` | `IllegalArgumentException`, contiene "no es miembro" |
| `handle_proposerPokemonNotOwned_throwsIllegalArgument` | draft sin pick de "ash"/"pikachu" | `handle(validCmd)` | `IllegalArgumentException`, contiene "no está en el equipo" |
| `handle_responderPokemonNotOwned_throwsIllegalArgument` | draft sin pick de "brock"/"onix" | `handle(validCmd)` | `IllegalArgumentException` |
| `handle_pokemonLocked_throwsIllegalState` | pick de "ash"/"pikachu" con `lockedUntilRound=1`; `scheduleRepository.findByLeagueId` devuelve schedule con jornada 1 que tiene un `Match` `PENDING` | `handle(validCmd)` | `IllegalStateException`, contiene "bloqueado" |
| `handle_insufficientBalance_throwsIllegalState` | proposer "ash" con `coinBalance=50`; cmd con `coinsOffered=150` | `handle(cmd)` | `IllegalStateException`, contiene "suficientes monedas" |
| `commandType_returnsCorrectClass` | — | `handler.commandType()` | igual a `ProposeTradeCommand.class` |

Run: `./mvnw -B -ntp test -pl application -am -Dtest=ProposeTradeCommandHandlerTest`
Expected: FAIL — `ProposeTradeCommandHandler` no existe / no compila.

- [ ] **Step 3: Implementar `ProposeTradeCommandHandler`**

```java
package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProposeTradeCommandHandler implements CommandHandler<ProposeTradeCommand, Void> {

    private final TradeRepository tradeRepository;
    private final DraftRepository draftRepository;
    private final LeagueRepository leagueRepository;
    private final ScheduleRepository scheduleRepository;

    public ProposeTradeCommandHandler(TradeRepository tradeRepository,
                                      DraftRepository draftRepository,
                                      LeagueRepository leagueRepository,
                                      ScheduleRepository scheduleRepository) {
        this.tradeRepository = tradeRepository;
        this.draftRepository = draftRepository;
        this.leagueRepository = leagueRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    public Void handle(ProposeTradeCommand command) {
        String leagueId = command.leagueId();
        String proposer = command.proposer().trim();
        String responder = command.responder().trim();
        String proposerPokemonName = command.proposerPokemonName().trim();
        String responderPokemonName = command.responderPokemonName().trim();
        int coinsOffered = command.coinsOffered();

        if (proposer.equalsIgnoreCase(responder)) {
            throw new IllegalArgumentException("No puedes proponerte un trade a ti mismo");
        }
        if (coinsOffered < 0) {
            throw new IllegalArgumentException("coinsOffered must be >= 0");
        }

        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException(
                        "Trades are only allowed after the draft is completed"));

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        LeagueMember proposerMember = findMember(league, proposer);
        findMember(league, responder); // valida que el responder es miembro

        DraftPick proposerPick = findPick(draft, proposer, proposerPokemonName);
        DraftPick responderPick = findPick(draft, responder, responderPokemonName);

        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId).orElse(null);
        assertNotLocked(proposerPick, schedule, proposerPokemonName);
        assertNotLocked(responderPick, schedule, responderPokemonName);

        if (proposerMember.getCoinBalance() < coinsOffered) {
            throw new IllegalStateException("No tienes suficientes monedas para esta oferta. Necesitas "
                    + coinsOffered + " pero tienes " + proposerMember.getCoinBalance() + ".");
        }

        TradeEntity trade = TradeEntity.builder()
                .leagueId(leagueId)
                .proposer(proposer)
                .responder(responder)
                .proposerPokemonName(proposerPick.getPokemonName())
                .proposerPokemonId(proposerPick.getPokemonId())
                .responderPokemonName(responderPick.getPokemonName())
                .responderPokemonId(responderPick.getPokemonId())
                .coinsOffered(coinsOffered)
                .status(TradeStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        tradeRepository.save(trade);
        return null;
    }

    private LeagueMember findMember(LeagueEntity league, String username) {
        return league.getMembers().stream()
                .filter(m -> username.equalsIgnoreCase(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(username + " no es miembro de la liga"));
    }

    private DraftPick findPick(DraftEntity draft, String username, String pokemonName) {
        return draft.getPicks().stream()
                .filter(p -> username.equalsIgnoreCase(p.getUsername())
                        && pokemonName.equalsIgnoreCase(p.getPokemonName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + pokemonName + "' no está en el equipo de " + username));
    }

    private void assertNotLocked(DraftPick pick, ScheduleEntity schedule, String pokemonName) {
        if (pick.getLockedUntilRound() == null || schedule == null) return;
        int lockedRound = pick.getLockedUntilRound();
        boolean stillLocked = schedule.getJornadas().stream()
                .filter(j -> j.getRoundNumber() == lockedRound)
                .findFirst()
                .map(j -> j.getMatches().stream()
                        .anyMatch(m -> MatchStatus.PENDING.equals(m.getStatus())))
                .orElse(false);
        if (stillLocked) {
            throw new IllegalStateException(
                    "'" + pokemonName + "' está bloqueado hasta que finalice la jornada actual.");
        }
    }

    @Override
    public Class<ProposeTradeCommand> commandType() {
        return ProposeTradeCommand.class;
    }
}
```

- [ ] **Step 4: Ejecutar el test, verificar que pasa**

Run: `./mvnw -B -ntp test -pl application -am -Dtest=ProposeTradeCommandHandlerTest`
Expected: PASS — todos los tests verdes.

- [ ] **Step 5: Commit**

```bash
git add src/application/src/main/java/com/villu/pokefantasy/commands/trade/ProposeTradeCommand.java src/application/src/main/java/com/villu/pokefantasy/commands/trade/ProposeTradeCommandHandler.java src/application/src/test/java/com/villu/pokefantasy/commands/trade/ProposeTradeCommandHandlerTest.java
git commit -m "feat: add ProposeTrade command and handler"
```

---

### Task 5: `RespondToTradeCommand` + handler (TDD) — ejecución del intercambio

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/RespondToTradeCommand.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/RespondToTradeCommandHandler.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/trade/RespondToTradeCommandHandlerTest.java`

- [ ] **Step 1: Crear el record `RespondToTradeCommand`**

```java
package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

public record RespondToTradeCommand(
        String leagueId,
        String tradeId,
        String respondingUser,
        boolean accept
) implements Command {}
```

- [ ] **Step 2: Escribir el test que falla**

Crear `RespondToTradeCommandHandlerTest`. Mocks: `TradeRepository`, `DraftRepository`, `ScheduleRepository`, `LeagueRepository`, `UserRepository`, `ClosedListRepository`, `JornadaWindowService`.

Helper `pendingTrade()` — devuelve `TradeEntity.builder().id("t1").leagueId("l1").proposer("ash").responder("brock").proposerPokemonName("pikachu").proposerPokemonId(25).responderPokemonName("onix").responderPokemonId(95).coinsOffered(100).status(TradeStatus.PENDING).createdAt(Instant.now()).build()`.

Test ancla 1 — rechazo (escribir completo):

```java
@Test
void handle_reject_marksTradeRejected() {
    TradeEntity trade = pendingTrade();
    when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

    handler.handle(new RespondToTradeCommand("l1", "t1", "brock", false));

    ArgumentCaptor<TradeEntity> captor = ArgumentCaptor.forClass(TradeEntity.class);
    verify(tradeRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(TradeStatus.REJECTED);
    assertThat(captor.getValue().getResolvedAt()).isNotNull();
    verifyNoInteractions(draftRepository);
}
```

Test ancla 2 — aceptación que ejecuta el intercambio (escribir completo):

```java
@Test
void handle_accept_executesSwapAndLocksBothPicks() {
    TradeEntity trade = pendingTrade();
    when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

    DraftEntity draft = new DraftEntity();
    draft.setStatus(DraftStatus.COMPLETED);
    DraftPick ashPick = new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null);
    DraftPick brockPick = new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null);
    draft.setPicks(new ArrayList<>(List.of(ashPick, brockPick)));
    when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

    ScheduleEntity schedule = new ScheduleEntity();
    when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
    when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(true);
    when(jornadaWindowService.getActiveJornada(schedule))
            .thenReturn(Optional.of(new ScheduleEntity.Jornada(3, new ArrayList<>(), null)));

    LeagueEntity league = new LeagueEntity();
    league.setMembers(List.of(
            new LeagueMember("ash", LeagueRole.USER, 500),
            new LeagueMember("brock", LeagueRole.USER, 200)));
    when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

    when(userRepository.findByUsername("ash")).thenReturn(userWith("ash", "pikachu", 25));
    when(userRepository.findByUsername("brock")).thenReturn(userWith("brock", "onix", 95));
    when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(anyString(), eq("l1")))
            .thenReturn(Optional.empty());
    when(tradeRepository.findPendingByLeagueId("l1")).thenReturn(List.of(trade));

    handler.handle(new RespondToTradeCommand("l1", "t1", "brock", true));

    // picks intercambiados y bloqueados
    assertThat(ashPick.getUsername()).isEqualTo("brock");
    assertThat(brockPick.getUsername()).isEqualTo("ash");
    assertThat(ashPick.getLockedUntilRound()).isEqualTo(3);
    assertThat(brockPick.getLockedUntilRound()).isEqualTo(3);
    // monedas: ash paga 100, brock recibe 100
    assertThat(league.getMembers().get(0).getCoinBalance()).isEqualTo(400);
    assertThat(league.getMembers().get(1).getCoinBalance()).isEqualTo(300);
    // trade marcado ACCEPTED
    assertThat(trade.getStatus()).isEqualTo(TradeStatus.ACCEPTED);
    verify(draftRepository).save(draft);
    verify(leagueRepository).save(league);
    verify(userRepository, times(2)).updateUserWithPokemons(any());
}
```

Helper de test:

```java
private UserEntity userWith(String username, String pokemonName, int pokemonId) {
    UserEntity u = new UserEntity();
    u.setName(username);
    Pokemons p = new Pokemons();
    p.setId(pokemonId);
    p.setName(pokemonName);
    p.setLeagueId("l1");
    u.setPokemons(new ArrayList<>(List.of(p)));
    return u;
}
```

Resto de tests. **Importante (Mockito strict stubs):** `@ExtendWith(MockitoExtension.class)` falla si hay stubs sin usar. Cada test de camino negativo debe stubbear **solo** lo que el handler alcanza antes de lanzar. La columna "Arrange" lista los stubs mínimos exactos — no heredar stubs del ancla 2.

| Método | Arrange (stubs mínimos exactos) | Act | Assert |
|--------|---------|-----|--------|
| `handle_tradeNotFound_throwsIllegalArgument` | `tradeRepository.findById("t1")` → empty | accept | `IllegalArgumentException`, "Trade not found" |
| `handle_tradeNotPending_throwsIllegalState` | `findById` → trade con `status=ACCEPTED` | accept | `IllegalStateException`, "ya no está pendiente" |
| `handle_notResponder_throwsForbidden` | `findById` → `pendingTrade()` | `RespondToTradeCommand("l1","t1","ash",true)` (ash es proposer, no responder) | `ForbiddenOperationException` |
| `handle_accept_draftNotCompleted_throwsIllegalState` | `findById` → `pendingTrade()`; `draftRepository.findLatestByLeagueId("l1")` → draft `IN_PROGRESS` | accept | `IllegalStateException` |
| `handle_accept_noSchedule_throwsIllegalState` | `findById` → `pendingTrade()`; draft `COMPLETED`; `scheduleRepository.findByLeagueId("l1")` → empty | accept | `IllegalStateException`, "No schedule" |
| `handle_accept_swapWindowClosed_throwsIllegalState` | `findById` → `pendingTrade()`; draft `COMPLETED`; schedule presente; `jornadaWindowService.isSwapWindowOpen` → `false` | accept | `IllegalStateException`, "ventana de intercambios" |
| `handle_accept_proposerPokemonMoved_cancelsTradeAndThrows` | `findById` → `pendingTrade()`; draft `COMPLETED` con **solo** el pick de "brock"/"onix" (sin "ash"/"pikachu"); schedule presente; `isSwapWindowOpen` → `true`; `leagueRepository.findById("l1")` → liga con "ash" y "brock" | accept | `IllegalStateException` "ya no es válida"; `verify(tradeRepository).save(...)` con `status=CANCELLED` |
| `handle_accept_pickLocked_throwsIllegalState` | `findById` → `pendingTrade()`; draft `COMPLETED` con ambos picks, el de "ash"/"pikachu" con `lockedUntilRound=3`; schedule con jornada 3 que contiene un `Match` `PENDING`; `isSwapWindowOpen` → `true`; `leagueRepository.findById` → liga con ambos. **No** stubbear `getActiveJornada` ni `userRepository`/`closedListRepository` (el handler lanza antes) | accept | `IllegalStateException`, "bloqueado" |
| `handle_accept_insufficientProposerBalance_throwsIllegalState` | `findById` → `pendingTrade()` (`coinsOffered=100`); draft `COMPLETED` con ambos picks sin bloqueo; schedule presente; `isSwapWindowOpen` → `true`; `leagueRepository.findById` → liga con "ash" `coinBalance=50` y "brock". **No** stubbear `getActiveJornada`/`userRepository`/`closedListRepository` | accept | `IllegalStateException`, "suficientes monedas" |
| `handle_accept_cancelsConflictingPendingTrades` | todos los stubs del ancla 2 + `tradeRepository.findPendingByLeagueId("l1")` → `[trade, otherTrade]`, con `otherTrade` (id "t2", `status=PENDING`, `proposerPokemonName="pikachu"`) | accept | `otherTrade.getStatus()` = `CANCELLED`; `verify(tradeRepository).save(otherTrade)` |
| `commandType_returnsCorrectClass` | — | `commandType()` | `RespondToTradeCommand.class` |

Nota: el ancla 2 (`handle_accept_executesSwapAndLocksBothPicks`) ya stubbea `findPendingByLeagueId` → `List.of(trade)`; `cancelConflicting` itera, encuentra el propio trade (`getId().equals` → `continue`) y no guarda nada más. Todos sus stubs se usan.

Run: `./mvnw -B -ntp test -pl application -am -Dtest=RespondToTradeCommandHandlerTest`
Expected: FAIL — el handler no existe.

- [ ] **Step 3: Implementar `RespondToTradeCommandHandler`**

```java
package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RespondToTradeCommandHandler implements CommandHandler<RespondToTradeCommand, Void> {

    private final TradeRepository tradeRepository;
    private final DraftRepository draftRepository;
    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final ClosedListRepository closedListRepository;
    private final JornadaWindowService jornadaWindowService;

    public RespondToTradeCommandHandler(TradeRepository tradeRepository,
                                        DraftRepository draftRepository,
                                        ScheduleRepository scheduleRepository,
                                        LeagueRepository leagueRepository,
                                        UserRepository userRepository,
                                        ClosedListRepository closedListRepository,
                                        JornadaWindowService jornadaWindowService) {
        this.tradeRepository = tradeRepository;
        this.draftRepository = draftRepository;
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.closedListRepository = closedListRepository;
        this.jornadaWindowService = jornadaWindowService;
    }

    @Override
    public Void handle(RespondToTradeCommand command) {
        TradeEntity trade = tradeRepository.findById(command.tradeId())
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + command.tradeId()));

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new IllegalStateException("Esta propuesta ya no está pendiente");
        }
        if (!trade.getResponder().equalsIgnoreCase(command.respondingUser())) {
            throw new ForbiddenOperationException("Solo el destinatario puede responder a esta propuesta");
        }

        if (!command.accept()) {
            trade.setStatus(TradeStatus.REJECTED);
            trade.setResolvedAt(Instant.now());
            tradeRepository.save(trade);
            return null;
        }

        executeTrade(trade);
        return null;
    }

    private void executeTrade(TradeEntity trade) {
        String leagueId = trade.getLeagueId();

        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException(
                        "Trades are only allowed after the draft is completed"));

        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No schedule found for this league"));

        if (!jornadaWindowService.isSwapWindowOpen(schedule)) {
            throw new IllegalStateException(
                    "La ventana de intercambios no está abierta. El plazo cerró el viernes a las 16:00.");
        }

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        DraftPick proposerPick = findPickOrInvalidate(draft, trade,
                trade.getProposer(), trade.getProposerPokemonName());
        DraftPick responderPick = findPickOrInvalidate(draft, trade,
                trade.getResponder(), trade.getResponderPokemonName());

        assertNotLocked(proposerPick, schedule, trade.getProposerPokemonName());
        assertNotLocked(responderPick, schedule, trade.getResponderPokemonName());

        LeagueMember proposerMember = getMember(league, trade.getProposer());
        LeagueMember responderMember = getMember(league, trade.getResponder());
        if (proposerMember.getCoinBalance() < trade.getCoinsOffered()) {
            throw new IllegalStateException(
                    "El proponente ya no tiene suficientes monedas para esta oferta.");
        }

        // 1. Monedas A → B
        proposerMember.setCoinBalance(proposerMember.getCoinBalance() - trade.getCoinsOffered());
        responderMember.setCoinBalance(responderMember.getCoinBalance() + trade.getCoinsOffered());
        leagueRepository.save(league);

        // 2. user.getPokemons() de ambos
        movePokemon(trade.getProposer(), trade.getProposerPokemonName(),
                trade.getResponderPokemonName(), trade.getResponderPokemonId(), leagueId);
        movePokemon(trade.getResponder(), trade.getResponderPokemonName(),
                trade.getProposerPokemonName(), trade.getProposerPokemonId(), leagueId);

        // 3. DraftPicks: intercambio de username + bloqueo
        int lockedRound = jornadaWindowService.getActiveJornada(schedule)
                .map(Jornada::getRoundNumber).orElse(0);
        Instant now = Instant.now();
        proposerPick.setUsername(trade.getResponder());
        proposerPick.setLockedUntilRound(lockedRound);
        proposerPick.setPickedAt(now);
        responderPick.setUsername(trade.getProposer());
        responderPick.setLockedUntilRound(lockedRound);
        responderPick.setPickedAt(now);
        draftRepository.save(draft);

        // 4. Trade aceptado
        trade.setStatus(TradeStatus.ACCEPTED);
        trade.setResolvedAt(now);
        tradeRepository.save(trade);

        // 5. Auto-cancelar propuestas en conflicto
        cancelConflicting(leagueId, trade.getId(),
                trade.getProposerPokemonName(), trade.getResponderPokemonName());
    }

    private DraftPick findPickOrInvalidate(DraftEntity draft, TradeEntity trade,
                                           String username, String pokemonName) {
        return draft.getPicks().stream()
                .filter(p -> username.equalsIgnoreCase(p.getUsername())
                        && pokemonName.equalsIgnoreCase(p.getPokemonName()))
                .findFirst()
                .orElseGet(() -> {
                    trade.setStatus(TradeStatus.CANCELLED);
                    trade.setResolvedAt(Instant.now());
                    tradeRepository.save(trade);
                    throw new IllegalStateException("La propuesta ya no es válida: '"
                            + pokemonName + "' ha cambiado de dueño.");
                });
    }

    private void movePokemon(String username, String giveName,
                             String takeName, int takeId, String leagueId) {
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) return;
        List<Pokemons> pokemons = user.getPokemons() != null
                ? new ArrayList<>(user.getPokemons()) : new ArrayList<>();
        pokemons.removeIf(p -> leagueId.equals(p.getLeagueId())
                && giveName.equalsIgnoreCase(p.getName()));
        Pokemons received = new Pokemons();
        received.setId(takeId);
        received.setName(takeName);
        received.setLeagueId(leagueId);
        closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(takeName, leagueId)
                .ifPresent(entry -> {
                    received.setStats(entry.getStats());
                    received.setTypes(entry.getTypes());
                });
        pokemons.add(received);
        user.setPokemons(pokemons);
        userRepository.updateUserWithPokemons(user);
    }

    private void cancelConflicting(String leagueId, String executedTradeId,
                                   String name1, String name2) {
        for (TradeEntity other : tradeRepository.findPendingByLeagueId(leagueId)) {
            if (other.getId().equals(executedTradeId)) continue;
            boolean conflicts = matches(other.getProposerPokemonName(), name1, name2)
                    || matches(other.getResponderPokemonName(), name1, name2);
            if (conflicts) {
                other.setStatus(TradeStatus.CANCELLED);
                other.setResolvedAt(Instant.now());
                tradeRepository.save(other);
            }
        }
    }

    private boolean matches(String name, String a, String b) {
        return name.equalsIgnoreCase(a) || name.equalsIgnoreCase(b);
    }

    private void assertNotLocked(DraftPick pick, ScheduleEntity schedule, String pokemonName) {
        if (pick.getLockedUntilRound() == null) return;
        int lockedRound = pick.getLockedUntilRound();
        boolean stillLocked = schedule.getJornadas().stream()
                .filter(j -> j.getRoundNumber() == lockedRound)
                .findFirst()
                .map(j -> j.getMatches().stream()
                        .anyMatch(m -> MatchStatus.PENDING.equals(m.getStatus())))
                .orElse(false);
        if (stillLocked) {
            throw new IllegalStateException(
                    "'" + pokemonName + "' está bloqueado hasta que finalice la jornada actual.");
        }
    }

    private LeagueMember getMember(LeagueEntity league, String username) {
        return league.getMembers().stream()
                .filter(m -> username.equalsIgnoreCase(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Member not found: " + username));
    }

    @Override
    public Class<RespondToTradeCommand> commandType() {
        return RespondToTradeCommand.class;
    }
}
```

Nota: `schedule.getJornadas()` puede ser null en un `ScheduleEntity` recién instanciado en los tests; en `assertNotLocked` solo se llama cuando un pick está bloqueado, y esos tests construyen el schedule con jornadas. Para los tests del camino feliz, `ScheduleEntity` sin jornadas no entra en `assertNotLocked` (picks sin `lockedUntilRound`).

- [ ] **Step 4: Ejecutar el test, verificar que pasa**

Run: `./mvnw -B -ntp test -pl application -am -Dtest=RespondToTradeCommandHandlerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/application/src/main/java/com/villu/pokefantasy/commands/trade/RespondToTradeCommand.java src/application/src/main/java/com/villu/pokefantasy/commands/trade/RespondToTradeCommandHandler.java src/application/src/test/java/com/villu/pokefantasy/commands/trade/RespondToTradeCommandHandlerTest.java
git commit -m "feat: add RespondToTrade command and handler with trade execution"
```

---

### Task 6: `CancelTradeCommand` + handler (TDD)

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/CancelTradeCommand.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/CancelTradeCommandHandler.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/trade/CancelTradeCommandHandlerTest.java`

- [ ] **Step 1: Crear el record `CancelTradeCommand`**

```java
package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

public record CancelTradeCommand(
        String leagueId,
        String tradeId,
        String requestingUser
) implements Command {}
```

- [ ] **Step 2: Escribir el test que falla**

Crear `CancelTradeCommandHandlerTest`. Mock: `TradeRepository`. Helper `pendingTrade()` igual que en Task 5 (proposer "ash", responder "brock").

Test ancla (escribir completo):

```java
@Test
void handle_proposerCancels_marksCancelled() {
    TradeEntity trade = pendingTrade();
    when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

    handler.handle(new CancelTradeCommand("l1", "t1", "ash"));

    ArgumentCaptor<TradeEntity> captor = ArgumentCaptor.forClass(TradeEntity.class);
    verify(tradeRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(TradeStatus.CANCELLED);
    assertThat(captor.getValue().getResolvedAt()).isNotNull();
}
```

Resto de tests:

| Método | Arrange | Act | Assert |
|--------|---------|-----|--------|
| `handle_tradeNotFound_throwsIllegalArgument` | `findById` → empty | `handle(cmd)` | `IllegalArgumentException`, "Trade not found" |
| `handle_tradeNotPending_throwsIllegalState` | trade con `status=ACCEPTED` | `handle(cmd con "ash")` | `IllegalStateException`, "pendientes" |
| `handle_notProposer_throwsForbidden` | `pendingTrade()` | `handle(CancelTradeCommand("l1","t1","brock"))` | `ForbiddenOperationException` |
| `commandType_returnsCorrectClass` | — | `commandType()` | `CancelTradeCommand.class` |

Run: `./mvnw -B -ntp test -pl application -am -Dtest=CancelTradeCommandHandlerTest`
Expected: FAIL

- [ ] **Step 3: Implementar `CancelTradeCommandHandler`**

```java
package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CancelTradeCommandHandler implements CommandHandler<CancelTradeCommand, Void> {

    private final TradeRepository tradeRepository;

    public CancelTradeCommandHandler(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    public Void handle(CancelTradeCommand command) {
        TradeEntity trade = tradeRepository.findById(command.tradeId())
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + command.tradeId()));

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new IllegalStateException("Solo se pueden cancelar propuestas pendientes");
        }
        if (!trade.getProposer().equalsIgnoreCase(command.requestingUser())) {
            throw new ForbiddenOperationException("Solo el proponente puede cancelar esta propuesta");
        }

        trade.setStatus(TradeStatus.CANCELLED);
        trade.setResolvedAt(Instant.now());
        tradeRepository.save(trade);
        return null;
    }

    @Override
    public Class<CancelTradeCommand> commandType() {
        return CancelTradeCommand.class;
    }
}
```

- [ ] **Step 4: Ejecutar el test, verificar que pasa**

Run: `./mvnw -B -ntp test -pl application -am -Dtest=CancelTradeCommandHandlerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/application/src/main/java/com/villu/pokefantasy/commands/trade/CancelTradeCommand.java src/application/src/main/java/com/villu/pokefantasy/commands/trade/CancelTradeCommandHandler.java src/application/src/test/java/com/villu/pokefantasy/commands/trade/CancelTradeCommandHandlerTest.java
git commit -m "feat: add CancelTrade command and handler"
```

---

### Task 7: `GetTradesCommand` + handler (TDD)

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/GetTradesCommand.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/GetTradesCommandHandler.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/trade/GetTradesCommandHandlerTest.java`

- [ ] **Step 1: Crear el record `GetTradesCommand`**

```java
package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

public record GetTradesCommand(
        String leagueId,
        String username
) implements Command {}
```

- [ ] **Step 2: Escribir el test que falla**

Crear `GetTradesCommandHandlerTest`. Mock: `TradeRepository`.

Test ancla (escribir completo):

```java
@Test
void handle_mapsEntitiesToResponses() {
    TradeEntity trade = TradeEntity.builder()
            .id("t1").leagueId("l1").proposer("ash").responder("brock")
            .proposerPokemonName("pikachu").proposerPokemonId(25)
            .responderPokemonName("onix").responderPokemonId(95)
            .coinsOffered(100).status(TradeStatus.PENDING)
            .createdAt(Instant.parse("2026-05-22T10:00:00Z")).build();
    when(tradeRepository.findByLeagueIdAndParticipant("l1", "ash"))
            .thenReturn(List.of(trade));

    List<TradeResponse> result = handler.handle(new GetTradesCommand("l1", "ash"));

    assertThat(result).hasSize(1);
    TradeResponse r = result.get(0);
    assertThat(r.getId()).isEqualTo("t1");
    assertThat(r.getProposer()).isEqualTo("ash");
    assertThat(r.getResponder()).isEqualTo("brock");
    assertThat(r.getProposerPokemonName()).isEqualTo("pikachu");
    assertThat(r.getProposerPokemonId()).isEqualTo(25);
    assertThat(r.getResponderPokemonName()).isEqualTo("onix");
    assertThat(r.getResponderPokemonId()).isEqualTo(95);
    assertThat(r.getCoinsOffered()).isEqualTo(100);
    assertThat(r.getStatus()).isEqualTo("PENDING");
    assertThat(r.getCreatedAt()).isEqualTo("2026-05-22T10:00:00Z");
    assertThat(r.getResolvedAt()).isNull();
}
```

Resto de tests:

| Método | Arrange | Act | Assert |
|--------|---------|-----|--------|
| `handle_noTrades_returnsEmptyList` | `findByLeagueIdAndParticipant` → `List.of()` | `handle(cmd)` | resultado vacío |
| `handle_resolvedTrade_includesResolvedAt` | trade `ACCEPTED` con `resolvedAt = Instant.parse("2026-05-22T12:00:00Z")` | `handle(cmd)` | `r.getResolvedAt()` = "2026-05-22T12:00:00Z", `r.getStatus()` = "ACCEPTED" |
| `commandType_returnsCorrectClass` | — | `commandType()` | `GetTradesCommand.class` |

Run: `./mvnw -B -ntp test -pl application -am -Dtest=GetTradesCommandHandlerTest`
Expected: FAIL

- [ ] **Step 3: Implementar `GetTradesCommandHandler`**

```java
package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.response.TradeResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetTradesCommandHandler implements CommandHandler<GetTradesCommand, List<TradeResponse>> {

    private final TradeRepository tradeRepository;

    public GetTradesCommandHandler(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    public List<TradeResponse> handle(GetTradesCommand command) {
        return tradeRepository.findByLeagueIdAndParticipant(command.leagueId(), command.username())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TradeResponse toResponse(TradeEntity t) {
        return TradeResponse.builder()
                .id(t.getId())
                .leagueId(t.getLeagueId())
                .proposer(t.getProposer())
                .responder(t.getResponder())
                .proposerPokemonName(t.getProposerPokemonName())
                .proposerPokemonId(t.getProposerPokemonId())
                .responderPokemonName(t.getResponderPokemonName())
                .responderPokemonId(t.getResponderPokemonId())
                .coinsOffered(t.getCoinsOffered())
                .status(t.getStatus().name())
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
                .resolvedAt(t.getResolvedAt() != null ? t.getResolvedAt().toString() : null)
                .build();
    }

    @Override
    public Class<GetTradesCommand> commandType() {
        return GetTradesCommand.class;
    }
}
```

- [ ] **Step 4: Ejecutar el test, verificar que pasa**

Run: `./mvnw -B -ntp test -pl application -am -Dtest=GetTradesCommandHandlerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/application/src/main/java/com/villu/pokefantasy/commands/trade/GetTradesCommand.java src/application/src/main/java/com/villu/pokefantasy/commands/trade/GetTradesCommandHandler.java src/application/src/test/java/com/villu/pokefantasy/commands/trade/GetTradesCommandHandlerTest.java
git commit -m "feat: add GetTrades command and handler"
```

---

### Task 8: `TradeFacade` + `TradeController`

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/TradeFacade.java`
- Create: `src/api-rest/src/main/java/com/villu/pokefantasy/TradeController.java`

Sin test (la lógica está en los handlers; el patrón Facade/Controller no se testea, igual que `StealFacade`/`StealController`).

- [ ] **Step 1: Crear `TradeFacade`**

```java
package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.TradeResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeFacade {

    private final Mediator mediator;

    public TradeFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public void propose(String leagueId, String proposer, String responder,
                         String proposerPokemonName, String responderPokemonName,
                         int coinsOffered) throws Exception {
        mediator.send(new ProposeTradeCommand(leagueId, proposer, responder,
                proposerPokemonName, responderPokemonName, coinsOffered));
    }

    public List<TradeResponse> getTrades(String leagueId, String username) throws Exception {
        return mediator.send(new GetTradesCommand(leagueId, username));
    }

    public void respond(String leagueId, String tradeId, String respondingUser,
                        boolean accept) throws Exception {
        mediator.send(new RespondToTradeCommand(leagueId, tradeId, respondingUser, accept));
    }

    public void cancel(String leagueId, String tradeId, String requestingUser) throws Exception {
        mediator.send(new CancelTradeCommand(leagueId, tradeId, requestingUser));
    }
}
```

- [ ] **Step 2: Crear `TradeController`**

```java
package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.trade.TradeFacade;
import com.villu.pokefantasy.request.trade.ProposeTradeRequest;
import com.villu.pokefantasy.request.trade.RespondToTradeRequest;
import com.villu.pokefantasy.response.TradeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/leagues/{leagueId}")
public class TradeController {

    private final TradeFacade tradeFacade;

    public TradeController(TradeFacade tradeFacade) {
        this.tradeFacade = tradeFacade;
    }

    @PostMapping("/trades")
    public ResponseEntity<Void> propose(@PathVariable String leagueId,
                                        @AuthenticationPrincipal UserDetails userDetails,
                                        @RequestBody ProposeTradeRequest request) throws Exception {
        int coins = request.getCoinsOffered() != null ? request.getCoinsOffered() : 0;
        tradeFacade.propose(leagueId, userDetails.getUsername(), request.getResponder(),
                request.getProposerPokemonName(), request.getResponderPokemonName(), coins);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeResponse>> getTrades(
            @PathVariable String leagueId,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(tradeFacade.getTrades(leagueId, userDetails.getUsername()));
    }

    @PostMapping("/trades/{tradeId}/respond")
    public ResponseEntity<Void> respond(@PathVariable String leagueId,
                                        @PathVariable String tradeId,
                                        @AuthenticationPrincipal UserDetails userDetails,
                                        @RequestBody RespondToTradeRequest request) throws Exception {
        tradeFacade.respond(leagueId, tradeId, userDetails.getUsername(), request.isAccept());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/trades/{tradeId}")
    public ResponseEntity<Void> cancel(@PathVariable String leagueId,
                                       @PathVariable String tradeId,
                                       @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        tradeFacade.cancel(leagueId, tradeId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 3: Compilar todo**

Run: `./mvnw -B -ntp compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/application/src/main/java/com/villu/pokefantasy/commands/trade/TradeFacade.java src/api-rest/src/main/java/com/villu/pokefantasy/TradeController.java
git commit -m "feat: add TradeFacade and TradeController endpoints"
```

---

### Task 9: Verificación completa + PR

- [ ] **Step 1: Build + tests + gate de cobertura**

Run: `./mvnw -B -ntp clean verify`
Expected: BUILD SUCCESS, gate JaCoCo 80% verde.

Si el gate de cobertura falla: revisar el informe `target/site/jacoco/index.html`. Los 4 handlers nuevos están cubiertos por sus tests; el adaptador `TradeRepositoryImpl` no tiene test (igual que el resto de `*RepositoryImpl`). Si la cobertura baja del 80%, añadir tests adicionales de ramas en los handler tests (no testear el adaptador Mongo).

- [ ] **Step 2: Push de la rama**

```bash
git push -u origin feature/player-trades
```

- [ ] **Step 3: Abrir el PR a `develop`**

```powershell
$headers = @{ Authorization = "Bearer $env:GITHUB_TOKEN"; Accept = "application/vnd.github+json" }
$pr = @{
  title = "feat: player-to-player trades (backend)"
  head  = "feature/player-trades"
  base  = "develop"
  body  = "Implementa el intercambio de Pokemon entre jugadores: coleccion trades, 4 endpoints CQRS (propose/respond/cancel/get), ejecucion del intercambio reutilizando la mecanica de steal. Spec en docs/superpowers/specs/2026-05-22-player-to-player-trades-design.md"
} | ConvertTo-Json
Invoke-RestMethod -Uri "https://api.github.com/repos/Pokemons-Fantasy/pokefantasy/pulls" -Method Post -Headers $headers -Body $pr -ContentType "application/json; charset=utf-8"
```

---

## FASE 2 — FRONTEND (repo `pokefantasy-web`, rama `feature/player-trades`, PR → `main`)

> El agente de frontend usa la skill `frontend-design` para el diseño visual de los componentes. El plan fija el contrato de datos y los requisitos funcionales; las decisiones visuales (layout, estilos, animaciones) son del agente.

**Contrato de API ya implementado en Fase 1:**
- `POST /v1/leagues/{id}/trades` — body `{ responder, proposerPokemonName, responderPokemonName, coinsOffered }`
- `GET /v1/leagues/{id}/trades` — devuelve `Trade[]`
- `POST /v1/leagues/{id}/trades/{tradeId}/respond` — body `{ accept: boolean }`
- `DELETE /v1/leagues/{id}/trades/{tradeId}`

### Task 10: Capa API `src/api/trades.ts`

**Files:**
- Create: `src/api/trades.ts`

- [ ] **Step 1: Explorar el patrón existente**

Leer `src/api/leagues.ts` y `src/api/pokemons.ts` para confirmar el patrón (`apiClient`, funciones async, interfaces exportadas).

- [ ] **Step 2: Crear `src/api/trades.ts`**

```ts
import { apiClient } from './client';

export type TradeStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';

export interface Trade {
  id: string;
  leagueId: string;
  proposer: string;
  responder: string;
  proposerPokemonName: string;
  proposerPokemonId: number;
  responderPokemonName: string;
  responderPokemonId: number;
  coinsOffered: number;
  status: TradeStatus;
  createdAt: string;
  resolvedAt?: string | null;
}

export interface ProposeTradePayload {
  responder: string;
  proposerPokemonName: string;
  responderPokemonName: string;
  coinsOffered: number;
}

export const getTrades = async (leagueId: string): Promise<Trade[]> => {
  const { data } = await apiClient.get<Trade[]>(`/v1/leagues/${leagueId}/trades`);
  return data;
};

export const proposeTrade = async (
  leagueId: string,
  payload: ProposeTradePayload
): Promise<void> => {
  await apiClient.post(`/v1/leagues/${leagueId}/trades`, payload);
};

export const respondToTrade = async (
  leagueId: string,
  tradeId: string,
  accept: boolean
): Promise<void> => {
  await apiClient.post(`/v1/leagues/${leagueId}/trades/${tradeId}/respond`, { accept });
};

export const cancelTrade = async (leagueId: string, tradeId: string): Promise<void> => {
  await apiClient.delete(`/v1/leagues/${leagueId}/trades/${tradeId}`);
};
```

- [ ] **Step 3: Verificar TypeScript**

Run: `./node_modules/.bin/tsc --noEmit`
Expected: sin errores.

- [ ] **Step 4: Commit**

```bash
git add src/api/trades.ts
git commit -m "feat: add trades API client"
```

---

### Task 11: `ProposeTradeModal` (componente, skill frontend-design)

**Files:**
- Create: `src/components/ProposeTradeModal.tsx`

- [ ] **Step 1: Explorar componentes modales existentes**

Leer `src/components/CreateLeagueModal.tsx` y la sección de robo en `src/pages/TeamsPage.tsx` (buscar "steal") para el patrón de modal y de tokens CSS (`src/index.css`).

- [ ] **Step 2: Implementar el modal**

Props: `leagueId`, `responder` (username del rival), `responderPokemon` (`{ name, id }` del Pokémon objetivo), `myTeam` (lista de los Pokémon del usuario para elegir cuál entregar: `{ name, id }[]`), `onClose`.

Requisitos funcionales:
- Cabecera: "Recibes _responderPokemon.name_ de _responder_".
- Selector del Pokémon propio a entregar (de `myTeam`), con sprite desde el CDN `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/{id}.png`.
- Input numérico de monedas a ofrecer (mínimo 0, por defecto 0).
- Botón "Enviar propuesta" → `useMutation` que llama `proposeTrade(leagueId, payload)`.
- `onSuccess`: invalidar `['trades', leagueId]`, cerrar el modal.
- `onError`: mostrar el mensaje de error del backend (`error.response.data.message` o equivalente) dentro del modal.
- Diseño visual: aplicar la skill `frontend-design`, coherente con los tokens CSS y el resto de modales.

- [ ] **Step 3: Verificar TypeScript**

Run: `./node_modules/.bin/tsc --noEmit`
Expected: sin errores (recordar `import type` para interfaces puras).

- [ ] **Step 4: Commit**

```bash
git add src/components/ProposeTradeModal.tsx
git commit -m "feat: add ProposeTradeModal component"
```

---

### Task 12: `TradesModal` — bandeja de propuestas (componente, skill frontend-design)

**Files:**
- Create: `src/components/TradesModal.tsx`

- [ ] **Step 1: Implementar el modal de bandeja**

Props: `leagueId`, `currentUsername`, `onClose`.

Datos: `useQuery(['trades', leagueId], () => getTrades(leagueId))`.

Secciones:
- **Entrantes** — trades con `status === 'PENDING'` y `responder === currentUsername`: muestra `proposer`, los dos Pokémon (sprites), `coinsOffered`, botones **Aceptar** / **Rechazar** → `respondToTrade(leagueId, id, true|false)`.
- **Salientes** — `status === 'PENDING'` y `proposer === currentUsername`: muestra `responder`, los dos Pokémon, monedas, botón **Cancelar** → `cancelTrade(leagueId, id)`.
- **Histórico** — trades con `status !== 'PENDING'`: sección colapsada, secundaria; muestra el estado y `resolvedAt`.

Mutaciones — `onSuccess` de aceptar: invalidar `['trades', leagueId]`, `['draft', leagueId]`, `['my-coins', leagueId]`. `onSuccess` de rechazar/cancelar: invalidar `['trades', leagueId]`. `onError`: mostrar el mensaje del backend.

Diseño visual: aplicar la skill `frontend-design`.

- [ ] **Step 2: Verificar TypeScript**

Run: `./node_modules/.bin/tsc --noEmit`
Expected: sin errores.

- [ ] **Step 3: Commit**

```bash
git add src/components/TradesModal.tsx
git commit -m "feat: add TradesModal inbox component"
```

---

### Task 13: Integración en `TeamsPage`

**Files:**
- Modify: `src/pages/TeamsPage.tsx`

- [ ] **Step 1: Cablear los modales en TeamsPage**

- Al hacer clic en un Pokémon de un rival (mismo gesto que el robo): abrir `ProposeTradeModal` con el `responder`, el `responderPokemon` y `myTeam` (el equipo del usuario actual, derivado de `draft.picks` filtrando por el username).
- Botón en la cabecera de `TeamsPage` que abre `TradesModal`, con un **badge** mostrando el número de trades con `status === 'PENDING'` y `responder === currentUsername`. Para el badge, añadir `useQuery(['trades', leagueId], () => getTrades(leagueId))` en `TeamsPage` (React Query deduplica la query compartida con `TradesModal`).
- Polling moderado: en ese `useQuery`, fijar `refetchInterval` discreto (p. ej. 60000 ms) y `staleTime` acorde, respetando la restricción de Render free tier. Refetch al recuperar foco con los defaults de React Query.

- [ ] **Step 2: Verificar TypeScript**

Run: `./node_modules/.bin/tsc --noEmit`
Expected: sin errores.

- [ ] **Step 3: Commit**

```bash
git add src/pages/TeamsPage.tsx
git commit -m "feat: wire trade proposal and inbox into TeamsPage"
```

---

### Task 14: Verificación final + PR

- [ ] **Step 1: Verificación TypeScript completa**

Run: `./node_modules/.bin/tsc --noEmit`
Expected: sin errores.

- [ ] **Step 2: Verificación manual**

Levantar el frontend (`npm run dev`) contra el backend y comprobar el flujo: proponer un trade desde el Pokémon de un rival → aparece en salientes del proponente y en entrantes del rival (badge) → aceptar (los equipos y las monedas cambian) → rechazar y cancelar funcionan.

- [ ] **Step 3: Push + PR a `main`**

```bash
git push -u origin feature/player-trades
```

```powershell
$headers = @{ Authorization = "Bearer $env:GITHUB_TOKEN"; Accept = "application/vnd.github+json" }
$pr = @{
  title = "feat: player-to-player trades (frontend)"
  head  = "feature/player-trades"
  base  = "main"
  body  = "UI de intercambios entre jugadores: capa API, modal de propuesta, bandeja de trades con badge, integracion en TeamsPage."
} | ConvertTo-Json
Invoke-RestMethod -Uri "https://api.github.com/repos/Pokemons-Fantasy/pokefantasy-web/pulls" -Method Post -Headers $headers -Body $pr -ContentType "application/json; charset=utf-8"
```

---

## Cierre (tras mergear ambos PRs)

- Actualizar el roadmap de `CLAUDE.md`: mover "Player-to-player trades" de **Next** a **Done**.
- Actualizar `docs/DIAGRAMS.md`: añadir `TradeEntity` al diagrama de entidades y un nodo de trades al flujo de negocio.
