# Replays privados en el import de Showdown — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que un replay privado pegado en «Importar de Showdown» se pueda registrar, que «Ver replay» funcione, que un mismo combate no cuente en dos partidos y que los enlaces mal pegados den un mensaje útil.

**Architecture:** Showdown devuelve un replay privado con `id` sin contraseña y `password` aparte. `ShowdownApiAdapter` recompone el id descargable (`<id>-<password>pw`), así que el contrato del puerto pasa a ser "el id es el que se puede descargar" y `application` no cambia de forma. La reutilización compara ids base (sin `-...pw`). `ScheduleFacade` da mensajes concretos para el enlace de la sala y para un privado pegado sin contraseña.

**Tech Stack:** Java / Spring Boot 4.1 (parent de `src/pom.xml`), RestTemplate + MockRestServiceServer, Mockito, Testcontainers.

**Spec:** diseño corto aprobado en la conversación del 2026-11-14 (sin fichero de spec, por norma del usuario). Resumen:
- Bug: Showdown `GET /<id>-<pw>pw.json` devuelve `{"id":"<id>","password":"<pw>","private":1,...}`; el adaptador usa `id`, el registro vuelve a descargar por ese id y Showdown da 404.
- Enfoque A (elegido): recomponer el id en el adaptador. Descartados: B (campo `password` en el puerto, dos nociones de id en `application`) y C (guardar la contraseña en el partido, cambia esquema/DTO/front).
- Fuera de alcance: import automático de privados (`search.json` no los devuelve), cambios en el front, migración (no hay datos rotos).

## Global Constraints

- La contraseña de un replay privado nunca va a logs: los logs y los mensajes de error usan `ReplayCandidates.redact(id)`.
- Sin cambios de contrato de la API (DTOs, endpoints) ni del front.
- Textos exactos de los mensajes nuevos:
  - Enlace de sala: `Ese es el enlace de la sala, no del replay: pulsa «Upload and share replay» y pega el enlace que te da.`
  - 404 de un id sin contraseña: `No se encontró el replay <id>. Si es privado, pega el enlace completo, el que acaba en …pw.`
  - 404 de un id con contraseña: `No se encontró el replay <redact(id)>.`
- Rama `fix/replays-privados` desde `develop`; PR a `develop`. Antes del push: `./mvnw -B -ntp -f src/pom.xml clean verify` con Docker abierto (gate JaCoCo 80 %).

## Review Focus

1. Showdown devuelve `"password":null` o `""` en un replay público → el id queda igual (test en Task 1).
2. Showdown ya devolviera el id con el sufijo `-<pw>pw` → no se duplica (test en Task 1).
3. El mismo combate guardado en otro partido como público y pegado ahora como privado (o al revés) → "Ya usado en otro partido" (test en Task 2).
4. Un id público cuyo número no acaba en `pw` no se recorta en `baseId` (p. ej. `gen9doublescustomgame-123`) (test en Task 2).
5. El enlace de la sala con contraseña (`https://play.pokemonshowdown.com/battle-gen9doublescustomgame-1-abcpw`) también da el mensaje de sala (test en Task 3).

---

### Task 1: el adaptador devuelve el id descargable

**Files:**
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/adapters/ShowdownApiAdapter.java` (`getReplay`, record `ReplayJson`)
- Test: `src/infrastructure/src/test/java/com/villu/pokefantasy/adapters/ShowdownApiAdapterTest.java`

**Interfaces:**
- Produces: `ShowdownPort.getReplay(id)` devuelve `ShowdownReplay` cuyo `id()` es el id con el que se descarga (`<id>-<password>pw` en privados). Actualizar el javadoc de `ShowdownReplay`/`ShowdownPort.getReplay` para decirlo.

- [ ] **Step 1: tests que fallan**

```java
private static final String PRIVATE_URL = "https://replay.pokemonshowdown.com/gen9doublescustomgame-1-secretpw.json";

@Test
void getReplay_private_idKeepsPassword() {
    server.expect(requestTo(PRIVATE_URL)).andRespond(withSuccess(
            "{\"id\":\"gen9doublescustomgame-1\",\"password\":\"secret\",\"private\":1,\"formatid\":\"gen9doublescustomgame\","
                    + "\"players\":[\"Ash\",\"Misty\"],\"log\":\"|win|Ash\",\"uploadtime\":1700000000}",
            MediaType.APPLICATION_JSON));
    assertThat(adapter.getReplay("gen9doublescustomgame-1-secretpw")).get()
            .extracting(ShowdownReplay::id).isEqualTo("gen9doublescustomgame-1-secretpw");
}

@Test
void getReplay_idAlreadyWithPassword_notDuplicated() {
    // misma petición; respuesta con "id":"gen9doublescustomgame-1-secretpw","password":"secret"
    // → id() == "gen9doublescustomgame-1-secretpw"
}

@Test
void getReplay_blankPassword_idUnchanged() {
    // REPLAY_URL; respuesta pública con "password":"" → id() == "gen9doublescustomgame-1"
}
```

`getReplay_returnsReplay` (sin campo `password`, es decir null) ya cubre el público sin contraseña.

- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest=ShowdownApiAdapterTest -Dsurefire.failIfNoSpecifiedTests=false` → FAIL en `getReplay_private_idKeepsPassword` (id sin `-secretpw`).

- [ ] **Step 3:** `ReplayJson` gana `String password`; método privado `static String downloadableId(String id, String password)`: si `password` es null o vacío, o `id` ya acaba en `"-" + password + "pw"`, devuelve `id`; si no, `id + "-" + password + "pw"`. `getReplay` lo usa para el `id` del `ShowdownReplay`.

- [ ] **Step 4:** mismo comando → PASS.

- [ ] **Step 5: commit** `fix(showdown): conservar la contraseña en el id de un replay privado`

### Task 2: "ya usado" compara ids base

**Files:**
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/schedule/replay/ReplayCandidates.java` (nuevo `baseId`, `check` línea 78)
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/schedule/ReplayImportService.java:72-75`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/schedule/replay/ReplayCandidatesTest.java`

**Interfaces:**
- Produces: `public static String ReplayCandidates.baseId(String replayId)`: el id sin `-<contraseña>pw` final (mismo patrón que `redact`: `-[a-z0-9]+pw$`); null → null.
- `ReplayImportContext.usedReplayIds` pasa a contener ids base (actualizar su javadoc).

- [ ] **Step 1: tests que fallan** en `ReplayCandidatesTest`

```java
@Test
void baseId_stripsPrivatePassword() {
    assertThat(ReplayCandidates.baseId("gen9doublescustomgame-1-secretpw")).isEqualTo("gen9doublescustomgame-1");
    assertThat(ReplayCandidates.baseId("gen9doublescustomgame-123")).isEqualTo("gen9doublescustomgame-123");
}

@Test
void check_privateReplayOfAUsedPublicOne_isUsed() {
    // ctx con usedReplayIds = Set.of("gen9doublescustomgame-1") y replay válido con id "gen9doublescustomgame-1-secretpw"
    // → reason() contiene "Ya usado en otro partido"
}

@Test
void check_publicReplayOfAUsedPrivateOne_isUsed() {
    // ctx con usedReplayIds = Set.of("gen9doublescustomgame-1") (ReplayImportService ya los pasa a base)
    // y replay con id "gen9doublescustomgame-1" → "Ya usado en otro partido"
}
```

Para `ReplayImportService`, en `PrepareReplayImportCommandHandlerTest` (donde ya se prueba `usedReplayIds`, líneas 63 y 114): test nuevo `usedReplayIds_areBaseIds` con `other.setReplayIds(List.of("used-1-secretpw", "used-2"))` → `ctx.usedReplayIds()` `containsExactlyInAnyOrder("used-1", "used-2")`. Los tests existentes de `ImportSeriesCommandHandlerTest` (`replayNotSelectable_rejectedWithReason`, `privateReplayNotSelectable_errorHidesItsPassword`) deben seguir pasando sin cambios.

- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest='ReplayCandidatesTest,PrepareReplayImportCommandHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false` → FAIL (`baseId` no existe).

- [ ] **Step 3:** implementar `baseId`; en `check`, `ctx.usedReplayIds().contains(baseId(replay.id()))`; en `ReplayImportService`, `m.getReplayIds().stream().map(ReplayCandidates::baseId).forEach(used::add)`.

- [ ] **Step 4:** mismo comando → PASS.

- [ ] **Step 5: commit** `fix(replays): un combate no cuenta dos veces aunque cambie de público a privado`

### Task 3: mensajes para enlaces mal pegados

**Files:**
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/schedule/ScheduleFacade.java` (`replayCandidates`, `fetchReplay`)
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/schedule/ScheduleFacadeTest.java`

**Interfaces:**
- Consumes: `ReplayCandidates.redact(String)` (existente).
- Produces: `ReplayCandidates.isBattleRoomUrl(String url)`: `true` si encaja con `^https://play\.pokemonshowdown\.com/battle-[a-z0-9-]+/?$` (tras `trim`).

- [ ] **Step 1: tests que fallan** en `ScheduleFacadeTest`

```java
@Test
void replayCandidates_battleRoomUrl_explains() throws Exception {
    when(mediator.send(new PrepareReplayImportCommand("l1", "m1", "ash"))).thenReturn(CTX);
    for (String url : List.of("https://play.pokemonshowdown.com/battle-gen9doublescustomgame-1",
            "https://play.pokemonshowdown.com/battle-gen9doublescustomgame-1-abcpw")) {
        assertThatThrownBy(() -> facade.replayCandidates("l1", "m1", url, "ash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese es el enlace de la sala, no del replay: pulsa «Upload and share replay» y pega el enlace que te da.");
    }
    verifyNoInteractions(showdownPort);
}

@Test
void replayCandidates_privateMissing_redactsPassword() throws Exception {
    // getReplay("gen9doublescustomgame-9-secretpw") → empty
    // → hasMessage("No se encontró el replay gen9doublescustomgame-9-***pw.")
}
```

Actualizar `replayCandidates_pastedReplayMissing_badRequest` al texto nuevo:
`"No se encontró el replay gen9doublescustomgame-9. Si es privado, pega el enlace completo, el que acaba en …pw."`

- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=ScheduleFacadeTest -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.

- [ ] **Step 3:** en `replayCandidates`, antes de `replayId(url)`, si `ReplayCandidates.isBattleRoomUrl(url)` lanzar `IllegalArgumentException` con el texto de sala. En `fetchReplay`, el 404 decide el texto: id distinto de `baseId(id)` → `"No se encontró el replay " + redact(id) + "."`; si no, el texto con la pista de `…pw`. Test de `isBattleRoomUrl` en `ReplayCandidatesTest` (las dos URL de arriba → true; `https://replay.pokemonshowdown.com/gen9doublescustomgame-1` → false).

- [ ] **Step 4:** mismo comando + `ReplayCandidatesTest` → PASS.

- [ ] **Step 5: commit** `fix(replays): mensajes claros para el enlace de la sala y el privado sin contraseña`

### Task 4: integración de punta a punta con un replay privado

**Files:**
- Modify: `src/boot/src/test/java/com/villu/pokefantasy/it/ReplayImportIntegrationTest.java`

**Interfaces:**
- Consumes: el adaptador mockeado (`showdownApiAdapter`) devolviendo ids completos, como hace el real tras Task 1.

- [ ] **Step 1: test**

```java
@Test
void privateSeries_pastedThenImported_keepsFullIds() throws Exception {
    // getReplay("g1-secretpw"/"g2-secretpw"/"g3-secretpw") → game(<mismo id>, n, ganador, t) (ganadores Ash, Misty, Ash)
    // ash y misty registrados y vinculados (como en el test existente)
    // GET BASE + "/matches/m1/replays?url=" + URLEncoder("https://replay.pokemonshowdown.com/g1-secretpw") → body contiene "\"id\":\"g1-secretpw\""
    // POST BASE + "/matches/m1/import" {"replayIds":["g1-secretpw","g2-secretpw","g3-secretpw"]} → 204
    // GET BASE → contiene "\"replayIds\":[\"g1-secretpw\",\"g2-secretpw\",\"g3-secretpw\"]"
}
```

- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl boot -am -Dtest=ReplayImportIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` (Docker abierto) → PASS. Este test fija el flujo; el fallo real lo cubre Task 1.

- [ ] **Step 3:** `./mvnw -B -ntp -f src/pom.xml clean verify` → BUILD SUCCESS (gate JaCoCo incluido).

- [ ] **Step 4: commit** `test(replays): import de una serie privada de punta a punta`; push y PR a `develop` (`fix: import de replays privados de Showdown`).

### Task 5: release y vault

- [ ] **Step 1:** tras mergear el PR, `git log v1.8.0..origin/develop --oneline`: si solo hay fixes y dependencias, versión `1.8.1`; si hay features, `1.9.0`. PR `chore/version-x.y.z` a `develop`, luego PR `develop` → `main` con merge commit, y mergear el PR `sync/...` que abre `sync-develop` (proceso de `CLAUDE.md`).
- [ ] **Step 2: vault** (`git pull` antes): en `50 Features/Combates en Showdown.md`, sección fase 3, anotar que el id privado se recompone en el adaptador, que la reutilización compara ids base y los dos mensajes nuevos, con los PRs. En `60 Operaciones/Gotchas.md`: "Showdown devuelve el id del replay privado sin la contraseña (va en `password`)". Commit y push a `main` del vault.
