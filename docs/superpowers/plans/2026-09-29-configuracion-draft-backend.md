# Configuración del draft (backend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fase de preparación del draft (tiers editables, precio por tier, presupuesto por jugador, orden de turnos, snake) y picks con coste, saltando a quien no pueda pagar y pasando el sobrante al saldo de la liga.

**Architecture:** El draft en `PENDING` es la preparación: `DraftEntity.config` guarda presupuesto, precios y snake; los tiers siguen en `closed_list`. Las reglas de turno y presupuesto viven en un servicio sin repositorios (`DraftTurnService`) que usan el pick, el auto-pick, el arranque y la consulta de estado. El presupuesto restante se calcula desde `draftHistory` (cada pick guarda su `price`). Los drafts anteriores (`config == null`) siguen funcionando igual: gratis y lineales.

**Tech Stack:** Java 21, Spring Boot, Spring Data MongoDB (`MongoTemplate`), Lombok, JUnit 5 + Mockito + AssertJ, MockMvc standalone.

**Spec:** `C:\PokeFantasy\vault\50 Features\Configuración del draft.md` (diseño aprobado en brainstorming; en este repo no hay spec en `docs/superpowers/specs` por preferencia del usuario).

## Global Constraints

- Trabajar en el worktree `C:\PokeFantasy\wt\back-draft-config` (rama `feature/configuracion-draft`, base `origin/develop`). PR contra `develop`.
- CQRS obligatorio: `XCommand` (record que **implementa `Command`**) → `XCommandHandler` (`@Service`) → método en `DraftFacade` (un `mediator.send` por método) → controller que solo usa la facade.
- `domain` sin lógica de negocio: entidades, DTOs y enums con campos; la lógica en `application`.
- El back nuevo no rompe el front viejo: `POST draft/start` con `{turnOrder}` y sin draft preparado sigue creando el draft como hoy. Campos nuevos de respuesta opcionales.
- SSE solo desde el controller tras el commit (`realtimeNotifier.draftUpdated(leagueId)`), nunca desde handlers.
- Mensajes de error en español, sin em-dash. `IllegalArgumentException` → 400 `BAD_REQUEST`, `IllegalStateException` → 409 `CONFLICT`, `ForbiddenOperationException` → 403.
- Comandos Maven desde la raíz del worktree: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=X` para una clase; `./mvnw -B -ntp -f src/pom.xml clean verify` antes del PR (gate JaCoCo 80 % instrucciones y ramas).
- Commits con `git commit -F <archivo>` (UTF-8 sin BOM), mensaje en español, terminando en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Valores por defecto de `DraftConfig`: `budget 1000`, `priceS 200`, `priceA 150`, `priceB 100`, `priceC 60`, `priceD 30`, `snake false`.

## Review Focus

1. **Snake con saltos**: en snake, un jugador sin dinero en mitad de una ronda inversa se salta y el turno sigue hacia atrás; al final de una ronda el último jugador repite (A B C | C B A). Test en Task 2 (`advance_snake_*`).
2. **Pool agotado antes de acabar las rondas**: si no quedan Pokémon libres, el draft termina (`COMPLETED`), no se queda colgado. Test en Task 2 (`advance_noPokemonLeft_completes`).
3. **Precio exacto al saldo**: con 100 monedas restantes un Pokémon de 100 se puede elegir (`<=`), y el jugador queda con 0 y se le salta después. Tests en Task 2 (`canPick_exactBudget_true`) y Task 3 (`handle_priceEqualsRemaining_allowed`).
4. **Arranque con presupuesto imposible**: si nadie puede pagar ni el tier más barato, `start` falla con 400 y no guarda nada. Test en Task 7.
5. **Draft anterior en curso al desplegar**: un draft `IN_PROGRESS` sin `config` sigue gratis, lineal y termina por rondas. Tests existentes de `DraftPickCommandHandlerTest` deben seguir en verde sin tocar sus stubs (Task 3).

---

## File Structure

| Archivo | Responsabilidad |
|---|---|
| `src/domain/.../dto/DraftConfig.java` (nuevo) | Presupuesto, precios por tier y snake de un draft |
| `src/domain/.../dto/ActivityEventType.java` | Nuevo valor `DRAFT_COINS` |
| `src/domain/.../repository/entity/DraftEntity.java` | Campo `config` |
| `src/domain/.../repository/entity/DraftPick.java` | Campo `price` + constructor de 7 argumentos de compatibilidad |
| `src/domain/.../repository/DraftRepository.java` + `infrastructure/.../DraftRepositoryImpl.java` | `delete(DraftEntity)` |
| `src/domain/.../response/DraftStatusResponse.java`, `DraftPickResponse.java` | `config`, `budgets`, `price` |
| `src/domain/.../request/draft/UpdateDraftConfigRequest.java`, `SetDraftPoolTiersRequest.java` (nuevos) | Cuerpos de los endpoints nuevos |
| `src/application/.../commands/draft/DraftTurnService.java` (nuevo) | Precio, presupuesto restante, quién puede elegir, avance de turno (lineal/snake) |
| `src/application/.../commands/draft/TurnOrderPolicy.java` (nuevo) | Validar el orden de turnos contra los miembros (extraído de `StartDraftCommandHandler`) |
| `src/application/.../commands/draft/DraftSetupGuard.java` (nuevo) | Admin + draft en `PENDING` |
| `src/application/.../commands/draft/PrepareDraft*`, `UpdateDraftConfig*`, `SetDraftPoolTiers*`, `ResetDraftPoolTiers*` (nuevos) | Casos de uso de la preparación |
| `StartDraftCommandHandler`, `DraftPickCommandHandler`, `DraftTurnTimeoutService`, `CancelDraftCommandHandler`, `GetDraftStatusCommandHandler`, `DraftFacade` | Cambios de flujo |
| `commands/closedlist/NominatePokemonCommandHandler`, `DenominatePokemonCommandHandler`, `commands/league/UpdateLeagueSettingsCommandHandler` | Nominaciones cerradas con draft; no recalcular tiers en preparación |
| `src/api-rest/.../DraftController.java` | Endpoints nuevos, `start` con body opcional |
| `CLAUDE.md` | Dueños nuevos en la tabla §2 |

Rutas abreviadas: `.../` = `main/java/com/villu/pokefantasy/` dentro del módulo (`src/<módulo>/src/main/java/com/villu/pokefantasy/`). Tests en la ruta equivalente de `src/<módulo>/src/test/java/com/villu/pokefantasy/`.

---

### Task 1: Modelo (config, precio del pick, evento, borrado de draft, respuestas)

**Files:**
- Create: `src/domain/src/main/java/com/villu/pokefantasy/dto/DraftConfig.java`
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/dto/ActivityEventType.java`
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/entity/DraftEntity.java`
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/entity/DraftPick.java`
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/DraftRepository.java`
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/DraftRepositoryImpl.java`
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/response/DraftStatusResponse.java`
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/response/DraftPickResponse.java`
- Test: `src/infrastructure/src/test/java/com/villu/pokefantasy/repository/DraftRepositoryImplTest.java`

**Interfaces:**
- Produces: `DraftConfig` (Lombok `@Data @Builder(toBuilder = true) @NoArgsConstructor @AllArgsConstructor`, campos `Integer budget, priceS, priceA, priceB, priceC, priceD; Boolean snake`, `static DraftConfig defaults()`); `DraftEntity.getConfig()/setConfig(DraftConfig)`; `DraftPick.getPrice()/setPrice(Integer)` y constructor `DraftPick(String username, String pokemonName, Integer pokemonId, int round, Instant pickedAt, Integer customStealPrice, Instant lockedUntil)` (sin precio, `price = null`); `ActivityEventType.DRAFT_COINS`; `DraftRepository.delete(DraftEntity draft)`; `DraftStatusResponse.config (DraftConfig)`, `DraftStatusResponse.budgets (Map<String,Integer>)`; `DraftPickResponse.price (Integer)`.

- [ ] **Step 1: Test del borrado en el repositorio**

Añadir a `DraftRepositoryImplTest`:

```java
    @Test
    void delete_removesTheDraftDocument() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        DraftEntity draft = new DraftEntity();

        new DraftRepositoryImpl(mongoTemplate).delete(draft);

        verify(mongoTemplate).remove(draft);
    }
```

- [ ] **Step 2: Ejecutar y ver que no compila**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest=DraftRepositoryImplTest`
Expected: error de compilación `cannot find symbol: method delete(DraftEntity)`.

- [ ] **Step 3: Implementar el modelo**

`DraftConfig.java`:

```java
package com.villu.pokefantasy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Reglas económicas de un draft: presupuesto por jugador, precio de cada tier y orden snake. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DraftConfig {

    /** Monedas de cada jugador para este draft; lo que sobra pasa a su saldo de la liga al terminar. */
    private Integer budget;
    private Integer priceS;
    private Integer priceA;
    private Integer priceB;
    private Integer priceC;
    private Integer priceD;
    /** true = el orden se invierte en las rondas pares. */
    private Boolean snake;

    public static DraftConfig defaults() {
        return DraftConfig.builder()
                .budget(1000)
                .priceS(200).priceA(150).priceB(100).priceC(60).priceD(30)
                .snake(false)
                .build();
    }
}
```

`ActivityEventType.java`: añadir al final (poner coma tras `COIN_REVOKED`):

```java
    COIN_REVOKED,
    /** Presupuesto del draft que le sobró al jugador y pasó a su saldo; {@code coinsAmount} es lo sumado. */
    DRAFT_COINS
```

`DraftEntity.java`: añadir tras `currentTurnStartedAt` (e importar `com.villu.pokefantasy.dto.DraftConfig`):

```java
    /** Presupuesto, precios por tier y snake. null en drafts anteriores a la configuración: picks gratis y lineales. */
    private DraftConfig config;
```

`DraftPick.java`: añadir el campo y el constructor de compatibilidad (las 69 llamadas existentes usan 7 argumentos):

```java
    /** Monedas pagadas en el draft por este pick. null en drafts sin presupuesto y en fichajes fuera del draft. */
    private Integer price;

    /** Constructor de compatibilidad: todos los usos anteriores al presupuesto del draft (sin precio). */
    public DraftPick(String username, String pokemonName, Integer pokemonId, int round, Instant pickedAt,
                     Integer customStealPrice, Instant lockedUntil) {
        this(username, pokemonName, pokemonId, round, pickedAt, customStealPrice, lockedUntil, null);
    }
```

`DraftRepository.java`: añadir

```java
    /** Borra el draft (solo se usa con un draft en preparación: no tiene picks). */
    void delete(DraftEntity draft);
```

`DraftRepositoryImpl.java`: añadir

```java
    @Override
    public void delete(DraftEntity draft) {
        mongoTemplate.remove(draft);
    }
```

`DraftStatusResponse.java`: añadir (importar `DraftConfig` y `java.util.Map`):

```java
    /** Presupuesto, precios y snake. null en drafts anteriores a la configuración. */
    private DraftConfig config;
    /** Monedas que le quedan a cada jugador del orden de turnos. null si el draft no tiene presupuesto. */
    private Map<String, Integer> budgets;
```

`DraftPickResponse.java`: añadir

```java
    /** Monedas pagadas en el draft. null en drafts sin presupuesto. */
    private Integer price;
```

- [ ] **Step 4: Compilar todo y pasar los tests afectados**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest=DraftRepositoryImplTest`
Expected: PASS. Después `./mvnw -B -ntp -f src/pom.xml test -pl application -am` también en verde (nada cambia de comportamiento).

- [ ] **Step 5: Commit**

```bash
git add src/domain src/infrastructure
git commit -F msg.txt   # "feat(draft): modelo de la configuración del draft y precio de cada pick"
```

---

### Task 2: `DraftTurnService` (reglas de turno y presupuesto)

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/draft/DraftTurnService.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/draft/DraftTurnServiceTest.java`

**Interfaces:**
- Consumes: `DraftConfig`, `DraftPick.getPrice()`, `DraftEntity.getConfig()` (Task 1); `DraftEntity.ownedPokemonNames()`, `DraftEntity.teamSize(String)` (existentes).
- Produces (`@Service`, sin dependencias, constructor por defecto):
  - `int maxTeamSize(LeagueEntity league)`: `settings.maxTeamSize` si > 0, si no `10`.
  - `int priceOf(DraftEntity draft, ClosedListEntity entry)`: 0 sin config o sin tier.
  - `Integer remainingBudget(DraftEntity draft, String username)`: `budget - Σ price` de `draftHistory`; `null` sin config.
  - `List<ClosedListEntity> available(DraftEntity draft, List<ClosedListEntity> pool)`: pool sin los Pokémon con dueño.
  - `boolean canPick(DraftEntity draft, String username, List<ClosedListEntity> available, int maxTeamSize)`.
  - `void advance(DraftEntity draft, List<ClosedListEntity> available, int maxTeamSize)`: mueve `currentTurnIndex`/`currentRound` al siguiente que pueda elegir o pone `COMPLETED`.
  - `void placeFirstTurn(DraftEntity draft, List<ClosedListEntity> available, int maxTeamSize)`.

- [ ] **Step 1: Escribir los tests**

```java
package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DraftTurnServiceTest {

    private final DraftTurnService service = new DraftTurnService();

    private static final DraftConfig CONFIG = DraftConfig.builder()
            .budget(300).priceS(200).priceA(150).priceB(100).priceC(60).priceD(30).snake(false).build();

    private static ClosedListEntity entry(String name, Tier tier) {
        ClosedListEntity e = new ClosedListEntity();
        e.setPokemonName(name);
        e.setTier(tier);
        return e;
    }

    private static DraftPick pick(String user, String name, Integer price) {
        DraftPick p = new DraftPick(user, name, 1, 1, Instant.now(), null, null);
        p.setPrice(price);
        return p;
    }

    private static DraftEntity draft(DraftConfig config, List<String> order, List<DraftPick> history) {
        DraftEntity d = new DraftEntity();
        d.setStatus(DraftStatus.IN_PROGRESS);
        d.setConfig(config);
        d.setTurnOrder(order);
        d.setCurrentTurnIndex(0);
        d.setCurrentRound(1);
        d.setPicks(new ArrayList<>(history));
        d.setDraftHistory(new ArrayList<>(history));
        return d;
    }

    @Test
    void maxTeamSize_usesSettingsOrDefaultTen() {
        LeagueEntity league = new LeagueEntity();
        assertThat(service.maxTeamSize(null)).isEqualTo(10);
        assertThat(service.maxTeamSize(league)).isEqualTo(10);
        league.setSettings(LeagueSettings.builder().maxTeamSize(0).build());
        assertThat(service.maxTeamSize(league)).isEqualTo(10);
        league.setSettings(LeagueSettings.builder().maxTeamSize(12).build());
        assertThat(service.maxTeamSize(league)).isEqualTo(12);
    }

    @Test
    void priceOf_usesTierPrice_zeroWithoutConfigOrTier() {
        DraftEntity withConfig = draft(CONFIG, List.of("ash"), List.of());
        assertThat(service.priceOf(withConfig, entry("mew", Tier.S))).isEqualTo(200);
        assertThat(service.priceOf(withConfig, entry("abra", Tier.D))).isEqualTo(30);
        assertThat(service.priceOf(withConfig, entry("ditto", null))).isZero();
        assertThat(service.priceOf(draft(null, List.of("ash"), List.of()), entry("mew", Tier.S))).isZero();
    }

    @Test
    void remainingBudget_subtractsHistoryPricesOfThatPlayer() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty"),
                List.of(pick("ash", "mew", 200), pick("misty", "abra", 30), pick("ash", "old", null)));
        assertThat(service.remainingBudget(d, "ash")).isEqualTo(100);
        assertThat(service.remainingBudget(d, "misty")).isEqualTo(270);
        assertThat(service.remainingBudget(d, "brock")).isEqualTo(300);
    }

    @Test
    void remainingBudget_nullWithoutConfig() {
        assertThat(service.remainingBudget(draft(null, List.of("ash"), List.of()), "ash")).isNull();
    }

    @Test
    void available_excludesOwnedPokemonIgnoringCase() {
        DraftEntity d = draft(CONFIG, List.of("ash"), List.of(pick("ash", "Mew", 200)));
        List<ClosedListEntity> pool = List.of(entry("mew", Tier.S), entry("abra", Tier.D));
        assertThat(service.available(d, pool)).extracting(ClosedListEntity::getPokemonName).containsExactly("abra");
    }

    @Test
    void canPick_exactBudget_true() {
        DraftEntity d = draft(CONFIG, List.of("ash"), List.of(pick("ash", "mew", 200)));
        assertThat(service.canPick(d, "ash", List.of(entry("bulbasaur", Tier.B)), 10)).isTrue();
    }

    @Test
    void canPick_cannotAffordAnything_false() {
        DraftEntity d = draft(CONFIG, List.of("ash"), List.of(pick("ash", "mew", 200), pick("ash", "abra", 30)));
        assertThat(service.canPick(d, "ash", List.of(entry("bulbasaur", Tier.B)), 10)).isFalse();
    }

    @Test
    void canPick_teamFull_false() {
        DraftEntity d = draft(CONFIG, List.of("ash"), List.of(pick("ash", "abra", 30)));
        assertThat(service.canPick(d, "ash", List.of(entry("kadabra", Tier.D)), 1)).isFalse();
    }

    @Test
    void canPick_withoutConfig_onlyTeamSizeMatters() {
        DraftEntity d = draft(null, List.of("ash"), List.of());
        assertThat(service.canPick(d, "ash", List.of(), 10)).isTrue();
    }

    @Test
    void advance_linear_goesToNextPlayer() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty", "brock"), List.of());
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(1);
        assertThat(d.getCurrentRound()).isEqualTo(1);
    }

    @Test
    void advance_linear_wrapsToFirstPlayerOfNextRound() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty"), List.of());
        d.setCurrentTurnIndex(1);
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isZero();
        assertThat(d.getCurrentRound()).isEqualTo(2);
    }

    @Test
    void advance_skipsPlayerWhoCannotPay() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty", "brock"),
                List.of(pick("misty", "mew", 200), pick("misty", "mewtwo", 90)));
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(2); // misty (10 monedas) se salta
    }

    @Test
    void advance_snake_lastPlayerRepeatsAndRoundGoesBackwards() {
        DraftEntity d = draft(CONFIG.toBuilder().snake(true).build(), List.of("ash", "misty", "brock"), List.of());
        d.setCurrentTurnIndex(2);
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentRound()).isEqualTo(2);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(2);

        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(1);
    }

    @Test
    void advance_snake_skipsBackwardsAndTurnsAtTheStart() {
        DraftEntity d = draft(CONFIG.toBuilder().snake(true).build(), List.of("ash", "misty", "brock"),
                List.of(pick("misty", "mew", 280)));
        d.setCurrentRound(2);
        d.setCurrentTurnIndex(2);
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentTurnIndex()).isZero(); // misty (20 monedas) se salta hacia atrás
        assertThat(d.getCurrentRound()).isEqualTo(2);

        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getCurrentRound()).isEqualTo(3); // ash repite al empezar la ronda impar
        assertThat(d.getCurrentTurnIndex()).isZero();
    }

    @Test
    void advance_nobodyCanPay_completes() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty"),
                List.of(pick("ash", "mew", 290), pick("misty", "mewtwo", 290)));
        service.advance(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }

    @Test
    void advance_noPokemonLeft_completes() {
        DraftEntity d = draft(CONFIG, List.of("ash", "misty"), List.of());
        service.advance(d, List.of(), 10);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }

    @Test
    void advance_pastLastRound_completes() {
        DraftEntity d = draft(null, List.of("ash", "misty"), List.of());
        d.setCurrentRound(10);
        d.setCurrentTurnIndex(1);
        service.advance(d, List.of(), 10);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }

    @Test
    void placeFirstTurn_startsWithFirstPlayerWhoCanPay() {
        DraftEntity d = draft(CONFIG.toBuilder().budget(50).build(), List.of("ash", "misty"),
                List.of(pick("ash", "abra", 30)));
        d.setCurrentTurnIndex(1);
        d.setCurrentRound(4);
        service.placeFirstTurn(d, List.of(entry("kadabra", Tier.D)), 10);
        assertThat(d.getCurrentRound()).isEqualTo(1);
        assertThat(d.getCurrentTurnIndex()).isEqualTo(1); // ash tiene 20 y lo más barato cuesta 30
    }

    @Test
    void placeFirstTurn_nobodyCanPay_completes() {
        DraftEntity d = draft(CONFIG.toBuilder().budget(10).build(), List.of("ash"), List.of());
        service.placeFirstTurn(d, List.of(entry("abra", Tier.D)), 10);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }
}
```


- [ ] **Step 2: Ejecutar y ver que falla**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=DraftTurnServiceTest`
Expected: error de compilación `cannot find symbol: class DraftTurnService`.

- [ ] **Step 3: Implementar**

```java
package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Reglas de turno y presupuesto del draft: precio de cada Pokémon, monedas que le quedan a cada jugador,
 * quién puede elegir y a quién le toca. No usa repositorios: quien llama le pasa el draft y el pool.
 * Un draft sin {@code config} (anterior a la configuración) es gratis y lineal.
 */
@Service
public class DraftTurnService {

    static final int DEFAULT_MAX_TEAM_SIZE = 10;

    /** Rondas del draft y tope de equipo: {@code maxTeamSize} de la liga, o 10 si no está configurado. */
    public int maxTeamSize(LeagueEntity league) {
        if (league != null && league.getSettings() != null) {
            Integer max = league.getSettings().getMaxTeamSize();
            if (max != null && max > 0) return max;
        }
        return DEFAULT_MAX_TEAM_SIZE;
    }

    public int priceOf(DraftEntity draft, ClosedListEntity entry) {
        DraftConfig config = draft.getConfig();
        if (config == null || entry.getTier() == null) return 0;
        Integer price = switch (entry.getTier()) {
            case S -> config.getPriceS();
            case A -> config.getPriceA();
            case B -> config.getPriceB();
            case C -> config.getPriceC();
            case D -> config.getPriceD();
        };
        return price == null ? 0 : price;
    }

    /** Monedas que le quedan a {@code username}; null si el draft no tiene presupuesto. */
    public Integer remainingBudget(DraftEntity draft, String username) {
        DraftConfig config = draft.getConfig();
        if (config == null || config.getBudget() == null) return null;
        int spent = draft.getDraftHistory() == null ? 0 : draft.getDraftHistory().stream()
                .filter(p -> username.equals(p.getUsername()) && p.getPrice() != null)
                .mapToInt(DraftPick::getPrice)
                .sum();
        return config.getBudget() - spent;
    }

    /** Pokémon del pool que aún no tienen dueño en el draft. */
    public List<ClosedListEntity> available(DraftEntity draft, List<ClosedListEntity> pool) {
        Set<String> owned = draft.ownedPokemonNames();
        return pool.stream().filter(e -> !owned.contains(e.getPokemonName().toLowerCase())).toList();
    }

    /** Puede elegir quien no tiene el equipo lleno y puede pagar al menos un Pokémon libre. */
    public boolean canPick(DraftEntity draft, String username, List<ClosedListEntity> available, int maxTeamSize) {
        if (draft.teamSize(username) >= maxTeamSize) return false;
        Integer remaining = remainingBudget(draft, username);
        if (remaining == null) return true;
        return available.stream().anyMatch(e -> priceOf(draft, e) <= remaining);
    }

    /**
     * Pasa el turno al siguiente jugador que pueda elegir (en snake, las rondas pares van hacia atrás).
     * A quien no puede elegir se le salta. Si nadie puede, o se pasa de {@code maxTeamSize} rondas,
     * el draft queda COMPLETED. Poder elegir solo empeora con el tiempo (menos dinero, menos pool, más
     * equipo), así que recorrer dos vueltas de posiciones basta para encontrar a alguien.
     */
    public void advance(DraftEntity draft, List<ClosedListEntity> available, int maxTeamSize) {
        int players = draft.getTurnOrder().size();
        int index = draft.getCurrentTurnIndex();
        int round = draft.getCurrentRound();
        for (int step = 0; step < 2 * players; step++) {
            boolean forward = isForward(draft, round);
            if (forward ? index + 1 < players : index > 0) {
                index += forward ? 1 : -1;
            } else {
                round++;
                if (round > maxTeamSize) break;
                index = isForward(draft, round) ? 0 : players - 1;
            }
            if (canPick(draft, draft.getTurnOrder().get(index), available, maxTeamSize)) {
                draft.setCurrentTurnIndex(index);
                draft.setCurrentRound(round);
                return;
            }
        }
        draft.setStatus(DraftStatus.COMPLETED);
    }

    /** Primer turno del draft: el primero del orden, o el siguiente que pueda elegir. */
    public void placeFirstTurn(DraftEntity draft, List<ClosedListEntity> available, int maxTeamSize) {
        draft.setCurrentRound(1);
        draft.setCurrentTurnIndex(0);
        if (!canPick(draft, draft.getTurnOrder().get(0), available, maxTeamSize)) {
            advance(draft, available, maxTeamSize);
        }
    }

    private static boolean isForward(DraftEntity draft, int round) {
        boolean snake = draft.getConfig() != null && Boolean.TRUE.equals(draft.getConfig().getSnake());
        return !snake || round % 2 == 1;
    }
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=DraftTurnServiceTest`
Expected: PASS (18 tests).

- [ ] **Step 5: Commit** — `feat(draft): reglas de turno y presupuesto del draft`

---

### Task 3: Pick con precio, saltos y sobrante al saldo

**Files:**
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/draft/DraftPickCommandHandler.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/draft/DraftPickCommandHandlerTest.java`

**Interfaces:**
- Consumes: `DraftTurnService` (Task 2), `ActivityEventType.DRAFT_COINS`, `DraftPick.setPrice` (Task 1), `ActivityEventRepository.save(ActivityEventEntity)` (existente).
- Produces: constructor `DraftPickCommandHandler(DraftRepository, ClosedListRepository, UserRepository, LeagueRepository, ScheduleRepository, DraftTurnNotifier, DraftTurnService, ActivityEventRepository)`. `DraftTurnTimeoutService` sigue inyectándolo directamente (ADR-005).

- [ ] **Step 1: Adaptar el `setUp` y escribir los tests nuevos**

En `DraftPickCommandHandlerTest`: añadir `@Mock private ActivityEventRepository activityEventRepository;` y cambiar el `setUp`:

```java
        handler = new DraftPickCommandHandler(draftRepository, closedListRepository, userRepository,
                leagueRepository, scheduleRepository, draftTurnNotifier, new DraftTurnService(),
                activityEventRepository);
```

Añadir estos helpers y tests (imports: `DraftConfig`, `Tier`, `ActivityEventType`, `ActivityEventRepository`, `ActivityEventEntity`, `LeagueMember`, `LeagueRole`):

```java
    private static final DraftConfig CONFIG = DraftConfig.builder()
            .budget(300).priceS(200).priceA(150).priceB(100).priceC(60).priceD(30).snake(false).build();

    private ClosedListEntity tiered(String name, int id, Tier tier) {
        ClosedListEntity entry = closedListEntry(name, id);
        entry.setTier(tier);
        return entry;
    }

    private static DraftPick paid(String user, String name, int price) {
        DraftPick p = new DraftPick(user, name, 1, 1, Instant.now(), null, null);
        p.setPrice(price);
        return p;
    }

    private DraftEntity budgetDraft(List<String> order, List<DraftPick> history) {
        DraftEntity draft = activeDraft(order, 0, 1, new ArrayList<>(history));
        draft.setDraftHistory(new ArrayList<>(history));
        draft.setConfig(CONFIG);
        return draft;
    }

    private LeagueEntity leagueOf(String... players) {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setSettings(LeagueSettings.builder().maxTeamSize(10).build());
        List<LeagueMember> members = new ArrayList<>();
        for (String p : players) members.add(new LeagueMember(p, LeagueRole.USER, 0));
        league.setMembers(members);
        return league;
    }

    @Test
    void handle_cannotAfford_throwsAndSavesNothing() {
        DraftEntity draft = budgetDraft(List.of(USERNAME, "brock"), List.of(paid(USERNAME, "mew", 200)));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(new UserEntity());
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueOf(USERNAME, "brock")));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(tiered(POKEMON, 25, Tier.A)));

        assertThatThrownBy(() -> handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No te llega: pikachu cuesta 150 y te quedan 100");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void handle_priceEqualsRemaining_allowedAndPriceRecorded() {
        DraftEntity draft = budgetDraft(List.of(USERNAME, "brock"), List.of(paid(USERNAME, "mew", 200)));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(new UserEntity());
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueOf(USERNAME, "brock")));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(tiered(POKEMON, 25, Tier.B)));
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID))
                .thenReturn(List.of(tiered(POKEMON, 25, Tier.B), tiered("abra", 63, Tier.D)));

        handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID));

        ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(captor.capture());
        DraftEntity saved = captor.getValue();
        assertThat(saved.getDraftHistory()).last().extracting(DraftPick::getPrice).isEqualTo(100);
        assertThat(saved.getPicks()).last().extracting(DraftPick::getPrice).isEqualTo(100);
        assertThat(saved.getCurrentTurnIndex()).isEqualTo(1); // brock
    }

    @Test
    void handle_nextPlayerCannotPay_isSkipped() {
        DraftEntity draft = budgetDraft(List.of(USERNAME, "brock", "misty"), List.of(paid("brock", "mew", 290)));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(new UserEntity());
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueOf(USERNAME, "brock", "misty")));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(tiered(POKEMON, 25, Tier.D)));
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID))
                .thenReturn(List.of(tiered(POKEMON, 25, Tier.D), tiered("abra", 63, Tier.D)));

        handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID));

        ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentTurnIndex()).isEqualTo(2); // brock (10 monedas) se salta
    }

    @Test
    void handle_lastPossiblePick_completesAndPaysLeftoverBudgets() {
        DraftEntity draft = budgetDraft(List.of(USERNAME, "brock"), List.of(paid("brock", "mew", 200)));
        LeagueEntity league = leagueOf(USERNAME, "brock");
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(new UserEntity());
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(tiered(POKEMON, 25, Tier.C)));
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(List.of(tiered(POKEMON, 25, Tier.C)));

        handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID));

        ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DraftStatus.COMPLETED);
        assertThat(league.getMembers()).extracting(LeagueMember::getCoinBalance).containsExactly(240, 100);
        verify(leagueRepository).save(league);
        ArgumentCaptor<ActivityEventEntity> events = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository, times(2)).save(events.capture());
        assertThat(events.getAllValues()).allMatch(e -> e.getType() == ActivityEventType.DRAFT_COINS);
        assertThat(events.getAllValues()).extracting(ActivityEventEntity::getActorUsername, ActivityEventEntity::getCoinsAmount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(USERNAME, 240), org.assertj.core.groups.Tuple.tuple("brock", 100));
        verify(scheduleRepository).save(any(ScheduleEntity.class));
    }
```

Los tests existentes no se tocan: sin `config`, el handler no pide el pool (`findAllByLeagueId`) y no paga sobrantes, así que sus stubs siguen valiendo (Review Focus 5).

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=DraftPickCommandHandlerTest`
Expected: error de compilación por el constructor de 8 argumentos.

- [ ] **Step 3: Implementar**

Cambios en `DraftPickCommandHandler`:
1. Quitar `DEFAULT_MAX_TEAM_SIZE`, `resolveMaxTeamSize` y `advanceTurn`; inyectar `DraftTurnService draftTurnService` y `ActivityEventRepository activityEventRepository` (constructor de la sección Interfaces).
2. `int maxTeamSize = draftTurnService.maxTeamSize(league);`
3. Tras obtener `entry`, sustituir la creación del pick y del historial y el avance por:

```java
        int price = draftTurnService.priceOf(draft, entry);
        Integer remaining = draftTurnService.remainingBudget(draft, username);
        if (remaining != null && price > remaining) {
            throw new IllegalArgumentException("No te llega: " + entry.getPokemonName() + " cuesta " + price
                    + " y te quedan " + remaining);
        }
        Integer paid = draft.getConfig() == null ? null : price;

        DraftPick pick = new DraftPick(username, entry.getPokemonName(),
                entry.getPokemonId(), draft.getCurrentRound(), Instant.now(), null, null);
        pick.setPrice(paid);
        draft.getPicks().add(pick);

        // Registro inmutable del draft original: una copia que robos/swaps/trades nunca tocan.
        if (draft.getDraftHistory() == null) {
            draft.setDraftHistory(new ArrayList<>());
        }
        DraftPick historyPick = new DraftPick(pick.getUsername(), pick.getPokemonName(),
                pick.getPokemonId(), pick.getRound(), pick.getPickedAt(),
                pick.getCustomStealPrice(), pick.getLockedUntil());
        historyPick.setPrice(paid);
        draft.getDraftHistory().add(historyPick);

        // Sin presupuesto (drafts anteriores) solo cuenta el tamaño del equipo: no hace falta el pool.
        List<ClosedListEntity> available = draft.getConfig() == null ? List.of()
                : draftTurnService.available(draft, closedListRepository.findAllByLeagueId(leagueId));
        draftTurnService.advance(draft, available, maxTeamSize);
        draft.setCurrentTurnStartedAt(Instant.now());
        draftRepository.save(draft);

        if (draft.getStatus() == DraftStatus.COMPLETED) {
            boolean settingsCreated = initLeagueSettingsIfNeeded(league);
            boolean leftoverPaid = payLeftoverBudgets(league, draft);
            if (settingsCreated || leftoverPaid) {
                leagueRepository.save(league);
            }
            generateLeagueSchedule(leagueId, draft.getTurnOrder());
        } else {
            draftTurnNotifier.notifyCurrentTurn(draft, league);
        }
        return null;
```

4. `initLeagueSettingsIfNeeded` deja de guardar y devuelve si ha creado los ajustes:

```java
    private boolean initLeagueSettingsIfNeeded(LeagueEntity league) {
        if (league != null && league.getSettings() == null) {
            league.setSettings(LeagueSettings.defaults());
            return true;
        }
        return false;
    }

    /** Lo que le sobra a cada jugador del presupuesto del draft pasa a su saldo de la liga. */
    private boolean payLeftoverBudgets(LeagueEntity league, DraftEntity draft) {
        if (league == null || draft.getConfig() == null) return false;
        Instant now = Instant.now();
        boolean paid = false;
        for (LeagueMember member : league.getMembers()) {
            if (!draft.getTurnOrder().contains(member.getUsername())) continue;
            Integer leftover = draftTurnService.remainingBudget(draft, member.getUsername());
            if (leftover == null || leftover <= 0) continue;
            member.setCoinBalance(member.getCoinBalance() + leftover);
            activityEventRepository.save(ActivityEventEntity.builder()
                    .leagueId(league.getId())
                    .type(ActivityEventType.DRAFT_COINS)
                    .actorUsername(member.getUsername())
                    .coinsAmount(leftover)
                    .createdAt(now)
                    .build());
            paid = true;
        }
        return paid;
    }
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest='DraftPickCommandHandlerTest,AutoPickDraftCommandHandlerTest'`
Expected: `DraftPickCommandHandlerTest` PASS. `AutoPickDraftCommandHandlerTest` puede fallar por el constructor si crea un `DraftPickCommandHandler` real: se arregla en Task 4.

- [ ] **Step 5: Commit** — `feat(draft): los picks cuestan monedas y el sobrante pasa al saldo`

---

### Task 4: Auto-pick solo entre lo que el jugador puede pagar

**Files:**
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/draft/DraftTurnTimeoutService.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/draft/AutoPickDraftCommandHandlerTest.java` (y cualquier otro `new DraftTurnTimeoutService(`: `git grep -n "new DraftTurnTimeoutService("`)

**Interfaces:**
- Consumes: `DraftTurnService.available/priceOf/remainingBudget` (Task 2).
- Produces: constructor `DraftTurnTimeoutService(DraftRepository, ClosedListRepository, DraftPickCommandHandler, DraftTurnService)`.

- [ ] **Step 1: Test**

En `AutoPickDraftCommandHandlerTest`, pasar `new DraftTurnService()` como cuarto argumento del constructor y añadir:

```java
    @Test
    void handle_budgetDraft_picksOnlyAffordablePokemon() throws Exception {
        DraftEntity draft = inProgressDraft(Instant.now().minusSeconds(120));
        draft.setConfig(DraftConfig.builder().budget(50).priceS(200).priceA(150).priceB(100).priceC(60).priceD(30)
                .snake(false).build());
        LeagueEntity league = leagueWithTimer(60);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        ClosedListEntity mew = closedListEntry("mew");
        mew.setTier(Tier.S);
        ClosedListEntity abra = closedListEntry("abra");
        abra.setTier(Tier.D);
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(List.of(mew, abra));

        handler.handle(new AutoPickDraftCommand(LEAGUE_ID, "ash"));

        verify(draftPickCommandHandler).handle(new DraftPickCommand("ash", "abra", LEAGUE_ID));
    }
```

(Adaptar nombres de mocks/campos a los del test si difieren: el test ya tiene `leagueRepository`, `draftRepository`, `closedListRepository` y `draftPickCommandHandler` mockeados.)

- [ ] **Step 2: Ejecutar y ver que falla** — `-Dtest=AutoPickDraftCommandHandlerTest`, error de compilación del constructor.

- [ ] **Step 3: Implementar**

Inyectar `DraftTurnService draftTurnService` y sustituir el bloque que construye `pickedNames`/`available` por:

```java
        List<ClosedListEntity> available = draftTurnService.available(draft,
                closedListRepository.findAllByLeagueId(leagueId));
        Integer remaining = draftTurnService.remainingBudget(draft, currentPlayer);
        List<ClosedListEntity> affordable = available.stream()
                .filter(e -> remaining == null || draftTurnService.priceOf(draft, e) <= remaining)
                .toList();

        if (affordable.isEmpty()) {
            throw new IllegalStateException("No quedan Pokémon para elegir automáticamente");
        }

        String randomPokemon = affordable.get(random.nextInt(affordable.size())).getPokemonName();
```

Quitar los imports que queden sin uso (`DraftPick`, `Set`, `Collectors`).

- [ ] **Step 4: Ejecutar** — `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest='AutoPickDraftCommandHandlerTest,ExpireDraftTurnCommandHandlerTest'` → PASS.

- [ ] **Step 5: Commit** — `feat(draft): el auto-pick solo elige lo que el jugador puede pagar`

---

### Task 5: Preparar el draft (`TurnOrderPolicy`, `DraftSetupGuard`, `PrepareDraftCommand`)

**Files:**
- Create: `.../commands/draft/TurnOrderPolicy.java`, `DraftSetupGuard.java`, `PrepareDraftCommand.java`, `PrepareDraftCommandHandler.java` (módulo `application`)
- Modify: `.../commands/draft/StartDraftCommandHandler.java` (usar `TurnOrderPolicy`), `DraftFacade.java`
- Test: `TurnOrderPolicyTest.java`, `DraftSetupGuardTest.java`, `PrepareDraftCommandHandlerTest.java`, `DraftFacadeTest.java`, `StartDraftCommandHandlerTest.java` (solo el `setUp`)

**Interfaces:**
- Produces:
  - `TurnOrderPolicy` (`@Service`, sin dependencias): `List<String> canonical(List<String> turnOrder, LeagueEntity league)` con los mensajes actuales ("El orden de turnos debe tener al menos un jugador", "El orden de turnos tiene un jugador sin nombre", "El orden de turnos tiene jugadores repetidos", "No son miembros de la liga: …", "Faltan en el orden de turnos: …").
  - `DraftSetupGuard(LeagueAdminGuard, DraftRepository)`: `DraftSetup requireDraftInSetup(String leagueId, String username)`; `public record DraftSetup(LeagueEntity league, DraftEntity draft) {}` anidado. Error: `IllegalStateException("El draft no se está preparando")`.
  - `PrepareDraftCommand(String leagueId, String requestingUsername) implements Command`; handler devuelve `Void`.
  - `DraftFacade.prepareDraft(String leagueId, String requestingUsername)`.

- [ ] **Step 1: Tests**

`TurnOrderPolicyTest`: mover aquí la lógica probada hoy en `StartDraftCommandHandlerTest` (sin mocks):

```java
class TurnOrderPolicyTest {

    private final TurnOrderPolicy policy = new TurnOrderPolicy();

    private static LeagueEntity league(String... members) {
        LeagueEntity league = new LeagueEntity();
        List<LeagueMember> list = new ArrayList<>();
        for (String m : members) list.add(new LeagueMember(m, LeagueRole.USER, 0));
        league.setMembers(list);
        return league;
    }

    @Test
    void canonical_usesLeagueNamesAndTrims() {
        assertThat(policy.canonical(List.of(" brock ", "ASH"), league("ash", "Brock"))).containsExactly("Brock", "ash");
    }

    @Test
    void canonical_rejectsEmptyBlankDuplicatesStrangersAndMissing() {
        assertThatThrownBy(() -> policy.canonical(null, league("ash"))).hasMessageContaining("al menos un jugador");
        assertThatThrownBy(() -> policy.canonical(List.of("ash", " "), league("ash"))).hasMessageContaining("sin nombre");
        assertThatThrownBy(() -> policy.canonical(List.of("ash", "ASH"), league("ash"))).hasMessageContaining("repetidos");
        assertThatThrownBy(() -> policy.canonical(List.of("ash", "gary"), league("ash"))).hasMessage("No son miembros de la liga: gary");
        assertThatThrownBy(() -> policy.canonical(List.of("ash"), league("ash", "misty"))).hasMessage("Faltan en el orden de turnos: misty");
    }
}
```

`DraftSetupGuardTest` (Mockito): admin + draft `PENDING` → devuelve ambos; draft `IN_PROGRESS` o sin draft → `IllegalStateException("El draft no se está preparando")`.

```java
@ExtendWith(MockitoExtension.class)
class DraftSetupGuardTest {
    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private DraftRepository draftRepository;

    @Test
    void requireDraftInSetup_pendingDraft_returnsLeagueAndDraft() {
        LeagueEntity league = new LeagueEntity();
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.PENDING);
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        DraftSetupGuard.DraftSetup setup = new DraftSetupGuard(leagueAdminGuard, draftRepository).requireDraftInSetup("l1", "ash");

        assertThat(setup.league()).isSameAs(league);
        assertThat(setup.draft()).isSameAs(draft);
    }

    @Test
    void requireDraftInSetup_draftInProgress_throws() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> new DraftSetupGuard(leagueAdminGuard, draftRepository).requireDraftInSetup("l1", "ash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El draft no se está preparando");
    }
}
```

`PrepareDraftCommandHandlerTest`:

```java
@ExtendWith(MockitoExtension.class)
class PrepareDraftCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private TierAssignmentService tierAssignmentService;

    private PrepareDraftCommandHandler handler;
    private final LeagueEntity league = new LeagueEntity();

    @BeforeEach
    void setUp() {
        handler = new PrepareDraftCommandHandler(draftRepository, leagueAdminGuard, closedListRepository,
                tierAssignmentService);
        league.setMembers(new ArrayList<>(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.USER, 0))));
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);
    }

    private static DraftEntity draftWith(DraftStatus status) {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(status);
        return draft;
    }

    @Test
    void handle_noDraft_createsPendingDraftWithDefaultsAndAssignsTiers() {
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(new ClosedListEntity()));

        handler.handle(new PrepareDraftCommand("l1", "ash"));

        ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(captor.capture());
        DraftEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(DraftStatus.PENDING);
        assertThat(saved.getLeagueId()).isEqualTo("l1");
        assertThat(saved.getTurnOrder()).containsExactly("ash", "misty");
        assertThat(saved.getConfig()).isEqualTo(DraftConfig.defaults());
        assertThat(saved.getPicks()).isEmpty();
        assertThat(saved.getDraftHistory()).isEmpty();
        assertThat(saved.getCurrentRound()).isEqualTo(1);
        verify(tierAssignmentService).assignTiersToPool(eq("l1"), any(LeagueSettings.class));
    }

    @Test
    void handle_afterCancelledDraft_allowed() {
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draftWith(DraftStatus.CANCELLED)));
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(new ClosedListEntity()));

        handler.handle(new PrepareDraftCommand("l1", "ash"));

        verify(draftRepository).save(any(DraftEntity.class));
    }

    @ParameterizedTest
    @EnumSource(value = DraftStatus.class, names = {"PENDING", "IN_PROGRESS", "COMPLETED"})
    void handle_latestDraftNotCancelled_throws(DraftStatus status) {
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draftWith(status)));

        assertThatThrownBy(() -> handler.handle(new PrepareDraftCommand("l1", "ash")))
                .isInstanceOf(IllegalStateException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void handle_emptyPool_throws() {
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of());

        assertThatThrownBy(() -> handler.handle(new PrepareDraftCommand("l1", "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El pool está vacío: nominad Pokémon antes de preparar el draft");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(PrepareDraftCommand.class);
    }
}
```

En `StartDraftCommandHandlerTest.setUp` pasar `new TurnOrderPolicy()` (ver constructor en Task 7; en esta task solo se añade el parámetro `TurnOrderPolicy` al final).

En `DraftFacadeTest`, añadir:

```java
    @Test
    void prepareDraft_sendsPrepareDraftCommand() throws Exception {
        facade.prepareDraft("l1", "ash");
        verify(mediator).send(new PrepareDraftCommand("l1", "ash"));
    }
```

- [ ] **Step 2: Ejecutar y ver que falla** — `-Dtest='TurnOrderPolicyTest,DraftSetupGuardTest,PrepareDraftCommandHandlerTest,DraftFacadeTest'`, errores de compilación.

- [ ] **Step 3: Implementar**

`TurnOrderPolicy`: mover el bucle de saneado y `matchLeagueMembers` desde `StartDraftCommandHandler` tal cual, dentro de:

```java
/** Orden de turnos válido: exactamente los miembros de la liga, sin repetidos, con sus nombres tal como están. */
@Service
public class TurnOrderPolicy {

    public List<String> canonical(List<String> turnOrder, LeagueEntity league) {
        if (turnOrder == null || turnOrder.isEmpty()) {
            throw new IllegalArgumentException("El orden de turnos debe tener al menos un jugador");
        }
        List<String> sanitized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String username : turnOrder) {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("El orden de turnos tiene un jugador sin nombre");
            }
            if (!seen.add(username.trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("El orden de turnos tiene jugadores repetidos");
            }
            sanitized.add(username.trim());
        }
        return matchLeagueMembers(sanitized, league);
    }

    // matchLeagueMembers: copiar sin cambios desde StartDraftCommandHandler (javadoc incluido)
}
```

`StartDraftCommandHandler`: inyectar `TurnOrderPolicy` (último parámetro del constructor), borrar el bucle y `matchLeagueMembers`, y usar `draft.setTurnOrder(turnOrderPolicy.canonical(command.turnOrder(), league))`. Mantener el `if (command == null ...)` inicial tal cual para que los tests de validación sigan igual. (Task 7 reordena este handler.)

`DraftSetupGuard`:

```java
/** Casos de uso de la preparación del draft: solo el admin y solo con el draft en PENDING. */
@Service
public class DraftSetupGuard {

    public record DraftSetup(LeagueEntity league, DraftEntity draft) {}

    private final LeagueAdminGuard leagueAdminGuard;
    private final DraftRepository draftRepository;

    public DraftSetupGuard(LeagueAdminGuard leagueAdminGuard, DraftRepository draftRepository) {
        this.leagueAdminGuard = leagueAdminGuard;
        this.draftRepository = draftRepository;
    }

    public DraftSetup requireDraftInSetup(String leagueId, String username) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(leagueId, username);
        DraftEntity draft = draftRepository.findActiveByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException("El draft no se está preparando"));
        return new DraftSetup(league, draft);
    }
}
```

`PrepareDraftCommandHandler`:

```java
/**
 * Abre la preparación del draft: crea el draft en PENDING con la configuración por defecto, el orden de
 * turnos de la lista de miembros y los tiers calculados por BST como punto de partida. Cierra las nominaciones.
 */
@Service
public class PrepareDraftCommandHandler implements CommandHandler<PrepareDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final ClosedListRepository closedListRepository;
    private final TierAssignmentService tierAssignmentService;

    // constructor con los cuatro

    @Override
    public Void handle(PrepareDraftCommand command) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        draftRepository.findLatestByLeagueId(command.leagueId()).ifPresent(latest -> {
            switch (latest.getStatus()) {
                case PENDING -> throw new IllegalStateException("El draft ya se está preparando");
                case IN_PROGRESS -> throw new IllegalStateException("Ya hay un draft en marcha en esta liga");
                case COMPLETED -> throw new IllegalStateException("Esta liga ya ha hecho su draft");
                default -> { } // CANCELLED: se puede volver a preparar
            }
        });

        if (closedListRepository.findAllByLeagueId(command.leagueId()).isEmpty()) {
            throw new IllegalStateException("El pool está vacío: nominad Pokémon antes de preparar el draft");
        }

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.PENDING);
        draft.setLeagueId(command.leagueId());
        draft.setTurnOrder(league.getMembers().stream().map(LeagueMember::getUsername)
                .collect(Collectors.toCollection(ArrayList::new)));
        draft.setCurrentTurnIndex(0);
        draft.setCurrentRound(1);
        draft.setPicks(new ArrayList<>());
        draft.setDraftHistory(new ArrayList<>());
        draft.setConfig(DraftConfig.defaults());
        draftRepository.save(draft);

        tierAssignmentService.assignTiersToPool(command.leagueId(),
                league.getSettings() != null ? league.getSettings() : LeagueSettings.defaults());
        return null;
    }

    @Override
    public Class<PrepareDraftCommand> commandType() {
        return PrepareDraftCommand.class;
    }
}
```

`DraftFacade`: `public void prepareDraft(String leagueId, String requestingUsername) throws Exception { mediator.send(new PrepareDraftCommand(leagueId, requestingUsername)); }`

- [ ] **Step 4: Ejecutar** — `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest='TurnOrderPolicyTest,DraftSetupGuardTest,PrepareDraftCommandHandlerTest,DraftFacadeTest,StartDraftCommandHandlerTest'` → PASS.

- [ ] **Step 5: Commit** — `feat(draft): preparar el draft antes de empezarlo`

---

### Task 6: Configurar la preparación (config, mover tiers, recalcular)

**Files:**
- Create (application, `commands/draft/`): `UpdateDraftConfigCommand.java`, `UpdateDraftConfigCommandHandler.java`, `SetDraftPoolTiersCommand.java`, `SetDraftPoolTiersCommandHandler.java`, `ResetDraftPoolTiersCommand.java`, `ResetDraftPoolTiersCommandHandler.java`
- Modify: `DraftFacade.java`
- Test: `UpdateDraftConfigCommandHandlerTest.java`, `SetDraftPoolTiersCommandHandlerTest.java`, `ResetDraftPoolTiersCommandHandlerTest.java`, `DraftFacadeTest.java`

**Interfaces:**
- Consumes: `DraftSetupGuard.requireDraftInSetup`, `TurnOrderPolicy.canonical` (Task 5), `DraftConfig` (Task 1), `ClosedListRepository.findById/updateTier`, `TierAssignmentService.assignTiersToPool` (existentes).
- Produces:
  - `UpdateDraftConfigCommand(String leagueId, String requestingUsername, DraftConfig config, List<String> turnOrder) implements Command` (`turnOrder` null = no cambia).
  - `SetDraftPoolTiersCommand(String leagueId, String requestingUsername, List<String> entryIds, Tier tier) implements Command`.
  - `ResetDraftPoolTiersCommand(String leagueId, String requestingUsername) implements Command`.
  - `DraftFacade.updateConfig(String leagueId, String requestingUsername, DraftConfig config, List<String> turnOrder)`, `DraftFacade.setPoolTiers(String leagueId, String requestingUsername, List<String> entryIds, Tier tier)`, `DraftFacade.resetPoolTiers(String leagueId, String requestingUsername)`.

- [ ] **Step 1: Tests**

`UpdateDraftConfigCommandHandlerTest` (mocks `DraftSetupGuard`, `DraftRepository`; `new TurnOrderPolicy()` real):

```java
@ExtendWith(MockitoExtension.class)
class UpdateDraftConfigCommandHandlerTest {
    @Mock private DraftSetupGuard draftSetupGuard;
    @Mock private DraftRepository draftRepository;
    private UpdateDraftConfigCommandHandler handler;
    private final DraftEntity draft = new DraftEntity();
    private final LeagueEntity league = new LeagueEntity();

    @BeforeEach
    void setUp() {
        handler = new UpdateDraftConfigCommandHandler(draftSetupGuard, draftRepository, new TurnOrderPolicy());
        draft.setStatus(DraftStatus.PENDING);
        draft.setTurnOrder(List.of("ash", "misty"));
        league.setMembers(new ArrayList<>(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.USER, 0))));
    }

    private void inSetup() {
        when(draftSetupGuard.requireDraftInSetup("l1", "ash")).thenReturn(new DraftSetupGuard.DraftSetup(league, draft));
    }

    private static DraftConfig config(Integer budget, Integer priceD) {
        return DraftConfig.builder().budget(budget).priceS(200).priceA(150).priceB(100).priceC(60).priceD(priceD).snake(null).build();
    }

    @Test
    void handle_valid_savesConfigAndOrder_snakeNullIsFalse() {
        inSetup();
        handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(800, 0), List.of("misty", "ash")));

        verify(draftRepository).save(draft);
        assertThat(draft.getConfig().getBudget()).isEqualTo(800);
        assertThat(draft.getConfig().getPriceD()).isZero();
        assertThat(draft.getConfig().getSnake()).isFalse();
        assertThat(draft.getTurnOrder()).containsExactly("misty", "ash");
    }

    @Test
    void handle_nullTurnOrder_keepsCurrentOrder() {
        inSetup();
        handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(800, 30), null));
        assertThat(draft.getTurnOrder()).containsExactly("ash", "misty");
    }

    @Test
    void handle_invalidBudgetOrPrices_throws() {
        inSetup();
        assertThatThrownBy(() -> handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(0, 30), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El presupuesto tiene que ser mayor que 0");
        assertThatThrownBy(() -> handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(800, null), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Indica el precio de todos los tiers");
        assertThatThrownBy(() -> handler.handle(new UpdateDraftConfigCommand("l1", "ash", config(800, -1), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Los precios de los tiers no pueden ser negativos");
        assertThatThrownBy(() -> handler.handle(new UpdateDraftConfigCommand("l1", "ash", null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El presupuesto tiene que ser mayor que 0");
        verify(draftRepository, never()).save(any());
    }
}
```

`SetDraftPoolTiersCommandHandlerTest`:

```java
@ExtendWith(MockitoExtension.class)
class SetDraftPoolTiersCommandHandlerTest {

    @Mock private DraftSetupGuard draftSetupGuard;
    @Mock private ClosedListRepository closedListRepository;

    private SetDraftPoolTiersCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SetDraftPoolTiersCommandHandler(draftSetupGuard, closedListRepository);
    }

    private static ClosedListEntity entry(String id, String leagueId) {
        ClosedListEntity e = new ClosedListEntity();
        e.setId(id);
        e.setLeagueId(leagueId);
        return e;
    }

    @Test
    void handle_movesEveryEntryOnce() {
        when(closedListRepository.findById("e1")).thenReturn(Optional.of(entry("e1", "l1")));
        when(closedListRepository.findById("e2")).thenReturn(Optional.of(entry("e2", "l1")));

        handler.handle(new SetDraftPoolTiersCommand("l1", "ash", List.of("e1", "e2", "e1"), Tier.A));

        verify(draftSetupGuard).requireDraftInSetup("l1", "ash");
        verify(closedListRepository).updateTier("e1", Tier.A);
        verify(closedListRepository).updateTier("e2", Tier.A);
    }

    @Test
    void handle_entryOfAnotherLeague_throwsAndUpdatesNothing() {
        when(closedListRepository.findById("e1")).thenReturn(Optional.of(entry("e1", "l1")));
        when(closedListRepository.findById("e2")).thenReturn(Optional.of(entry("e2", "otra")));

        assertThatThrownBy(() -> handler.handle(new SetDraftPoolTiersCommand("l1", "ash", List.of("e1", "e2"), Tier.A)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese Pokémon no está en el pool de esta liga");
        verify(closedListRepository, never()).updateTier(any(), any());
    }

    @Test
    void handle_emptyIdsOrNullTier_throws() {
        assertThatThrownBy(() -> handler.handle(new SetDraftPoolTiersCommand("l1", "ash", List.of(), Tier.A)))
                .hasMessage("Elige al menos un Pokémon");
        assertThatThrownBy(() -> handler.handle(new SetDraftPoolTiersCommand("l1", "ash", null, Tier.A)))
                .hasMessage("Elige al menos un Pokémon");
        assertThatThrownBy(() -> handler.handle(new SetDraftPoolTiersCommand("l1", "ash", List.of("e1"), null)))
                .hasMessage("Indica el tier");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(SetDraftPoolTiersCommand.class);
    }
}
```

`ResetDraftPoolTiersCommandHandlerTest`:

```java
@ExtendWith(MockitoExtension.class)
class ResetDraftPoolTiersCommandHandlerTest {

    @Mock private DraftSetupGuard draftSetupGuard;
    @Mock private TierAssignmentService tierAssignmentService;

    @Test
    void handle_usesLeaguePercentages() {
        LeagueEntity league = new LeagueEntity();
        league.setSettings(LeagueSettings.builder().tierPctS(40).build());
        when(draftSetupGuard.requireDraftInSetup("l1", "ash"))
                .thenReturn(new DraftSetupGuard.DraftSetup(league, new DraftEntity()));

        new ResetDraftPoolTiersCommandHandler(draftSetupGuard, tierAssignmentService)
                .handle(new ResetDraftPoolTiersCommand("l1", "ash"));

        verify(tierAssignmentService).assignTiersToPool(eq("l1"), argThat(s -> s.getTierPctS() == 40));
    }

    @Test
    void handle_leagueWithoutSettings_usesDefaults() {
        when(draftSetupGuard.requireDraftInSetup("l1", "ash"))
                .thenReturn(new DraftSetupGuard.DraftSetup(new LeagueEntity(), new DraftEntity()));

        new ResetDraftPoolTiersCommandHandler(draftSetupGuard, tierAssignmentService)
                .handle(new ResetDraftPoolTiersCommand("l1", "ash"));

        verify(tierAssignmentService).assignTiersToPool(eq("l1"), argThat(s -> s.getTierPctS() == 20));
    }
}
```

`DraftFacadeTest`, añadir:

```java
    @Test
    void setupMethods_sendTheirCommands() throws Exception {
        DraftConfig config = DraftConfig.defaults();
        facade.updateConfig("l1", "ash", config, List.of("ash"));
        facade.setPoolTiers("l1", "ash", List.of("e1"), Tier.B);
        facade.resetPoolTiers("l1", "ash");

        verify(mediator).send(new UpdateDraftConfigCommand("l1", "ash", config, List.of("ash")));
        verify(mediator).send(new SetDraftPoolTiersCommand("l1", "ash", List.of("e1"), Tier.B));
        verify(mediator).send(new ResetDraftPoolTiersCommand("l1", "ash"));
    }
```

Y en `UpdateDraftConfigCommandHandlerTest` añadir `commandType_returnsCorrectClass` como en los demás.

- [ ] **Step 2: Ejecutar y ver que falla** (compilación).

- [ ] **Step 3: Implementar**

`UpdateDraftConfigCommandHandler`:

```java
@Service
public class UpdateDraftConfigCommandHandler implements CommandHandler<UpdateDraftConfigCommand, Void> {

    private final DraftSetupGuard draftSetupGuard;
    private final DraftRepository draftRepository;
    private final TurnOrderPolicy turnOrderPolicy;

    // constructor con los tres

    @Override
    public Void handle(UpdateDraftConfigCommand command) {
        DraftSetupGuard.DraftSetup setup = draftSetupGuard.requireDraftInSetup(command.leagueId(), command.requestingUsername());
        DraftConfig config = command.config();
        if (config == null || config.getBudget() == null || config.getBudget() <= 0) {
            throw new IllegalArgumentException("El presupuesto tiene que ser mayor que 0");
        }
        List<Integer> prices = Arrays.asList(config.getPriceS(), config.getPriceA(), config.getPriceB(),
                config.getPriceC(), config.getPriceD());
        if (prices.contains(null)) {
            throw new IllegalArgumentException("Indica el precio de todos los tiers");
        }
        if (prices.stream().anyMatch(p -> p < 0)) {
            throw new IllegalArgumentException("Los precios de los tiers no pueden ser negativos");
        }

        DraftEntity draft = setup.draft();
        if (command.turnOrder() != null) {
            draft.setTurnOrder(turnOrderPolicy.canonical(command.turnOrder(), setup.league()));
        }
        draft.setConfig(config.toBuilder().snake(Boolean.TRUE.equals(config.getSnake())).build());
        draftRepository.save(draft);
        return null;
    }

    @Override
    public Class<UpdateDraftConfigCommand> commandType() {
        return UpdateDraftConfigCommand.class;
    }
}
```

`SetDraftPoolTiersCommandHandler`:

```java
/** Mueve Pokémon del pool a otro tier durante la preparación. Sin cascada: el reparto lo decide el admin. */
@Service
public class SetDraftPoolTiersCommandHandler implements CommandHandler<SetDraftPoolTiersCommand, Void> {

    private final DraftSetupGuard draftSetupGuard;
    private final ClosedListRepository closedListRepository;

    // constructor con los dos

    @Override
    public Void handle(SetDraftPoolTiersCommand command) {
        draftSetupGuard.requireDraftInSetup(command.leagueId(), command.requestingUsername());
        if (command.entryIds() == null || command.entryIds().isEmpty()) {
            throw new IllegalArgumentException("Elige al menos un Pokémon");
        }
        if (command.tier() == null) {
            throw new IllegalArgumentException("Indica el tier");
        }
        List<String> ids = command.entryIds().stream().distinct().toList();
        for (String id : ids) {
            closedListRepository.findById(id)
                    .filter(e -> command.leagueId().equals(e.getLeagueId()))
                    .orElseThrow(() -> new IllegalArgumentException("Ese Pokémon no está en el pool de esta liga"));
        }
        ids.forEach(id -> closedListRepository.updateTier(id, command.tier()));
        return null;
    }

    @Override
    public Class<SetDraftPoolTiersCommand> commandType() {
        return SetDraftPoolTiersCommand.class;
    }
}
```

`ResetDraftPoolTiersCommandHandler`:

```java
/** Vuelve a repartir los tiers del pool por BST (porcentajes de la liga) durante la preparación. */
@Service
public class ResetDraftPoolTiersCommandHandler implements CommandHandler<ResetDraftPoolTiersCommand, Void> {

    private final DraftSetupGuard draftSetupGuard;
    private final TierAssignmentService tierAssignmentService;

    // constructor con los dos

    @Override
    public Void handle(ResetDraftPoolTiersCommand command) {
        LeagueEntity league = draftSetupGuard.requireDraftInSetup(command.leagueId(), command.requestingUsername()).league();
        tierAssignmentService.assignTiersToPool(command.leagueId(),
                league.getSettings() != null ? league.getSettings() : LeagueSettings.defaults());
        return null;
    }

    @Override
    public Class<ResetDraftPoolTiersCommand> commandType() {
        return ResetDraftPoolTiersCommand.class;
    }
}
```

`DraftFacade`: los tres métodos de la sección Interfaces, un `mediator.send` cada uno.

- [ ] **Step 4: Ejecutar** — `-Dtest='UpdateDraftConfigCommandHandlerTest,SetDraftPoolTiersCommandHandlerTest,ResetDraftPoolTiersCommandHandlerTest,DraftFacadeTest'` → PASS.

- [ ] **Step 5: Commit** — `feat(draft): configurar presupuesto, precios, orden y tiers en la preparación`

---

### Task 7: Empezar el draft preparado y "volver a nominaciones"

**Files:**
- Modify: `.../commands/draft/StartDraftCommandHandler.java`, `.../commands/draft/CancelDraftCommandHandler.java`
- Test: `StartDraftCommandHandlerTest.java`, `CancelDraftCommandHandlerTest.java`

**Interfaces:**
- Consumes: `TurnOrderPolicy` (Task 5), `DraftTurnService` (Task 2), `DraftRepository.delete` (Task 1).
- Produces: constructor `StartDraftCommandHandler(DraftRepository, LeagueAdminGuard, LeagueRepository, TierAssignmentService, DraftTurnNotifier, TurnOrderPolicy, ClosedListRepository, DraftTurnService)`. `StartDraftCommand` no cambia (`turnOrder` puede ser null).

- [ ] **Step 1: Tests**

`StartDraftCommandHandlerTest.setUp` con el constructor nuevo (`@Mock ClosedListRepository closedListRepository`, `new TurnOrderPolicy()`, `new DraftTurnService()`). Los tests actuales siguen valiendo (camino "sin draft preparado"). Añadir:

```java
    private DraftEntity pendingDraft(DraftConfig config) {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.PENDING);
        draft.setLeagueId(LEAGUE_ID);
        draft.setTurnOrder(new ArrayList<>(List.of(ADMIN, "misty")));
        draft.setPicks(new ArrayList<>());
        draft.setDraftHistory(new ArrayList<>());
        draft.setConfig(config);
        return draft;
    }

    private static ClosedListEntity tiered(String name, Tier tier) {
        ClosedListEntity e = new ClosedListEntity();
        e.setPokemonName(name);
        e.setTier(tier);
        return e;
    }

    private static LeagueEntity leagueWith(String... members) {
        LeagueEntity league = new LeagueEntity();
        List<LeagueMember> list = new ArrayList<>();
        for (String m : members) list.add(new LeagueMember(m, LeagueRole.USER, 0));
        league.setMembers(list);
        return league;
    }

    @Test
    void handle_preparedDraftWithOutdatedTurnOrder_throws() {
        // brock entró en la liga después de preparar el draft y el admin no ha guardado el orden
        DraftEntity draft = pendingDraft(DraftConfig.defaults());
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, ADMIN)).thenReturn(leagueWith(ADMIN, "misty", "brock"));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new StartDraftCommand(null, LEAGUE_ID, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Faltan en el orden de turnos: brock");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void handle_preparedDraft_startsWithoutRetieringOrTurnOrder() {
        DraftEntity draft = pendingDraft(DraftConfig.defaults());
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, ADMIN)).thenReturn(leagueWith(ADMIN, "misty"));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(List.of(tiered("abra", Tier.D)));

        handler.handle(new StartDraftCommand(null, LEAGUE_ID, ADMIN));

        verify(draftRepository).save(draft);
        assertThat(draft.getStatus()).isEqualTo(DraftStatus.IN_PROGRESS);
        assertThat(draft.getCurrentTurnIndex()).isZero();
        assertThat(draft.getCurrentRound()).isEqualTo(1);
        assertThat(draft.getCurrentTurnStartedAt()).isNotNull();
        verify(tierAssignmentService, never()).assignTiersToPool(any(), any());
        verify(draftTurnNotifier).notifyCurrentTurn(eq(draft), any());
    }

    @Test
    void handle_preparedDraftNobodyCanPay_throwsAndSavesNothing() {
        DraftEntity draft = pendingDraft(DraftConfig.defaults().toBuilder().budget(10).build());
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, ADMIN)).thenReturn(leagueWith(ADMIN, "misty"));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(List.of(tiered("abra", Tier.D)));

        assertThatThrownBy(() -> handler.handle(new StartDraftCommand(null, LEAGUE_ID, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Con este presupuesto nadie puede elegir ningún Pokémon");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void handle_preparedDraftWithUntieredPokemon_throws() {
        DraftEntity draft = pendingDraft(DraftConfig.defaults());
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, ADMIN)).thenReturn(leagueWith(ADMIN, "misty"));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(List.of(tiered("abra", null)));

        assertThatThrownBy(() -> handler.handle(new StartDraftCommand(null, LEAGUE_ID, ADMIN)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hay Pokémon sin tier: recalcula los tiers antes de empezar");
    }
```

`CancelDraftCommandHandlerTest`, añadir:

```java
    @Test
    void handle_draftInSetup_deletesIt() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.PENDING);
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(new CancelDraftCommand("l1", "ash"));

        verify(draftRepository).delete(draft);
        verify(draftRepository, never()).save(any());
    }
```

(Usar los ids/usuario que ya use el test; `leagueAdminGuard` está mockeado.)

- [ ] **Step 2: Ejecutar y ver que falla.**

- [ ] **Step 3: Implementar**

`StartDraftCommandHandler.handle`:

```java
    @Override
    public Void handle(StartDraftCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("El orden de turnos debe tener al menos un jugador");
        }

        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        Optional<DraftEntity> active = draftRepository.findActiveByLeagueId(command.leagueId());
        if (active.isPresent()) {
            if (active.get().getStatus() == DraftStatus.PENDING) {
                return startPrepared(active.get(), league, command.leagueId());
            }
            throw new IllegalStateException("Ya hay un draft en marcha en esta liga");
        }

        // Sin preparación: el front anterior a la configuración del draft manda el orden y arranca directamente
        // (draft gratis y lineal). Quitar cuando no quede ningún cliente que lo use.
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draft.setTurnOrder(turnOrderPolicy.canonical(command.turnOrder(), league));
        // ... resto igual que ahora (currentTurnIndex, currentRound, picks, leagueId, currentTurnStartedAt,
        //     save, assignTiersToPool, notifyCurrentTurn)
    }

    private Void startPrepared(DraftEntity draft, LeagueEntity league, String leagueId) {
        // Alguien pudo entrar o salir de la liga después de guardar el orden de turnos.
        draft.setTurnOrder(turnOrderPolicy.canonical(draft.getTurnOrder(), league));
        List<ClosedListEntity> pool = closedListRepository.findAllByLeagueId(leagueId);
        if (pool.isEmpty()) {
            throw new IllegalStateException("El pool está vacío: nominad Pokémon antes de empezar");
        }
        if (pool.stream().anyMatch(e -> e.getTier() == null)) {
            throw new IllegalStateException("Hay Pokémon sin tier: recalcula los tiers antes de empezar");
        }
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draftTurnService.placeFirstTurn(draft, draftTurnService.available(draft, pool),
                draftTurnService.maxTeamSize(league));
        if (draft.getStatus() == DraftStatus.COMPLETED) {
            throw new IllegalArgumentException("Con este presupuesto nadie puede elegir ningún Pokémon");
        }
        draft.setCurrentTurnStartedAt(Instant.now());
        draftRepository.save(draft);
        draftTurnNotifier.notifyCurrentTurn(draft, league);
        return null;
    }
```

Notas:
- Los tests actuales `handle_nullTurnOrder_*` y `handle_emptyTurnOrder_*` no stubean el guard: el mock devuelve `null` y `findActiveByLeagueId` devuelve `Optional.empty()` por defecto, así que llegan a `canonical(...)` y lanzan `IllegalArgumentException` como antes.
- `handle_activeDraftAlreadyExists_throwsIllegalState` sigue valiendo (draft `IN_PROGRESS` → "Ya hay un draft en marcha").

`CancelDraftCommandHandler.handle`, tras obtener `draft`:

```java
        if (draft.getStatus() == DraftStatus.PENDING) {
            // Volver a nominaciones: el draft en preparación no tiene picks, se borra y se reabren las nominaciones.
            draftRepository.delete(draft);
            return null;
        }
```

- [ ] **Step 4: Ejecutar** — `-Dtest='StartDraftCommandHandlerTest,CancelDraftCommandHandlerTest'` → PASS.

- [ ] **Step 5: Commit** — `feat(draft): empezar el draft preparado sin recalcular tiers`

---

### Task 8: Nominaciones cerradas con draft y settings sin recalcular en preparación

**Files:**
- Modify: `.../commands/closedlist/NominatePokemonCommandHandler.java`, `DenominatePokemonCommandHandler.java`, `.../commands/league/UpdateLeagueSettingsCommandHandler.java`
- Test: `NominatePokemonCommandHandlerTest.java`, `DenominatePokemonCommandHandlerTest.java`, `UpdateLeagueSettingsCommandHandlerTest.java`

- [ ] **Step 1: Tests**

- `NominatePokemonCommandHandlerTest`: añadir `handle_draftInSetup_throwsIllegalState` (latest draft `PENDING` → `IllegalStateException` con mensaje "Las nominaciones están cerradas: se está preparando el draft").
- `DenominatePokemonCommandHandlerTest`: sustituir `handle_pendingDraft_deletesEntry` por `handle_draftInSetup_throwsIllegalState` (mensaje "Ya no se pueden quitar nominaciones: se está preparando el draft", `never()` borra).
- `UpdateLeagueSettingsCommandHandlerTest.handle_draftPending_savesSettings`: añadir al final `verify(tierAssignmentService, never()).assignTiersToPool(any(), any());` y renombrar a `handle_draftInSetup_savesSettingsWithoutRetiering`.

- [ ] **Step 2: Ejecutar y ver que fallan.**

- [ ] **Step 3: Implementar**

`NominatePokemonCommandHandler`:

```java
        draftRepository.findLatestByLeagueId(command.leagueId()).ifPresent(draft -> {
            throw new IllegalStateException(draft.getStatus() == DraftStatus.PENDING
                    ? "Las nominaciones están cerradas: se está preparando el draft"
                    : "Las nominaciones están cerradas: el draft ya ha empezado");
        });
```

`DenominatePokemonCommandHandler`:

```java
        draftRepository.findLatestByLeagueId(command.leagueId()).ifPresent(draft -> {
            throw new IllegalStateException(draft.getStatus() == DraftStatus.PENDING
                    ? "Ya no se pueden quitar nominaciones: se está preparando el draft"
                    : "Ya no se pueden quitar nominaciones: el draft ya ha empezado");
        });
```

`UpdateLeagueSettingsCommandHandler`: sustituir el bloque `findLatestByLeagueId(...).ifPresent(...)` por

```java
        Optional<DraftEntity> latestDraft = draftRepository.findLatestByLeagueId(command.leagueId());
        if (latestDraft.map(d -> d.getStatus() == DraftStatus.IN_PROGRESS).orElse(false)) {
            throw new IllegalStateException("No se pueden cambiar los ajustes con un draft en curso");
        }
```

y la llamada a `assignTiersToPool` por

```java
        // En preparación los tiers los reparte el admin a mano: no se pisan.
        boolean draftInSetup = latestDraft.map(d -> d.getStatus() == DraftStatus.PENDING).orElse(false);
        if (!draftInSetup) {
            tierAssignmentService.assignTiersToPool(command.leagueId(), league.getSettings());
        }
```

(Importar `java.util.Optional` y `DraftEntity`.)

- [ ] **Step 4: Ejecutar** — `-Dtest='NominatePokemonCommandHandlerTest,DenominatePokemonCommandHandlerTest,UpdateLeagueSettingsCommandHandlerTest'` → PASS.

- [ ] **Step 5: Commit** — `feat(draft): cerrar nominaciones al preparar el draft`

---

### Task 9: Estado del draft con config, presupuestos y precios

**Files:**
- Modify: `.../commands/draft/GetDraftStatusCommandHandler.java`
- Test: `GetDraftStatusCommandHandlerTest.java`

**Interfaces:**
- Consumes: `DraftTurnService.remainingBudget` (Task 2).
- Produces: constructor `GetDraftStatusCommandHandler(DraftRepository, LeagueRepository, LeagueMembershipGuard, DraftTurnService)`; respuesta con `config`, `budgets` (orden de `turnOrder`, `LinkedHashMap`), `picks[].price`, `draftHistory[].price`, `currentTurn` null en `PENDING`.

- [ ] **Step 1: Tests** (setUp con `new DraftTurnService()`):

```java
    @Test
    void handle_budgetDraft_returnsConfigBudgetsAndPrices() {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(DraftStatus.PENDING);
        draft.setTurnOrder(List.of("ash", "misty"));
        draft.setConfig(DraftConfig.defaults());
        DraftPick paid = new DraftPick("ash", "mew", 151, 1, Instant.now(), null, null);
        paid.setPrice(200);
        draft.setPicks(List.of(paid));
        draft.setDraftHistory(List.of(paid));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        DraftStatusResponse response = handler.handle(new GetDraftStatusCommand(LEAGUE_ID, "ash"));

        assertThat(response.getConfig()).isEqualTo(DraftConfig.defaults());
        assertThat(response.getBudgets()).containsExactly(Map.entry("ash", 800), Map.entry("misty", 1000));
        assertThat(response.getDraftHistory()).extracting(DraftPickResponse::getPrice).containsExactly(200);
        assertThat(response.getCurrentTurn()).isNull(); // en preparación no hay turno
    }

    @Test
    void handle_draftWithoutConfig_hasNoBudgets() {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setTurnOrder(List.of("ash"));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        DraftStatusResponse response = handler.handle(new GetDraftStatusCommand(LEAGUE_ID, "ash"));

        assertThat(response.getConfig()).isNull();
        assertThat(response.getBudgets()).isNull();
    }
```

(`leagueMembershipGuard` es un mock: sin stub no lanza. Si el fichero usa otra constante para la liga, usar la suya.)

- [ ] **Step 2: Ejecutar y ver que falla.**

- [ ] **Step 3: Implementar**

```java
        String currentTurn = draft.getStatus() == DraftStatus.COMPLETED || draft.getStatus() == DraftStatus.PENDING
                ? null : draft.getTurnOrder().get(draft.getCurrentTurnIndex());
        ...
                .config(draft.getConfig())
                .budgets(budgets(draft))
        ...
    private Map<String, Integer> budgets(DraftEntity draft) {
        if (draft.getConfig() == null) return null;
        Map<String, Integer> budgets = new LinkedHashMap<>();
        draft.getTurnOrder().forEach(u -> budgets.put(u, draftTurnService.remainingBudget(draft, u)));
        return budgets;
    }
```

y en `mapPicks` añadir `.price(pick.getPrice())`.

- [ ] **Step 4: Ejecutar** — `-Dtest=GetDraftStatusCommandHandlerTest` → PASS.

- [ ] **Step 5: Commit** — `feat(draft): el estado del draft expone config, presupuestos y precios`

---

### Task 10: Endpoints

**Files:**
- Create: `src/domain/src/main/java/com/villu/pokefantasy/request/draft/UpdateDraftConfigRequest.java`, `SetDraftPoolTiersRequest.java`
- Modify: `src/api-rest/src/main/java/com/villu/pokefantasy/DraftController.java`
- Test: `src/api-rest/src/test/java/com/villu/pokefantasy/DraftScheduleUserControllersTest.java`

**Interfaces:**
- Consumes: métodos de `DraftFacade` de Tasks 5 y 6.
- Produces (contrato para el front):
  - `POST /v1/leagues/{leagueId}/draft/prepare` (sin body) → 200.
  - `PUT /v1/leagues/{leagueId}/draft/config` body `{budget, priceS, priceA, priceB, priceC, priceD, snake, turnOrder}` → 200.
  - `PUT /v1/leagues/{leagueId}/draft/pool/tiers` body `{entryIds: string[], tier: "S"|"A"|"B"|"C"|"D"}` → 200.
  - `POST /v1/leagues/{leagueId}/draft/pool/reset-tiers` (sin body) → 200.
  - `POST /v1/leagues/{leagueId}/draft/start` body opcional `{turnOrder}` → 200.
  - Todos emiten `draft-updated` por SSE tras el commit.

- [ ] **Step 1: Tests** (en `DraftScheduleUserControllersTest`):

```java
    @Test
    void draftSetupActions_notifyWatchers() throws Exception {
        mvc.perform(post("/v1/leagues/l1/draft/prepare")).andExpect(status().isOk());
        verify(draftFacade).prepareDraft("l1", ME);

        mvc.perform(json(put("/v1/leagues/l1/draft/config"),
                "{\"budget\":900,\"priceS\":200,\"priceA\":150,\"priceB\":100,\"priceC\":60,\"priceD\":0,\"snake\":true,\"turnOrder\":[\"misty\",\"ash\"]}"))
                .andExpect(status().isOk());
        verify(draftFacade).updateConfig("l1", ME,
                DraftConfig.builder().budget(900).priceS(200).priceA(150).priceB(100).priceC(60).priceD(0).snake(true).build(),
                List.of("misty", "ash"));

        mvc.perform(json(put("/v1/leagues/l1/draft/pool/tiers"), "{\"entryIds\":[\"e1\",\"e2\"],\"tier\":\"A\"}"))
                .andExpect(status().isOk());
        verify(draftFacade).setPoolTiers("l1", ME, List.of("e1", "e2"), Tier.A);

        mvc.perform(post("/v1/leagues/l1/draft/pool/reset-tiers")).andExpect(status().isOk());
        verify(draftFacade).resetPoolTiers("l1", ME);

        verify(notifier, org.mockito.Mockito.times(4)).draftUpdated("l1");
    }

    @Test
    void startWithoutBody_startsPreparedDraft() throws Exception {
        mvc.perform(post("/v1/leagues/l1/draft/start")).andExpect(status().isOk());
        verify(draftFacade).startDraft(null, "l1", ME);
    }
```

(Importar `put` de `MockMvcRequestBuilders`, `DraftConfig`, `Tier`.)

- [ ] **Step 2: Ejecutar y ver que falla** — `./mvnw -B -ntp -f src/pom.xml test -pl api-rest -am -Dtest=DraftScheduleUserControllersTest`.

- [ ] **Step 3: Implementar**

Requests (`@Data`, paquete `com.villu.pokefantasy.request.draft`):

```java
@Data
public class UpdateDraftConfigRequest {
    private Integer budget;
    private Integer priceS;
    private Integer priceA;
    private Integer priceB;
    private Integer priceC;
    private Integer priceD;
    private Boolean snake;
    /** Orden de turnos; null = no cambia. */
    private List<String> turnOrder;
}

@Data
public class SetDraftPoolTiersRequest {
    private List<String> entryIds;
    private Tier tier;
}
```

`DraftController`:

```java
    @PostMapping("/start")
    public ResponseEntity<Void> startDraft(@PathVariable String leagueId,
                                           @AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody(required = false) StartDraftRequest request) throws Exception {
        // Sin body: empieza el draft preparado. Con turnOrder y sin preparar: arranque directo del front anterior.
        draftFacade.startDraft(request != null ? request.getTurnOrder() : null, leagueId, userDetails.getUsername());
        realtimeNotifier.draftUpdated(leagueId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/prepare")
    public ResponseEntity<Void> prepareDraft(@PathVariable String leagueId,
                                             @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        draftFacade.prepareDraft(leagueId, userDetails.getUsername());
        realtimeNotifier.draftUpdated(leagueId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/config")
    public ResponseEntity<Void> updateConfig(@PathVariable String leagueId,
                                             @AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody UpdateDraftConfigRequest request) throws Exception {
        DraftConfig config = DraftConfig.builder()
                .budget(request.getBudget())
                .priceS(request.getPriceS()).priceA(request.getPriceA()).priceB(request.getPriceB())
                .priceC(request.getPriceC()).priceD(request.getPriceD())
                .snake(request.getSnake())
                .build();
        draftFacade.updateConfig(leagueId, userDetails.getUsername(), config, request.getTurnOrder());
        realtimeNotifier.draftUpdated(leagueId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/pool/tiers")
    public ResponseEntity<Void> setPoolTiers(@PathVariable String leagueId,
                                             @AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody SetDraftPoolTiersRequest request) throws Exception {
        draftFacade.setPoolTiers(leagueId, userDetails.getUsername(), request.getEntryIds(), request.getTier());
        realtimeNotifier.draftUpdated(leagueId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pool/reset-tiers")
    public ResponseEntity<Void> resetPoolTiers(@PathVariable String leagueId,
                                               @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        draftFacade.resetPoolTiers(leagueId, userDetails.getUsername());
        realtimeNotifier.draftUpdated(leagueId);
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 4: Ejecutar** — PASS. Comprobar que `draftActions_notifyWatchers` (con body en `start`) sigue en verde.

- [ ] **Step 5: Commit** — `feat(draft): endpoints de la preparación del draft`

---

### Task 11: Verificación completa, CLAUDE.md, PR y documentación

**Files:**
- Modify: `CLAUDE.md` (tabla §2)
- Vault (repo `C:\PokeFantasy\vault`, `git pull` antes): `50 Features/Configuración del draft.md`, `20 Arquitectura/API REST.md`, `20 Arquitectura/Modelo de datos.md`, `20 Arquitectura/Flujos de datos.md`, `50 Features/Draft.md`, `50 Features/Pool y tiers.md`, `50 Features/Monedas.md`

- [ ] **Step 1: Dueños nuevos en `CLAUDE.md` §2**

Añadir filas:

```markdown
| Turnos y presupuesto del draft (precio, restante, quién puede elegir, avance lineal/snake) | `DraftTurnService` |
| Preparación del draft (admin + draft en `PENDING`) / orden de turnos válido | `DraftSetupGuard` / `TurnOrderPolicy` |
```

- [ ] **Step 2: Verificación**

Run (con Docker abierto para los tests de integración): `./mvnw -B -ntp -f src/pom.xml clean verify`
Expected: BUILD SUCCESS, JaCoCo por encima del 80 % en `application`, `infrastructure` y `api-rest`. Si el gate falla, añadir tests a las ramas sin cubrir (normalmente los `null` de `DraftTurnService.priceOf` y los mensajes de `PrepareDraftCommandHandler`).

- [ ] **Step 3: Spec OpenAPI para el front**

Arrancar el backend solo para la spec y guardarla para el plan del front:

```bash
./mvnw -B -ntp -f src/pom.xml clean package -DskipTests
SPRING_MAIN_LAZY_INITIALIZATION=true SERVER_PORT=8089 JWT_SECRET=<base64 de 256 bits de .env local> java -jar src/boot/target/boot-*.jar
```

Comprobar que `http://localhost:8089/v3/api-docs` incluye `/draft/prepare`, `/draft/config`, `/draft/pool/tiers`, `/draft/pool/reset-tiers`, `DraftConfig`, `budgets` y `DRAFT_COINS`. (El front la descarga en su Task 1.)

- [ ] **Step 4: Push y PR**

Revisar PRs abiertos en ambos repos (snippet de `CLAUDE.md`). `git push -u origin feature/configuracion-draft` y crear el PR contra `develop` por API con cuerpo:

```
## Qué
Preparación del draft antes de empezarlo: tiers editables, precio por tier, presupuesto por jugador, orden de turnos y snake. Los picks cuestan monedas; a quien no puede pagar se le salta y lo que sobra pasa al saldo de la liga.

## Endpoints nuevos
POST draft/prepare · PUT draft/config · PUT draft/pool/tiers · POST draft/pool/reset-tiers · POST draft/start (body opcional)

## Compatibilidad
El front actual sigue funcionando: POST draft/start con turnOrder y sin preparar arranca el draft como antes. Los drafts en curso sin config siguen gratis y lineales.
Mergear antes que el PR del front.

Nota: vault/50 Features/Configuración del draft.md

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

- [ ] **Step 5: Vault**

- `Configuración del draft.md`: `estado: en-curso`, `prs_back: ["#N"]`, "Enlaces → Plan" con la ruta de este plan; corregir "`POST draft/start` (sin body)" por "body opcional (compatibilidad con el front anterior)"; añadir la regla "Preparar solo sin draft o tras uno cancelado".
- `API REST.md`: los cinco endpoints y los campos nuevos de `GET draft`.
- `Modelo de datos.md`: `DraftEntity.config`, `DraftPick.price`, `ActivityEventType.DRAFT_COINS`.
- `Flujos de datos.md`: flujo preparar → configurar → empezar → pick con precio → sobrante.
- `Draft.md`, `Pool y tiers.md` (tiers en preparación sin cascada; `start` ya no recalcula), `Monedas.md` (ingreso `DRAFT_COINS`).
- Commit y push del vault.
