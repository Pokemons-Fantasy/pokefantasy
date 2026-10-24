# Combates en Showdown, fase 1 (vinculación y reto): plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada jugador vincula su cuenta de Pokémon Showdown, la liga configura su formato de combate y, desde el calendario, la web genera el `/challenge` al rival con las reglas de la liga.

**Architecture:** Back: puerto `ShowdownPort` (application) + adaptador HTTP `ShowdownApiAdapter` (infrastructure), llamado desde `UserFacade` **antes** del comando (ADR-007). Nuevos campos opcionales en `UserEntity`, `LeagueSettings` y las respuestas. Front: tipos manuales + `contract.ts`, la lógica del reto en `utils/showdown.ts` (pura, con test), una sección en el perfil, otra en la configuración y un modal en el calendario.

**Tech Stack:** Spring Boot + MongoDB (`MongoTemplate`), RestTemplate, JUnit 5 + Mockito + MockRestServiceServer, Testcontainers. React 19 + TS + React Query + Vitest.

**Spec:** `C:\PokeFantasy\vault\50 Features\Combates en Showdown.md` (secciones "Reglas de negocio" y "Fase 1").

## Global Constraints

- Ramas: back `feature/showdown-vinculacion` desde `develop` (PR a `develop`); front `feature/showdown-vinculacion` desde `main` (PR a `main`). Antes de crear ramas, revisar PRs abiertos en los dos repos y avisar al usuario.
- Compatibilidad en los dos sentidos: todos los campos nuevos son opcionales; `null` en `LeagueSettings` = valor por defecto; el front tolera que el back no los envíe.
- Defaults de combate: `battleGameType = DOUBLES`, `pickedTeamSize = 4`, `teamSheet = CLOSED`, `bestOf = 3`.
- `pickedTeamSize` entre 1 y 6 (6 = se juega con todos, sin regla). `bestOf` solo 1 o 3.
- Formatos de Showdown: `gen9customgame` (individual) y `gen9doublescustomgame` (dobles). Reglas: `Picked Team Size = N` solo si N < 6; `Best Of = 3` solo si `bestOf = 3`; separador `@@@` entre formato y reglas, reglas separadas por `, `.
- `showdownId` = `toID` de Showdown: minúsculas y solo `[a-z0-9]`. Nombre de Showdown: como máximo 18 caracteres.
- URL de usuario: `https://pokemonshowdown.com/users/{showdownId}.json`. 200 → `{username, userid, ...}`; 404 → no existe (verificado el 2026-10-24).
- Showdown caído o con timeout (5 s connect, 5 s read) → `ShowdownUnavailableException` → HTTP 502, `code` `SHOWDOWN_UNAVAILABLE`, mensaje "Showdown no responde, inténtalo en un rato".
- Mensajes de error, literales:
  - "Indica tu nombre de Showdown."
  - "El nombre de Showdown no puede tener más de 18 caracteres."
  - "No existe una cuenta registrada en Showdown con el nombre '<nombre>'."
  - "La cuenta de Showdown '<username canónico>' ya está vinculada a otro jugador."
  - "Los Pokémon por combate deben estar entre 1 y 6."
  - "El formato de la serie debe ser al mejor de 1 o de 3."
- Reglas del back: Command (record que implementa `Command`) → Handler → Facade → Controller; gate JaCoCo 80 %; `./mvnw -B -ntp -f src/pom.xml clean verify` antes de push.
- Reglas del front: llamadas solo en `src/api/*.ts`; tras cambiar DTOs, `npm run api:spec` + `npm run api:types` y commitear `openapi.json` + `src/api/schema.d.ts`; antes de push: `npm run lint`, `npm run api:check`, `npm test`, `npm run build`. Accesibilidad (labels, roles, foco en modales).
- Commits con `git commit -F <archivo>` (UTF-8 sin BOM), terminando en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Desvincular y volver a vincular a otro usuario:** con índice único sparse, guardar `showdownId: null` hace que dos usuarios desvinculados colisionen. Desvincular debe hacer `$unset` de los dos campos (test de integración en la Task 6).
2. **Nombres con espacios, mayúsculas o símbolos** ("Ash Ketchum!"): se busca `ashketchum` y se guarda el `username` que devuelve Showdown (test del facade en la Task 3).
3. **Volver a vincular el mismo nombre que ya tengo:** no es "ya vinculada a otro jugador" (test del handler en la Task 3).
4. **Liga antigua sin ajustes de combate:** `GET settings` devuelve los defaults y guardar la configuración desde un front viejo (sin esos campos) no rompe ni deja `null` raros (test del handler en la Task 5).
5. **Rival sin cuenta vinculada:** el modal no genera un reto con un nombre vacío; avisa y enlaza al perfil (test del modal en la Task 11).

---

## Backend (`C:\PokeFantasy\pokefantasy`)

Rutas relativas a `src/`. Paquete base `com.villu.pokefantasy`.

### Task 1: Puerto y adaptador de Showdown

**Files:**
- Create: `application/src/main/java/com/villu/pokefantasy/ports/ShowdownPort.java`
- Create: `application/src/main/java/com/villu/pokefantasy/ports/ShowdownUser.java`
- Create: `domain/src/main/java/com/villu/pokefantasy/exception/ShowdownUnavailableException.java`
- Create: `infrastructure/src/main/java/com/villu/pokefantasy/adapters/ShowdownApiAdapter.java`
- Modify: `api-rest/src/main/java/com/villu/pokefantasy/ApiExceptionHandler.java` (handler nuevo junto a `TooManyAttemptsException`)
- Test: `infrastructure/src/test/java/com/villu/pokefantasy/adapters/ShowdownApiAdapterTest.java`, `api-rest/src/test/java/com/villu/pokefantasy/ApiExceptionHandlerTest.java`

**Interfaces:**
- Produces: `public record ShowdownUser(String username, String userid)`; `public interface ShowdownPort { Optional<ShowdownUser> findUser(String showdownId); }`; `public class ShowdownUnavailableException extends RuntimeException` con constructor `(String message, Throwable cause)`.

- [ ] **Step 1: Tests del adaptador** (patrón de `PokemonApiAdapterTest`, constructor package-private con `RestTemplate`):
  - `findUser_existing_returnsCanonicalNameAndId`: `requestTo("https://pokemonshowdown.com/users/ashketchum.json")` responde `{"username":"Ash Ketchum","userid":"ashketchum","registertime":1,"group":1,"ratings":{}}` → `Optional.of(new ShowdownUser("Ash Ketchum", "ashketchum"))`.
  - `findUser_notFound_returnsEmpty`: `withStatus(HttpStatus.NOT_FOUND)` → `Optional.empty()`.
  - `findUser_serverError_throwsUnavailable`: `withServerError()` → `ShowdownUnavailableException`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest=ShowdownApiAdapterTest` → FAIL (no compila).
- [ ] **Step 3: Implementar `ShowdownApiAdapter implements ShowdownPort`** (`@Component`). `RestTemplate` propio con `SimpleClientHttpRequestFactory` (connect 5 s, read 5 s), sin reintentos. `HttpClientErrorException.NotFound` → `Optional.empty()`; cualquier otra `RestClientException` → `ShowdownUnavailableException("Showdown no responde, inténtalo en un rato", e)`. DTO de lectura privado (record con `username`, `userid`; ignorar el resto).
- [ ] **Step 4: Test del mapeo** en `ApiExceptionHandlerTest`: `ShowdownUnavailableException` → 502, `code` `SHOWDOWN_UNAVAILABLE`, `detail` igual al mensaje. Implementar el `@ExceptionHandler` con `respond(HttpStatus.BAD_GATEWAY, "SHOWDOWN_UNAVAILABLE", exception.getMessage())`.
- [ ] **Step 5:** `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure,api-rest -am -Dtest='ShowdownApiAdapterTest,ApiExceptionHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false` → PASS.
- [ ] **Step 6:** Commit `feat(showdown): puerto y adaptador para buscar usuarios`.

### Task 2: Campos de Showdown en el usuario y repositorio

**Files:**
- Modify: `domain/src/main/java/com/villu/pokefantasy/repository/entity/UserEntity.java`
- Modify: `domain/src/main/java/com/villu/pokefantasy/repository/UserRepository.java`
- Modify: `infrastructure/src/main/java/com/villu/pokefantasy/repository/UserRepositoryImpl.java`

**Interfaces:**
- Produces:
  - `UserEntity.showdownName` (`String`, el `username` canónico de Showdown) y `UserEntity.showdownId` (`String`, `@Indexed(unique = true, sparse = true)`).
  - `UserRepository`:
    - `void setShowdownAccount(String username, String showdownName, String showdownId)`: `$set` de los dos campos + `inc("version", 1)`, como `setAvatarVersion`.
    - `void clearShowdownAccount(String username)`: `$unset` de los dos + `inc("version", 1)`. **Nunca** `set(null)`, por el índice sparse (Review Focus 1).
    - `Optional<UserEntity> findByShowdownId(String showdownId)`.
  - `findByUsernames` incluye también `showdownName` en la proyección (actualizar su Javadoc).

- [ ] **Step 1:** Añadir campos y métodos con su Javadoc en español (estilo de `avatarVersion`). Comprobar que `UserEntity` ya está en `INDEXED_ENTITIES` de `MongoIndexInitializer` (lo está por `name`); si no, añadirla.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml clean package -DskipTests` → BUILD SUCCESS. Los tests de este repositorio son de integración y van en la Task 6.
- [ ] **Step 3:** Commit `feat(users): campos de la cuenta de Showdown`.

### Task 3: Vincular y desvincular la cuenta (comandos, facade, endpoints)

**Files:**
- Create: `application/src/main/java/com/villu/pokefantasy/commands/users/showdown/ShowdownIds.java`
- Create: `.../commands/users/showdown/LinkShowdownCommand.java`, `LinkShowdownCommandHandler.java`, `UnlinkShowdownCommand.java`, `UnlinkShowdownCommandHandler.java`
- Create: `domain/src/main/java/com/villu/pokefantasy/request/user/LinkShowdownRequest.java` (`@Data`, campo `name`)
- Modify: `application/.../commands/users/UserFacade.java`, `api-rest/.../UserController.java`
- Test: `application/src/test/java/com/villu/pokefantasy/commands/users/showdown/ShowdownIdsTest.java`, `ShowdownCommandHandlersTest.java`, `application/src/test/.../commands/users/UserFacadeTest.java`, `api-rest/src/test/.../DraftScheduleUserControllersTest.java`

**Interfaces:**
- Consumes: `ShowdownPort.findUser`, `ShowdownUser` (Task 1); `UserRepository.setShowdownAccount/clearShowdownAccount/findByShowdownId` (Task 2).
- Produces:
  - `ShowdownIds.toId(String name): String`: minúsculas, quita todo lo que no sea `[a-z0-9]`, `null` → `""`.
  - `record LinkShowdownCommand(String username, String showdownName, String showdownId) implements Command` → `Void`.
  - `record UnlinkShowdownCommand(String username) implements Command` → `Void`.
  - `UserFacade.linkShowdown(String username, String rawName): void` y `UserFacade.unlinkShowdown(String username): void`.
  - `PUT /v1/user/showdown` (body `{name}`) y `DELETE /v1/user/showdown`, los dos con 204.

- [ ] **Step 1: Tests** (Mockito puro):
  - `ShowdownIdsTest`: `toId("Ash Ketchum!") == "ashketchum"`, `toId("  ") == ""`, `toId(null) == ""`.
  - `ShowdownCommandHandlersTest`:
    - `link_free_savesAccount` → `verify(setShowdownAccount("ash", "Ash Ketchum", "ashketchum"))`.
    - `link_takenByOther_throwsIllegalState`: `findByShowdownId` devuelve un usuario `misty`; mensaje "La cuenta de Showdown 'Ash Ketchum' ya está vinculada a otro jugador.", sin guardar.
    - `link_sameUserAgain_saves` (Review Focus 3).
    - `unlink_clearsAccount`.
    - `commandType` de los dos.
  - `UserFacadeTest`:
    - `linkShowdown_lookupThenSend`: `port.findUser("ashketchum")` devuelve `ShowdownUser("Ash Ketchum","ashketchum")` → `mediator.send(new LinkShowdownCommand("ash","Ash Ketchum","ashketchum"))` (Review Focus 2).
    - `linkShowdown_blank_throwsWithoutLookup` ("Indica tu nombre de Showdown.").
    - `linkShowdown_tooLong_throws` (19 caracteres, mensaje de 18).
    - `linkShowdown_notRegistered_throws`, con el mensaje de "No existe…" usando el nombre tal como lo escribió el usuario.
  - Controller (`ControllerTestSupport`): `PUT /v1/user/showdown` con `{"name":"Ash"}` → 204 y `verify(userFacade).linkShowdown("user","Ash")`; `DELETE` → 204.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl application,api-rest -am -Dtest='ShowdownIdsTest,ShowdownCommandHandlersTest,UserFacadeTest,DraftScheduleUserControllersTest' -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3: Implementar.**
  - El facade valida longitud y vacío sobre el nombre recortado (`trim`), calcula el id, llama a `showdownPort.findUser` (fuera de la transacción) y envía el comando con el canónico.
  - El handler relee con `findByShowdownId` (seguro ante reintentos) y lanza `IllegalStateException` si pertenece a otro `name`. Una carrera la frena el índice, que da `DuplicateKeyException` → 409 genérico.
  - `UserFacade` recibe `ShowdownPort` por constructor.
- [ ] **Step 4:** El mismo comando que en el Step 2 → PASS.
- [ ] **Step 5:** Commit `feat(users): vincular y desvincular la cuenta de Showdown`.

### Task 4: `showdownName` en las respuestas del usuario y de los miembros

**Files:**
- Modify: `domain/.../response/CurrentUserResponse.java`, `domain/.../response/LeagueMemberResponse.java`
- Modify: `application/.../commands/users/me/GetCurrentUserCommandHandler.java`, `application/.../commands/league/GetLeagueDetailCommandHandler.java`
- Test: `GetCurrentUserCommandHandlerTest.java`, el test existente de `GetLeagueDetailCommandHandler`

**Interfaces:**
- Produces: `CurrentUserResponse.showdownName` y `LeagueMemberResponse.showdownName` (`String`, `null` = sin vincular).

- [ ] **Step 1: Tests.**
  - `handle_returnsShowdownName` (usuario con `showdownName "Ash Ketchum"` → respuesta igual; sin vincular → `null`).
  - En el detalle de liga, el miembro con cuenta trae su `showdownName` y el resto, `null`.
- [ ] **Step 2:** Ejecutarlos → FAIL.
- [ ] **Step 3:** Mapear el campo. En `GetLeagueDetailCommandHandler`, sustituir el `Map<String, Long> avatarVersions` por un `Map<String, UserEntity>` y leer de él los dos campos.
- [ ] **Step 4:** Ejecutarlos → PASS.
- [ ] **Step 5:** Commit `feat(users): nombre de Showdown en el usuario y los miembros`.

### Task 5: Ajustes de combate de la liga

**Files:**
- Create: `domain/src/main/java/com/villu/pokefantasy/dto/BattleGameType.java` (`SINGLES`, `DOUBLES`), `domain/.../dto/TeamSheet.java` (`OPEN`, `CLOSED`)
- Modify:
  - `domain/.../dto/LeagueSettings.java`: campos + `defaults()`
  - `domain/.../response/LeagueSettingsResponse.java`
  - `domain/.../request/league/UpdateLeagueSettingsRequest.java`
  - `application/.../commands/league/UpdateLeagueSettingsCommand.java`
  - `UpdateLeagueSettingsCommandHandler.java`
  - `GetLeagueSettingsCommandHandler.java`
  - `LeagueFacade.updateSettings`
  - `api-rest/.../LeagueController.java`
- Test: tests existentes de `UpdateLeagueSettingsCommandHandler`, `GetLeagueSettingsCommandHandler`, `LeagueControllersTest`

**Interfaces:**
- Produces: en `LeagueSettings`, `LeagueSettingsResponse`, `UpdateLeagueSettingsRequest` y el comando, los campos `BattleGameType battleGameType`, `Integer pickedTeamSize`, `TeamSheet teamSheet`, `Integer bestOf`, añadidos al final, después de `swapWindowCloseTime` (en el comando, antes de `requestingUsername`). `LeagueFacade.updateSettings` recibe los 4 en el mismo orden.

- [ ] **Step 1: Tests.**
  - Update:
    - `nullBattleFields_storeDefaults`: el comando sin los 4 campos guarda `DOUBLES, 4, CLOSED, 3` (Review Focus 4).
    - `pickedTeamSize0or7_throws` ("Los Pokémon por combate deben estar entre 1 y 6.").
    - `bestOf2_throws` ("El formato de la serie debe ser al mejor de 1 o de 3.").
    - `validBattleFields_stored` (`SINGLES, 6, OPEN, 1`).
  - Get: `legacySettingsWithoutBattleFields_returnDefaults`.
  - Controller: el PUT pasa los 4 campos al facade.
- [ ] **Step 2:** Ejecutarlos → FAIL.
- [ ] **Step 3: Implementar.**
  - Validar en el handler junto a las demás validaciones de rango.
  - Al guardar, aplicar el default cuando venga `null`, con el mismo patrón ternario que `stealWindowCloseDay`.
  - En `GetLeagueSettingsCommandHandler`, aplicar el default si el documento no tiene el campo.
  - `LeagueSettings.defaults()` incluye los 4.
- [ ] **Step 4:** Ejecutarlos → PASS.
- [ ] **Step 5:** Commit `feat(league): ajustes de combate (modo, Pokémon por combate, lista y serie)`.

### Task 6: Integración, verify y PR del back

**Files:**
- Create: `boot/src/test/java/com/villu/pokefantasy/it/ShowdownLinkIntegrationTest.java`

**Interfaces:**
- Consumes: todo lo anterior. `ShowdownApiAdapter` se mockea con `@MockitoBean` en esta clase, como `pokemonApiAdapter` en `IntegrationTest`.

- [ ] **Step 1: Test de integración** (con `client()` de `ApiClient`):
  - `linkUnlinkRelink`: `ash` vincula "Ash Ketchum" → `GET /v1/user/me` trae `showdownName`. `misty` intenta el mismo → 409 `CONFLICT`. `ash` desvincula, `brock` desvincula sin tener cuenta, `misty` vincula "Ash Ketchum" → 204. Esto cubre el `$unset` con el índice sparse (Review Focus 1).
  - `showdownDown_returns502`: el mock lanza `ShowdownUnavailableException` → 502 `SHOWDOWN_UNAVAILABLE`.
- [ ] **Step 2:** Con Docker abierto, `./mvnw -B -ntp -f src/pom.xml clean verify` → BUILD SUCCESS, gate JaCoCo incluido.
- [ ] **Step 3:** Commit `test(showdown): integración de la vinculación`.
- [ ] **Step 4:** Push y PR a `develop` por la API de GitHub. Título `feat: vincular cuenta de Showdown y ajustes de combate`. Cuerpo: resumen, endpoints nuevos, campos nuevos y la línea final `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

---

## Frontend (`C:\PokeFantasy\pokefantasy-web`)

Depende del back de las Tasks 1-5 **solo para regenerar los tipos**: el front funciona con el back viejo, porque los campos son opcionales.

### Task 7: Tipos y llamadas de la API

**Files:**
- Modify: `src/api/auth.ts`, `src/api/leagues.ts`, `src/api/contract.ts`, `openapi.json`, `src/api/schema.d.ts`

**Interfaces:**
- Produces:
  - `CurrentUser.showdownName?: string | null` y `LeagueMember.showdownName?: string | null`.
  - `linkShowdown(name: string): Promise<void>` (`PUT /v1/user/showdown`) y `unlinkShowdown(): Promise<void>` (`DELETE`), en `api/auth.ts`.
  - `export type BattleGameType = 'SINGLES' | 'DOUBLES'` y `export type TeamSheet = 'OPEN' | 'CLOSED'`.
  - `LeagueSettings`: `battleGameType?: BattleGameType; pickedTeamSize?: number; teamSheet?: TeamSheet; bestOf?: number`.

- [ ] **Step 1:** Arrancar el back de la rama con `API_DOCS_ENABLED=true` (y `SPRING_MAIN_LAZY_INITIALIZATION=true` si no hay Mongo ni Redis), luego `npm run api:spec` y `npm run api:types`.
- [ ] **Step 2:** Añadir los tipos y las funciones. Las entradas existentes de `contract.ts` ya cubren `CurrentUserResponse`, `LeagueMemberResponse` y los settings.
- [ ] **Step 3:** `./node_modules/.bin/tsc --noEmit` y `npm run api:check` → sin errores.
- [ ] **Step 4:** Commit `feat(api): tipos de Showdown y ajustes de combate`.

### Task 8: Lógica del reto (`utils/showdown.ts`)

**Files:**
- Create: `src/utils/showdown.ts`, `src/utils/showdown.test.ts`
- Modify: `src/pages/TeamsPage.tsx`, para que `exportTeamToShowdown` use `showdownTeamText`

**Interfaces:**
- Consumes: `LeagueSettings` (Task 7).
- Produces:
  - `BATTLE_DEFAULTS: { battleGameType: 'DOUBLES'; pickedTeamSize: 4; teamSheet: 'CLOSED'; bestOf: 3 }`.
  - `showdownFormat(settings: Partial<LeagueSettings>): string`: formato + reglas, aplicando `BATTLE_DEFAULTS` a lo que falte.
  - `challengeCommand(rivalShowdownName: string, settings: Partial<LeagueSettings>): string`.
  - `showdownTeamText(pokemonNames: string[]): string`: lo que hoy hace `exportTeamToShowdown` (capitalizar cada parte separada por `-`, bloques separados por línea en blanco).
  - `SHOWDOWN_PLAY_URL = 'https://play.pokemonshowdown.com/'`.

- [ ] **Step 1: Tests.**
  - `showdownFormat({})` → `'gen9doublescustomgame@@@Picked Team Size = 4, Best Of = 3'`.
  - `showdownFormat({battleGameType:'SINGLES', pickedTeamSize:6, bestOf:1})` → `'gen9customgame'`.
  - `showdownFormat({battleGameType:'SINGLES', pickedTeamSize:3, bestOf:1})` → `'gen9customgame@@@Picked Team Size = 3'`.
  - `showdownFormat({pickedTeamSize:6, bestOf:3})` → `'gen9doublescustomgame@@@Best Of = 3'`.
  - `challengeCommand('Ash Ketchum', {})` → `'/challenge Ash Ketchum, gen9doublescustomgame@@@Picked Team Size = 4, Best Of = 3'`.
  - `showdownTeamText(['urshifu-rapid-strike','pikachu'])` → `'Urshifu-Rapid-Strike\n\nPikachu'`.
- [ ] **Step 2:** `npx vitest run src/utils/showdown.test.ts` → FAIL. Con `rtk`, usar `rtk proxy npx vitest ...`: rtk oculta la salida de vitest.
- [ ] **Step 3:** Implementar y sustituir el cuerpo de texto de `exportTeamToShowdown` en `TeamsPage` por `showdownTeamText(picks.map((p) => p.pokemonName))`.
- [ ] **Step 4:** `npx vitest run src/utils/showdown.test.ts src/pages/TeamsPage.test.tsx` → PASS.
- [ ] **Step 5:** Commit `feat(showdown): generar el reto con las reglas de la liga`.

### Task 9: Sección Showdown en el perfil

**Files:**
- Create: `src/components/ShowdownAccountSection.tsx`, `src/components/ShowdownAccountSection.test.tsx`
- Modify: `src/pages/MyProfilePage.tsx` (antes de "Foto de perfil", con `section-label` "Showdown"), `src/pages/PlayerProfilePage.tsx`

**Interfaces:**
- Consumes: `getMe` (query `['me']`), `linkShowdown`, `unlinkShowdown` (Task 7); `league-detail` para `PlayerProfilePage`.

- [ ] **Step 1: Tests** (patrón de `ChangePasswordForm.test.tsx`).
  - Sin vincular: input con label "Nombre en Showdown" y botón "Vincular". Al enviar "Ash" se llama a `linkShowdown('Ash')`, se invalida `['me']` y sale un toast de éxito.
  - Vinculado (`showdownName: 'Ash Ketchum'`): se muestra el nombre y el botón "Desvincular" llama a `unlinkShowdown`.
  - Si falla, toast con `extractErrorMessage`.
  - Botón deshabilitado con el input vacío o mientras la mutación está en curso.
  - Texto de ayuda: "Tiene que ser una cuenta registrada en Showdown. Se usa para retar a tu rival e importar los resultados."
- [ ] **Step 2:** `npx vitest run src/components/ShowdownAccountSection.test.tsx` → FAIL.
- [ ] **Step 3:** Implementar e integrar.
  - En `PlayerProfilePage`, bajo el nombre del jugador, si el miembro de `league-detail` tiene `showdownName`, mostrar "Showdown: <nombre>".
  - Al vincular o desvincular, invalidar también `['league-detail']` (prefijo, todas las ligas).
- [ ] **Step 4:** `npx vitest run src/components/ShowdownAccountSection.test.tsx src/pages/MyProfilePage.test.tsx` → PASS.
- [ ] **Step 5:** Commit `feat(perfil): vincular la cuenta de Showdown`.

### Task 10: Sección "Combates" en la configuración de la liga

**Files:**
- Create: `src/components/leagueConfig/BattleSection.tsx`
- Modify: `src/pages/LeagueConfigPage.tsx` (`DEFAULT_SETTINGS` y fusión campo a campo con `BATTLE_DEFAULTS`; sección después de `CalendarSection`)
- Test: `src/pages/LeagueConfigPage.test.tsx`

**Interfaces:**
- Consumes: `SectionProps` (`components/leagueConfig/types.ts`), `BATTLE_DEFAULTS` (Task 8).

- [ ] **Step 1: Tests.**
  - Con settings del back sin campos de combate, se muestran Dobles / 4 / Lista cerrada / Al mejor de 3.
  - Al cambiar a Individual + "Todos (6)" + Lista abierta + "Una partida" y guardar, `updateLeagueSettings` recibe `battleGameType:'SINGLES', pickedTeamSize:6, teamSheet:'OPEN', bestOf:1`.
- [ ] **Step 2:** `npx vitest run src/pages/LeagueConfigPage.test.tsx` → FAIL.
- [ ] **Step 3: Implementar `BattleSection`** (mismo marcado que `CalendarSection`: `config-section`, `config-field`, `config-label`, `search-input`, hints).
  - Modo: select con "Dobles" e "Individual".
  - Pokémon por combate: select con "Todos (6)", 5, 4, 3, 2, 1. Hint: "Se llevan 6 y se eligen estos en el Team Preview."
  - Lista: select con "Cerrada (el rival solo ve los Pokémon)" y "Abierta (el rival ve el equipo completo)".
  - Serie: select con "Al mejor de 3" y "Una partida".
- [ ] **Step 4:** `npx vitest run src/pages/LeagueConfigPage.test.tsx` → PASS.
- [ ] **Step 5:** Commit `feat(config): ajustes de combate de la liga`.

### Task 11: Modal "Jugar en Showdown" en el calendario, verificación y PR

**Files:**
- Create: `src/components/schedule/PlayShowdownModal.tsx`, `src/components/schedule/PlayShowdownModal.test.tsx`
- Modify: `src/components/schedule/MatchRow.tsx`, `src/components/schedule/JornadaCard.tsx` (prop nueva `onPlay`), `src/pages/SchedulePage.tsx`

**Interfaces:**
- Consumes:
  - `challengeCommand`, `showdownTeamText`, `SHOWDOWN_PLAY_URL` (Task 8);
  - `league-detail` (ya cargado en `SchedulePage`), para los `showdownName` de los miembros;
  - `['league-settings', leagueId]` con `getLeagueSettings`, la misma clave que `DraftPage`, con `staleTime: 5 * 60_000`;
  - `['draft-status', leagueId]` + `deriveTeams`, para mis Pokémon.
- Produces: `PlayShowdownModal({ rival, myShowdownName, rivalShowdownName, settings, myPokemonNames, onClose })`.

- [ ] **Step 1: Tests.**
  - `PlayShowdownModal`:
    - Con las dos cuentas vinculadas muestra el comando exacto de `challengeCommand`.
    - "Copiar reto" llama a `Clipboard.write({ string: <comando> })`.
    - "Copiar mi equipo" copia `showdownTeamText(myPokemonNames)`.
    - "Abrir Showdown" es un enlace a `SHOWDOWN_PLAY_URL` con `target="_blank"` y `rel="noopener noreferrer"`.
    - Se muestra la guía en lista ordenada (`<ol>`), con estos textos literales:
      1. "En Showdown, abre el Teambuilder, crea un equipo nuevo y pega tu equipo con «Import from text»."
      2. "Completa movimientos, objetos, habilidades y stats, y elige el formato <Doubles Custom Game | Custom Game>." El formato sale de `battleGameType`.
      3. "Quien reta pega el reto en el chat de Showdown y elige su equipo; el rival elige el suyo al aceptar."
      4. "En el Team Preview, cada uno elige sus <N> Pokémon." Solo si `pickedTeamSize < 6`.
    - Se muestra el aviso "Al terminar cada partida, pulsa «Upload and share replay» en Showdown: sin replay no se podrá importar el resultado."
    - Si falta `rivalShowdownName`: "<rival> no ha vinculado su cuenta de Showdown.", y no hay botón de copiar reto (Review Focus 5).
    - Si falta `myShowdownName`: "Vincula tu cuenta de Showdown en tu perfil." con enlace a `/profile`.
    - `role="dialog"`, `aria-labelledby` y foco inicial en el modal.
  - `SchedulePage.test.tsx`: en mi partido pendiente aparece "Jugar en Showdown" (también si no soy admin); no aparece en partidos ajenos ni completados.
- [ ] **Step 2:** `npx vitest run src/components/schedule/PlayShowdownModal.test.tsx src/pages/SchedulePage.test.tsx` → FAIL.
- [ ] **Step 3:** Implementar.
  - `MatchRow` muestra el botón con `mine && !completed`; `JornadaCard` reenvía `onPlay`.
  - El modal usa la clase `modal-overlay` / `modal` existente y Motion respetando `useReducedMotion`, como los modales actuales.
- [ ] **Step 4:** `npx vitest run src/components/schedule src/pages/SchedulePage.test.tsx` → PASS.
- [ ] **Step 5:** Commit `feat(calendario): jugar el partido en Showdown`.
- [ ] **Step 6:** `npm run lint`, `npm run api:check`, `npm test`, `npm run build` → todos en verde.
- [ ] **Step 7:** Push y PR a `main` de `Pokemons-Fantasy/pokefantasy-web`, con título `feat: vincular Showdown, ajustes de combate y reto desde el calendario` y el enlace al PR del back en el cuerpo, terminando en `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

### Task 12: Vault

- [ ] **Step 1:** `git pull` en `C:\PokeFantasy\vault`. En `50 Features/Combates en Showdown.md`:
  - `estado: en-curso`;
  - `prs_back` y `prs_front` con los números;
  - el enlace "Plan fase 1" a este archivo.
- [ ] **Step 2:** `20 Arquitectura/API REST.md`: `PUT` y `DELETE /v1/user/showdown`, `showdownName` en `/v1/user/me` y en los miembros, y los 4 ajustes de liga. `20 Arquitectura/Modelo de datos.md`: `showdownName` y `showdownId` en `UserEntity`, los 4 campos de `LeagueSettings` y el puerto de Showdown en el diagrama de `Visión general.md`.
- [ ] **Step 3:** Commit y push a `main` del vault.
