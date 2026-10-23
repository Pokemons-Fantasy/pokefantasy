# Normas de uso y moderación (L5): plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Panel `/admin` para que el rol global `ADMIN` quite fotos, renombre ligas y borre usuarios, y página pública `/terms` con las normas de uso.

**Architecture:**
- Back: `AdminController` bajo `/v1/admin/**` (solo `ADMIN` en `SecurityConfig`) → `AdminFacade` → comandos nuevos.
- El borrado sin contraseña reutiliza el del L2, extraído a `AccountDeletionService`. `LeagueNamePolicy` es la regla común al crear y al renombrar ligas.
- Front: `AdminPage` con dos buscadores y `TermsPage`.

**Tech Stack:** Spring Boot 4.1 / MongoTemplate, JUnit 5 + Mockito, IT con Testcontainers; React 19 + React Query + vitest.

**Spec:** `docs/superpowers/specs/2026-10-18-moderacion-normas-design.md`

## Global Constraints

- Ramas apiladas sobre L2:
  - back `feature/moderacion` desde `feature/borrado-cuenta` (#178), con PR contra esa rama;
  - front `feature/moderacion` desde `feature/borrado-cuenta` del front (#128), con PR contra esa rama.
  - Al mergear L2, se cambia la base a `develop` y a `main`.
- `LeagueNamePolicy`: recortar espacios; entre 1 y 40 caracteres. Mensaje exacto: `El nombre de la liga debe tener entre 1 y 40 caracteres.`
- Búsquedas: como mucho 20 resultados. Usuarios por prefijo (`findByUsernamePrefix`); ligas por "contiene" sin distinguir mayúsculas, con `Pattern.quote(q)`.
- Borrar a un `ADMIN` desde el panel: `ForbiddenOperationException("No se puede borrar a otro administrador.")` → 403 `FORBIDDEN`.
- Cada acción de admin: `log.info("Admin {} <acción> {}", admin, objetivo)` en su handler.
- Reglas del `CLAUDE.md` del back: comando, handler y facade; el controller solo usa la facade; los efectos tras el commit van en el controller, como en el borrado propio.
- Glosario del front (`src/glossary.test.ts`): "banquillo", nunca "banca".
- `JAVA_HOME=E:\jdk-23.0.1`, Docker abierto para los IT, y en el front `rtk proxy npx vitest run`.

## Review Focus

1. Un `USER` con un JWT válido llama a cualquier `/v1/admin/**`: 403, y no se ejecuta nada (IT en Task 4).
2. Renombrar con espacios alrededor o con 41 caracteres: se guarda recortado, o 400 sin cambios (tests en Task 1).
3. El admin borra a un usuario conectado: sus sesiones y su SSE se cortan como en el borrado propio (test del controller en Task 4).
4. Buscar con caracteres especiales de regex (`.*`, `(`): no rompe la consulta ni devuelve todo (test en Task 3).
5. Un admin abre `/admin` y su sesión caduca: el 401 lleva al login como en el resto de páginas, sin pantalla en blanco (test en Task 6).

---

### Task 1: Back: `LeagueNamePolicy` y `RenameLeagueCommand`

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/league/LeagueNamePolicy.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/admin/RenameLeagueCommand.java` (`record(String adminUsername, String leagueId, String name)`) y su handler
- Modify: `CreateLeagueCommandHandler`: usa `LeagueNamePolicy.normalize` y guarda el nombre recortado
- Test: `LeagueNamePolicyTest`, `RenameLeagueCommandHandlerTest`, `CreateLeagueCommandHandlerTest` (nombre recortado y 41 caracteres)

**Interfaces (Produces):** `static String LeagueNamePolicy.normalize(String name)`. `RenameLeagueCommandHandler implements CommandHandler<RenameLeagueCommand, Void>`: `findById` (o `IllegalArgumentException("Liga no encontrada")`), `setName(normalize(name))`, `save`.

- [ ] **Step 1: Tests**
  - `normalize_trims`: `"  Kanto  "` → `"Kanto"`.
  - `normalize_rejectsBlankAndTooLong`: `" "`, `null` y 41 caracteres dan `IllegalArgumentException` con el mensaje exacto. 40 caracteres valen.
  - `rename_savesTheNormalizedName`.
  - `rename_unknownLeague_isNotFound`.
  - `rename_invalidName_doesNotSave`.
  - `create_trimsTheName` y `create_tooLongName_isRejected`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest='LeagueNamePolicyTest,RenameLeagueCommandHandlerTest,CreateLeagueCommandHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false` → FAIL (no compila).
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** Commit `feat(admin): renombrar liga y LeagueNamePolicy (L5)`.

### Task 2: Back: `AccountDeletionService` y `AdminDeleteUserCommand`

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/users/delete/AccountDeletionService.java` (`@Service`)
- Modify: `DeleteAccountCommandHandler`: comprueba la contraseña y delega
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/admin/AdminDeleteUserCommand.java` (`record(String adminUsername, String targetUsername)`) y su handler
- Test: los casos actuales de `DeleteAccountCommandHandlerTest` que no son de contraseña pasan a `AccountDeletionServiceTest`. El test del handler conserva los de contraseña y añade `handle_validPassword_delegatesToTheService`. Nuevo `AdminDeleteUserCommandHandlerTest`.

**Interfaces (Produces):**
- `List<String> AccountDeletionService.delete(String username)`: reserva del nombre, ligas, anonimización, foto, sesiones y usuario, en el mismo orden que hoy.
- `AdminDeleteUserCommandHandler implements CommandHandler<AdminDeleteUserCommand, List<String>>`:
  - usuario inexistente → `IllegalArgumentException("Usuario no encontrado")`;
  - `role == ADMIN` → `ForbiddenOperationException("No se puede borrar a otro administrador.")`;
  - si no, `delete(target)`.

- [ ] **Step 1: Tests** (los movidos, sin cambiar sus aserciones, más los nuevos):
  - `adminDelete_unknownUser_isNotFound`.
  - `adminDelete_otherAdmin_isForbidden_andDeletesNothing`.
  - `adminDelete_regularUser_delegatesAndReturnsTheRemainingLeagues`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest='AccountDeletionServiceTest,DeleteAccountCommandHandlerTest,AdminDeleteUserCommandHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3:** Implementar. Refactor del borrado sin cambiar su comportamiento: el IT de L2 debe seguir en verde.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** Commit `refactor(cuenta): AccountDeletionService y borrado por el admin (L5)`.

### Task 3: Back: búsquedas del admin

**Files:**
- Modify: `LeagueRepository` y su impl: `List<LeagueEntity> findByNameContaining(String text, int limit)`, con regex `Pattern.quote(text)`, flag `i`, orden por `name` y `limit`
- Create en `src/application/.../commands/admin/`: `SearchUsersForAdminCommand(String q)` → `List<AdminUserResponse>` y `SearchLeaguesForAdminCommand(String q)` → `List<AdminLeagueResponse>`, con sus handlers
- Create en `src/domain/.../response/`: `AdminUserResponse(username, avatarVersion, admin)` y `AdminLeagueResponse(id, name, members)` (records)
- Test: `AccountDeletionRepositoriesTest` (o `LeagueRepositoryImplTest`): `findByNameContaining_quotesTheText`, donde la query lleva `\Q.*\E` para `q=".*"`. Más los tests de los dos handlers.

- [ ] **Step 1: Tests:**
  - `q` en blanco o de menos de 2 caracteres → lista vacía, sin consultar.
  - Resultados mapeados.
  - Regex citada.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure,application -am -Dtest='LeagueRepositoryImplTest,SearchUsersForAdminCommandHandlerTest,SearchLeaguesForAdminCommandHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** Commit `feat(admin): busquedas de usuarios y ligas (L5)`.

### Task 4: Back: `AdminController`, seguridad, `me.admin` e IT

**Files:**
- Create: `src/application/.../commands/admin/AdminFacade.java`: `searchUsers`, `searchLeagues`, `removeAvatar(admin, target)`, `renameLeague`, `deleteUser`. Un `send` por método. `removeAvatar` manda `DeleteAvatarCommand(target)` y deja el log en la facade, porque el handler de avatar es compartido.
- Create: `src/api-rest/.../AdminController.java` (`@RequestMapping("/v1/admin")`). En `deleteUser`, tras la facade: `userFacade.revokeSessions(target)`, `userSseRegistry.closeAll(target)` y `realtimeNotifier.draftUpdated` por liga.
- Create: `src/api-rest/.../request/admin/RenameLeagueRequest.java` (`name`)
- Modify: `SecurityConfig`: `requestMatchers(request -> request.getServletPath().startsWith("/v1/admin/")).hasRole("ADMIN")`, antes de `anyRequest()`
- Modify: `CurrentUserResponse` (+ `admin`) y `GetCurrentUserCommandHandler`
- Create: `src/boot/src/test/java/com/villu/pokefantasy/it/AdminIntegrationTest.java`. El admin se crea registrándolo y poniendo `role=ADMIN` con `mongoTemplate`.
- Test: el controller con `ControllerTestSupport` y la facade
- Modify: `CLAUDE.md` del back. Tabla 2: `AccountDeletionService` (borrado de cuenta, propio o por el admin), `LeagueNamePolicy` y la moderación (`AdminController` → `AdminFacade`). Seguridad: `/v1/admin/**` solo `ADMIN`.

- [ ] **Step 1: Tests**
  - Controller `adminDeleteUser_revokesSessionsClosesSseAndNotifies`.
  - Controller `renameLeague_204`.
  - IT `user_gets403OnEveryAdminEndpoint`: los cinco endpoints, y la liga sigue con su nombre.
  - IT `admin_searchesRemovesPhotoRenamesAndDeletes`.
  - IT `admin_cannotDeleteAnotherAdmin`.
  - IT `me_reportsAdmin`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl boot -am -Dtest='AdminIntegrationTest,AdminControllerTest,AdminFacadeTest' -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `./mvnw -B -ntp -f src/pom.xml clean verify` con Docker → BUILD SUCCESS, IT sin saltados, cobertura OK.
- [ ] **Step 5:** Commit `feat(admin): panel de moderacion en la API (L5)`. Push y PR contra `feature/borrado-cuenta`.

### Task 5: Front: normas de uso

**Files:**
- Create: `src/pages/TermsPage.tsx` y su test. Ruta pública `/terms` con `title('Normas')`. Formato de `AboutPage`. Contenido: los 6 puntos de la spec, con el contacto de `src/legal.ts`.
- Modify: `RegisterPage` y su test. Texto: "Tengo 14 años o más y he leído la [política de privacidad] y las [normas de uso]".
- Modify: `FanDisclaimer` y su test (enlace "Normas" a `/terms`), y `AboutPage` (enlace).

- [ ] **Step 1: Tests:**
  - `TermsPage` contiene "Sé respetuoso", "borrar cuentas", el `mailto:` y "Volver".
  - La casilla del registro enlaza `/privacy` y `/terms`.
  - `FanDisclaimer` enlaza `/terms`.
- [ ] **Step 2:** `rtk proxy npx vitest run src/pages/TermsPage.test.tsx src/pages/RegisterPage.test.tsx src/components/FanDisclaimer.test.tsx` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** Commit `feat(legal): normas de uso (L5)`.

### Task 6: Front: panel `/admin`

**Files:**
- Tipos de la API: con el back de Task 4 arrancado (`API_DOCS_ENABLED=true`, `SPRING_MAIN_LAZY_INITIALIZATION=true`, `JWT_SECRET` de prueba), `npm run api:spec` y `npm run api:types`. Commitear `openapi.json` y `src/api/schema.d.ts`.
- Create: `src/api/admin.ts`, con `searchUsers(q)`, `searchLeagues(q)`, `removeAvatar(username)`, `renameLeague(id, name)`, `deleteUser(username)` y los tipos `AdminUser` y `AdminLeague`. Entradas en `contract.ts`. `CurrentUser` gana `admin?: boolean`.
- Create: `src/pages/AdminPage.tsx` y su test. Ruta `/admin` dentro de `ProtectedRoute`, con `title('Administración')`.
- Create: `src/utils/leagueName.ts` y su test: `leagueNameError(name)`, con la misma regla de 1-40 caracteres tras recortar.
- Modify: `MyProfilePage` y su test: enlace "Panel de administración" solo si `me.admin`.

Comportamiento: lo de la spec. Las búsquedas con `useQuery` (clave `['admin-users', q]` y `['admin-leagues', q]`) solo con `q.trim().length >= 2`. Las acciones con `useMutation`, que al terminar invalida la búsqueda y muestra un toast. Las confirmaciones con `ConfirmDialog` (`danger`). Renombrar en un `ConfirmDialog` con un campo, o en un modal pequeño con el mismo patrón de foco y Escape.

- [ ] **Step 1: Tests:**
  - `redirects non admins to /`.
  - `searches users and removes a photo after confirming` (sin foto, no hay botón).
  - `does not offer deleting another admin`.
  - `deletes a user after confirming`.
  - `renames a league and rejects 41 characters`.
  - `a 401 goes to login` (Review Focus 5): con `onSessionExpired`, como en el resto de páginas.
  - `leagueNameError` con los mismos casos que `LeagueNamePolicy`.
  - `MyProfilePage`: el enlace solo con `admin: true`.
- [ ] **Step 2:** `rtk proxy npx vitest run src/pages/AdminPage.test.tsx src/utils/leagueName.test.ts src/pages/MyProfilePage.test.tsx` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `rtk proxy npx vitest run`, `tsc -p tsconfig.app.json --noEmit`, `npm run api:check`, `npm run lint` y `npm run build` → en verde.
- [ ] **Step 5:** Commit `feat(admin): panel de moderacion (L5)`. Push y PR contra `feature/borrado-cuenta`.

### Task 7: Vault y revisión final

- [ ] **Step 1:** Vault:
  - nota nueva `50 Features/Moderación.md` (`estado: en-curso`, PRs);
  - `API REST.md` (las cinco rutas de admin y `me.admin`);
  - `Seguridad y auth.md` (rol `ADMIN` y `/v1/admin/**`);
  - auditoría L5 con los PRs;
  - `Estructura frontend.md` (`/terms` y `/admin`).
  - `git pull`, commit y push.
- [ ] **Step 2:** Revisión de toda la rama, back y front, con un revisor nuevo (Sonnet). Una sola pasada de arreglos para lo Critical o Important.
