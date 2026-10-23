# Borrado de cuenta y privacidad (L2): plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El usuario borra su cuenta desde Mi perfil, y la web publica la política de privacidad con la edad mínima.

**Architecture:**
- Back: un `DeleteAccountCommand` por el mediator, en una transacción. Saca al usuario de cada liga con `LeagueMemberRemoval`, un servicio extraído de la expulsión. Si deja una liga vacía la borra, si era el único admin le pasa el rol al siguiente, anonimiza su nombre y borra usuario, foto y sesiones.
- Front: modal de borrado en Mi perfil, casilla de edad y privacidad en el registro, y página `/privacy`.

**Tech Stack:** Spring Boot 4.1 / Spring Data MongoDB (MongoTemplate), JUnit 5 + Mockito + AssertJ, IT con Testcontainers; React 19 + React Query + vitest.

**Spec:** `docs/superpowers/specs/2026-10-18-borrado-cuenta-privacidad-design.md`

## Global Constraints

- Nombre anónimo: `DeletedUser.NAME = "Usuario eliminado"` (`application/.../users/DeletedUser.java`). El front no lo conoce: lo pinta tal cual llega.
- Endpoint: `DELETE /v1/users/me` con cuerpo `{ "password": "..." }`. Responde 204 y borra las cookies `jwt` y `refresh`. No va en `/v1/user`, que es ruta pública.
- La contraseña usa el mismo límite que `ChangePasswordCommandHandler`: clave `user:<minúsculas>`, `LoginUserCommandHandler.MAX_FAILURES_PER_USER` y `LOCKOUT_WINDOW`.
- Nuevo admin: el primer miembro de `league.getMembers()` que quede tras quitar al usuario.
- Liga vacía: se borran la liga, todos sus drafts, el calendario, los trades, los eventos de actividad y la lista cerrada. Las invitaciones en Redis caducan solas: al canjear una, la liga ya no existe.
- Reglas del `CLAUDE.md` del back: comando + handler + facade, el controller solo usa la facade, sin `save()` de datos leídos fuera de la transacción, y `@Version` en las entidades principales.
- Ramas: back `feature/borrado-cuenta` (ya existe y lleva la spec), PR a `develop`. Front `feature/borrado-cuenta` desde `main` **después de mergear el front #127** (trae `src/legal.ts`, `FanDisclaimer` y `/about`), PR a `main`. Commits con `git commit -F <archivo>`.
- Back: `JAVA_HOME=E:\jdk-23.0.1` para `mvnw`, y Docker abierto para los IT.

## Decisiones que se apartan de la spec

- **Anonimización por documento en las estructuras anidadas.** La spec pedía "update por consulta" en todos los casos. Eso vale para `ActivityEventEntity` y `TradeEntity`, que son planos. Los partidos (`jornadas[].matches[]`) y los picks de los drafts anteriores (`picks[]`) son arrays anidados: se leen dentro de la transacción, se cambian y se guardan con su `@Version`. La regla 6 prohíbe guardar lo leído *antes* de la transacción, no dentro.
- **`revokeAll` dentro del handler**, como ya hace `ChangePasswordCommandHandler`, y no después del commit. Si la transacción de Mongo fallase después, el usuario solo tendría que volver a entrar. No hace falta un mecanismo nuevo.

## Review Focus

1. Usuario sin ligas: el borrado funciona igual (test en Task 3).
2. Una cookie `jwt` emitida antes del borrado da 401 en la siguiente petición, no 500 (IT en Task 4).
3. Alguien se registra después con el mismo nombre: empieza sin ligas ni datos del anterior (IT en Task 4).
4. Cinco contraseñas mal seguidas: 429, y el borrado no toca nada (test en Task 3).
5. Su turno en un draft en curso: el turno pasa al siguiente, como en la expulsión (los tests actuales de la expulsión pasan a `LeagueMemberRemoval` en Task 2 y se ejecutan con el mismo caso).

---

### Task 1: Back: métodos de repositorio para borrar y anonimizar

**Files:**
- Modify (interfaces en `src/domain/src/main/java/com/villu/pokefantasy/repository/`): `UserRepository`, `LeagueRepository`, `ScheduleRepository`, `TradeRepository`, `ActivityEventRepository`, `ClosedListRepository`
- Modify (impl en `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/`): `UserRepositoryImpl`, `LeagueRepositoryImpl`, `ScheduleRepositoryImpl`, `TradeRepositoryImpl`, `ActivityEventMongoRepository`, `ClosedListRepositoryImpl`
- Test: los `*RepositoryImplTest` de `src/infrastructure/src/test/java/com/villu/pokefantasy/repository/` (crear los que no existan: `TradeRepositoryImplTest`, `ScheduleRepositoryImplTest`). Estilo de `DraftRepositoryImplTest`: `MongoTemplate` mockeado y `ArgumentCaptor<Query>`/`<Update>`.

**Interfaces (Produces):**
- `void UserRepository.deleteByUsername(String username)`
- `void LeagueRepository.deleteById(String leagueId)`
- `void ScheduleRepository.deleteByLeagueId(String leagueId)`
- `void TradeRepository.deleteByLeagueId(String leagueId)`
- `void TradeRepository.cancelPending(String leagueId, String username, Instant resolvedAt)`: los `PENDING` de esa liga donde es `proposer` o `responder` pasan a `CANCELLED` con `resolvedAt`.
- `void TradeRepository.anonymizeUser(String leagueId, String username, String replacement)`: dos `updateMulti`, uno sobre `proposer` y otro sobre `responder`.
- `void ActivityEventRepository.deleteByLeagueId(String leagueId)`
- `void ActivityEventRepository.anonymizeUser(String leagueId, String username, String replacement)`: igual, sobre `actorUsername` y `targetUsername`.
- `void ClosedListRepository.deleteAllByLeagueId(String leagueId)`

- [ ] **Step 1: Tests.** Uno por método. Comprueban la colección, que la consulta filtra por `leagueId` (o por `name`/`_id`) y el usuario, y el `Update` exacto. Por ejemplo:
  - `TradeRepositoryImplTest.cancelPending_setsCancelledOnlyOnThisLeaguesPendingTradesOfTheUser`: la query contiene `leagueId=l1`, `status=PENDING` y `$or` con `proposer`/`responder` igual a `ash`; el update hace `$set status=CANCELLED, resolvedAt=<instant>`; se usa `updateMulti`.
  - `ActivityEventMongoRepositoryTest.anonymizeUser_rewritesActorAndTargetInThatLeague`: dos `updateMulti`, con `{leagueId:l1, actorUsername:ash} → $set actorUsername="Usuario eliminado"` y lo mismo para `targetUsername`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest='*RepositoryImplTest,ActivityEventMongoRepositoryTest' -Dsurefire.failIfNoSpecifiedTests=false` → falla al compilar (los métodos no existen).
- [ ] **Step 3:** Implementar con `mongoTemplate.remove(query, Entity.class)` y `mongoTemplate.updateMulti(query, update, Entity.class)`.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** Commit `feat(cuenta): repositorios para borrar ligas y anonimizar usuarios (L2)`.

### Task 2: Back: extraer `LeagueMemberRemoval`

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/league/LeagueMemberRemoval.java` (`@Service`)
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/league/RemoveMemberFromLeagueCommandHandler.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/league/LeagueMemberRemovalTest.java` y el test del handler de expulsión

**Interfaces (Produces):** `void LeagueMemberRemoval.remove(LeagueEntity league, String username)`: el cuerpo actual del handler desde `leagueRepository.removeMember(...)` hasta el final, incluidos el draft activo, `draftTurnService.removePlayer` y `draftCompletionService.complete`. Mismas dependencias que hoy tiene el handler (`LeagueRepository`, `DraftRepository`, `ClosedListRepository`, `DraftTurnService`, `DraftCompletionService`).

- [ ] **Step 1:** Mover a `LeagueMemberRemovalTest` los tests actuales del handler que comprueban los picks, los turnos y el cierre del draft, sin cambiar sus aserciones. En el test del handler quedan los de permisos (no admin, no miembro, último admin) y uno nuevo, `handle_valid_delegatesToLeagueMemberRemoval`, que verifica `remove(league, "brock")`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest='LeagueMemberRemovalTest,RemoveMemberFromLeagueCommandHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false` → falla al compilar.
- [ ] **Step 3:** Crear el servicio y hacer que el handler delegue en él. Refactor puro: la expulsión no cambia de comportamiento.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** Commit `refactor(ligas): LeagueMemberRemoval como dueño de sacar a un miembro`.

### Task 3: Back: `DeleteAccountCommand`

**Files:**
- Create en `src/application/src/main/java/com/villu/pokefantasy/commands/users/delete/`: `DeleteAccountCommand.java` (record, implementa `Command`) y `DeleteAccountCommandHandler.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/users/DeletedUser.java`, con `public static final String NAME = "Usuario eliminado"`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/users/delete/DeleteAccountCommandHandlerTest.java`

**Interfaces:**
- Consumes: Task 1 y Task 2. Además: `LoginAttemptPort`, `PasswordHashPort`, `RefreshTokenPort.revokeAll`, `AvatarRepository.deleteByUsername`, `DraftRepository.findAllByLeagueIdsNewestFirst`/`delete`, `UserRepository.findByUsername`, `LeagueRepository.findByMemberUsername`/`findById`/`save`.
- Produces: `record DeleteAccountCommand(String username, String password) implements Command`. El handler es `CommandHandler<DeleteAccountCommand, List<String>>` y devuelve los `leagueId` de las ligas que siguen existiendo.

Orden dentro del handler:
1. Valida que llegue contraseña (`IllegalArgumentException("Indica tu contraseña.")`).
2. Comprueba el límite y la contraseña (Global Constraints).
3. Para cada liga: si es el único miembro, la borra entera. Si no:
   - `LeagueMemberRemoval.remove`;
   - relee la liga; si no le queda admin, el primero pasa a `ADMIN`; si `createdBy` era él, pasa a `DeletedUser.NAME`; la guarda;
   - `cancelPending` y luego los `anonymizeUser` de trades y actividad;
   - anonimiza el calendario y los drafts anteriores leyéndolos y guardándolos.
4. Borra la foto, revoca las sesiones (`revokeAll`) y borra el usuario.

- [ ] **Step 1: Tests**
  - `handle_wrongPassword_recordsFailureAndChangesNothing`: `BadCredentialsException`, `recordFailure("user:ash", LOCKOUT_WINDOW)`, y sin interacciones con `LeagueMemberRemoval` ni con los repositorios de borrado.
  - `handle_userAtFailureLimit_isTooManyAttempts` (Review Focus 4).
  - `handle_userWithoutLeagues_deletesAvatarSessionsAndUser` (Review Focus 1).
  - `handle_leagueWithOthers_removesAnonymizesAndReturnsItsId`: `remove(league, "ash")`, `cancelPending("l1", "ash", any)`, los dos `anonymizeUser(..., "Usuario eliminado")`, y el resultado es `["l1"]`.
  - `handle_onlyAdmin_firstRemainingMemberBecomesAdmin`: con miembros `[ash ADMIN, misty USER, brock USER]`, misty queda `ADMIN` en la liga guardada.
  - `handle_onlyMember_deletesTheLeagueAndEverythingInIt`: `deleteById`, `ScheduleRepository.deleteByLeagueId`, `TradeRepository.deleteByLeagueId`, `ActivityEventRepository.deleteByLeagueId`, `ClosedListRepository.deleteAllByLeagueId` y `delete` de cada draft. No llama a `remove`. El resultado es `[]`.
  - `handle_matchesAndPastDraftPicks_areAnonymized`: en el calendario guardado, `player1`, `player2` y `winnerUsername` de ash pasan a `Usuario eliminado` y los de otros no cambian. Lo mismo con los `DraftPick.username` de un draft anterior.
  - `handle_createdBy_isAnonymized`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=DeleteAccountCommandHandlerTest -Dsurefire.failIfNoSpecifiedTests=false` → falla al compilar.
- [ ] **Step 3:** Implementar el handler.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** Commit `feat(cuenta): DeleteAccountCommand (L2)`.

### Task 4: Back: facade, endpoint, tiempo real e IT

**Files:**
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/users/UserFacade.java`: `List<String> deleteAccount(String username, String password)`
- Create: `src/api-rest/src/main/java/com/villu/pokefantasy/request/user/DeleteAccountRequest.java` (`password`)
- Modify: `src/api-rest/src/main/java/com/villu/pokefantasy/UserController.java`: `@DeleteMapping("/users/me")` con `@AuthenticationPrincipal` y `@RequestBody DeleteAccountRequest`. Llama a la facade, luego a `realtimeNotifier.draftUpdated(id)` por cada liga devuelta, borra las dos cookies (`AuthCookies.clear`) y responde 204. `RealtimeNotifier` se inyecta en el controller, como en los controllers de liga.
- Modify: `src/boot/src/test/java/com/villu/pokefantasy/it/ApiClient.java`: `HttpResponse<String> delete(String path, String json)`, un DELETE con cuerpo JSON.
- Create: `src/boot/src/test/java/com/villu/pokefantasy/it/AccountDeletionIntegrationTest.java`
- Test: `UserFacadeTest`, y el test del controller con `ControllerTestSupport` (`DraftScheduleUserControllersTest`)
- Modify: `CLAUDE.md` del back. Tabla 2: fila "Sacar a un miembro de una liga (expulsión y borrado de cuenta)" → `LeagueMemberRemoval`, y fila "Borrado de cuenta y anonimización" → `DeleteAccountCommandHandler`. Sección Seguridad: `DELETE /v1/users/me`.

- [ ] **Step 1: Tests**
  - Facade: `deleteAccount` manda `new DeleteAccountCommand("ash", "pw")` y devuelve lo que devuelve el mediator.
  - Controller `deleteAccount_returns204ClearsCookiesAndNotifiesLeagues`: 204, dos `Set-Cookie` con `Max-Age=0` y `draftUpdated("l1")`.
  - IT, con ash, misty y brock (la liga se crea y se une por la API, como en los IT existentes):
    - `deleteAccount_leavesLeaguesWorkingAndAnonymized`:
      - liga A: ash es admin y está con misty; liga B: ash con brock, con un trade pendiente de ash a brock;
      - tras borrar: 204 y cookies borradas;
      - misty es admin de A;
      - el trade de B está `CANCELLED` y su `proposer` es `Usuario eliminado`;
      - `POST /v1/user/login` de ash da 401.
    - `deleteAccount_onlyMember_deletesTheLeague`: `GET` de la liga desde otro usuario da 404 o 403, no 500.
    - `oldSessionAfterDeletion_is401` (Review Focus 2): una copia de la cookie `jwt` tomada antes del borrado da 401 en `GET /v1/leagues/my`.
    - `sameUsernameAfterDeletion_startsEmpty` (Review Focus 3): registrar ash otra vez funciona y `GET /v1/leagues/my` devuelve `[]`.
    - `wrongPassword_is401AndNothingChanges`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl boot -am -Dtest='AccountDeletionIntegrationTest,UserFacadeTest,DraftScheduleUserControllersTest' -Dsurefire.failIfNoSpecifiedTests=false` → falla.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `./mvnw -B -ntp -f src/pom.xml clean verify` con Docker → BUILD SUCCESS, IT sin saltados, cobertura OK.
- [ ] **Step 5:** Commit `feat(cuenta): DELETE /v1/users/me (L2)`. Push y PR a `develop`. El PR explica que el front de borrado depende de este endpoint.

### Task 5: Front: borrar cuenta desde Mi perfil

**Files:**
- Modify: `src/api/auth.ts`: `export const deleteAccount = async (password: string): Promise<void>`, que hace `apiClient.delete('/v1/users/me', { data: { password } })`
- Modify: `src/store/authStore.ts`: acción `accountDeleted: () => void`, que pone `username: null` y `lastSession: null`. No usa `logout()`, que llama a la API de logout y guarda `lastSession`.
- Create: `src/components/DeleteAccountModal.tsx` y su test
- Modify: `src/pages/MyProfilePage.tsx`: en la sección "Cuenta", después de `ChangePasswordForm`, un botón `btn-danger` "Borrar cuenta" que abre el modal

Comportamiento del modal: `role="dialog"`, `aria-labelledby`, foco inicial en el campo de contraseña, Escape cierra, y motion con `useReducedMotion` como los demás modales. Texto:

> Se borrará tu cuenta y no se puede deshacer. Saldrás de todas tus ligas, tus Pokémon volverán a la banca y en el historial aparecerás como «Usuario eliminado». Si eres el único admin de una liga, pasará a serlo el miembro más antiguo; si estás solo, la liga se borra.

Campo de contraseña y botón "Borrar mi cuenta" (`btn-danger`), deshabilitado sin contraseña o mientras envía.

Al terminar con éxito: `queryClient.clear()`, `accountDeleted()`, toast "Cuenta borrada" y `navigate('/login', { replace: true })`. Si falla: toast con `extractErrorMessage` y el modal sigue abierto.

- [ ] **Step 1: Tests (vitest)**
  - `DeleteAccountModal`: `deletes the account with the password, clears the session and goes to /login`. Con `deleteAccount` mockeado: se llama con `'pikachu123'`, `useAuthStore.getState().username` queda `null` y se ve la ruta `/login`.
  - `keeps the modal open and shows the error on a wrong password`: el mock rechaza con un 401 `INVALID_CREDENTIALS`, el diálogo sigue en pantalla y hay un toast de error.
  - `cannot submit without a password`.
  - `src/api/auth.test.ts`: `deleteAccount` hace `DELETE /v1/users/me` con `{ password }`, con un adapter falso como en `client.test.ts`.
- [ ] **Step 2:** `rtk proxy npx vitest run src/components/DeleteAccountModal.test.tsx` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `rtk proxy npx vitest run`, `./node_modules/.bin/tsc -p tsconfig.app.json --noEmit`, `npm run lint` y `npm run build` → todo en verde.
- [ ] **Step 5:** Commit `feat(cuenta): borrar la cuenta desde Mi perfil (L2)`.

### Task 6: Front: privacidad y edad mínima

**Files:**
- Create: `src/pages/PrivacyPage.tsx` y su test. Ruta pública `/privacy` en `App.tsx`, junto a `/about`, con `handle: title('Privacidad')`. Mismo layout que `AboutPage` (`auth-page` / `about-card`).
- Modify: `src/pages/RegisterPage.tsx` y su test: casilla obligatoria.
- Modify: `src/components/FanDisclaimer.tsx` y su test: enlace "Privacidad" a `/privacy`, junto a "Acerca de".
- Modify: `src/pages/AboutPage.tsx` (enlace a `/privacy`) y `src/pages/MyProfilePage.tsx` (enlace "Política de privacidad" bajo "Cuenta").

Contenido de `/privacy`. El texto lo redacta el implementador con estos puntos, sin omitir ninguno:
1. Responsable y contacto: `LEGAL_CONTROLLER` y `LEGAL_CONTACT_EMAIL` de `src/legal.ts`.
2. Datos: nombre de usuario; contraseña (solo su hash bcrypt); foto de perfil si la subes; dirección IP (límite de intentos de login y registro, entre 15 minutos y 30 días, y registros técnicos del servidor); tokens de avisos push; tu actividad en las ligas.
3. Finalidad y base legal: prestar el servicio que pides al registrarte (art. 6.1.b RGPD). Sin publicidad, sin perfiles comerciales, sin venta de datos.
4. Encargados: Render (servidor), Netlify (web), MongoDB Atlas (base de datos), Upstash (Redis), Firebase (avisos push), Sentry (errores), GitHub (código y sprites). Pueden tratar datos fuera de la UE, con el EU-US Data Privacy Framework o cláusulas contractuales tipo.
5. Conservación: mientras la cuenta exista. Al borrarla se elimina, y tu nombre en el historial de las ligas pasa a «Usuario eliminado».
6. Cookies: solo técnicas (`jwt`, `refresh`), más `localStorage` (`auth-storage`, `pf-theme`). Sin cookies de terceros ni analítica, así que no hay banner.
7. Derechos: acceso, rectificación, supresión (botón "Borrar cuenta" en Mi perfil), oposición, limitación y portabilidad, escribiendo al contacto. Reclamación ante la AEPD (aepd.es).
8. Edad mínima: 14 años.

- [ ] **Step 1: Tests**
  - `PrivacyPage`: contiene `Adrian Rafael Rego Rodriguez`, un enlace `mailto:villuprise@gmail.com`, `14 años`, `Usuario eliminado`, los siete encargados por nombre y `AEPD`.
  - `RegisterPage`: `does not register until the age and privacy box is checked`. Con usuario y contraseña válidos y sin marcar la casilla, el botón está deshabilitado y `register` no se llama. Al marcarla se llama. La casilla tiene un enlace a `/privacy`.
  - `FanDisclaimer`: el enlace "Privacidad" apunta a `/privacy`.
- [ ] **Step 2:** `rtk proxy npx vitest run src/pages/PrivacyPage.test.tsx src/pages/RegisterPage.test.tsx src/components/FanDisclaimer.test.tsx` → FAIL.
- [ ] **Step 3:** Implementar. La casilla es `<input type="checkbox" required>` con `<label>`: "Tengo 14 años o más y he leído la [política de privacidad]". Se suma a `canSubmit`.
- [ ] **Step 4:** Las cuatro comprobaciones del front en verde. Revisar `/privacy` y `/register` en el deploy preview: consola sin violaciones de CSP, salvo el drawer de Netlify.
- [ ] **Step 5:** Commit `feat(legal): politica de privacidad y edad minima (L2)`. Push y PR a `main`. Mergear **después** del PR del back (el botón de borrar daría 404 con el back viejo).

### Task 7: Vault

**Files (vault `C:\PokeFantasy\vault`):**
- `50 Features/Borrado de cuenta.md`: `estado: hecho` cuando se mergee, `prs_back`/`prs_front`, y resolver las preguntas abiertas con lo decidido.
- `20 Arquitectura/API REST.md`: `DELETE /v1/users/me`.
- `20 Arquitectura/Seguridad y auth.md`: borrado de cuenta en "Registro y contraseñas".
- `60 Operaciones/Auditoría seguridad y legal 2026-09.md`: L2 con los PRs.
- `40 Frontend/Estructura frontend.md`: ruta `/privacy`.

- [ ] **Step 1:** Editar las notas, `git pull`, commit y push a `main` del vault.
