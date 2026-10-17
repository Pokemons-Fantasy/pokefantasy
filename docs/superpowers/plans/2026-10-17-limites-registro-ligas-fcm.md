# Límites de registro, ligas y tokens FCM (S6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar S6 de la auditoría: que nadie pueda llenar Atlas/Redis creando cuentas, ligas o tokens FCM sin límite.

**Architecture:** Tres límites independientes en los command handlers (CQRS/Mediator). Registro: contador por IP en `LoginAttemptPort` (Redis), IP de `ClientIpFilter`. Ligas: `count` en Mongo por `createdBy`. FCM: `$push` + `$each` + `$slice` atómico en Mongo.

**Tech Stack:** Spring Boot 4.1, Spring Data MongoDB (`MongoTemplate`), Redis (`StringRedisTemplate`), JUnit 5 + Mockito + AssertJ, Testcontainers (`boot/.../it`).

**Spec:** diseño aprobado en chat (sesión S6, 2026-10-17); hallazgo en el vault `60 Operaciones/Auditoría seguridad y legal 2026-09.md` (S6).

## Global Constraints

- Registro: máximo **3 cuentas creadas por IP por hora**; solo cuentan registros correctos. Exceso → `TooManyAttemptsException` (429 + `Retry-After`). Sin IP → no limita. Redis caído → no limita (fail-open, como el login).
- Ligas: máximo **10 ligas con `createdBy` = usuario** (en total; no hay borrado de ligas). Exceso → `IllegalStateException` con mensaje propio (409).
- FCM: máximo **3 tokens por usuario**; al pasar, se descarta el más antiguo.
- Mensajes al usuario en español; comentarios en español explicando el porqué (estilo del repo).
- Reglas de `CLAUDE.md` del repo (hexagonal, handlers con constructor injection, sin lógica en controllers).

## Review Focus

- Registro fallido (usuario duplicado, contraseña débil) no debe consumir cupo de la IP → test en Task 1.
- La 4ª cuenta desde la misma IP se rechaza **antes** de hashear/guardar (no se crea el usuario) → test en Task 1.
- Otra IP distinta sigue pudiendo registrarse cuando una está bloqueada → test IT en Task 1.
- Re-registrar un token que ya tiene el usuario no lo duplica ni expulsa otro token (el handler hace `removeFcmToken(token)` antes) → test en Task 3 (orden de llamadas).
- Liga nº 10 aún se crea; la 11ª no y no se guarda nada → test en Task 2.

---

### Task 1: Rate limit por IP en el registro

**Files:**
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/users/create/CreateUserCommand.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/users/create/CreateUserCommandHandler.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/users/UserFacade.java:37-39`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/ports/LoginAttemptPort.java` (solo javadoc: contador genérico por clave, también registro)
- Modify: `src/api-rest/src/main/java/com/villu/pokefantasy/UserController.java:49-53`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/users/create/CreateUserCommandHandlerTest.java`, `src/application/src/test/java/com/villu/pokefantasy/commands/users/UserFacadeTest.java`, `src/boot/src/test/java/com/villu/pokefantasy/it/SessionIntegrationTest.java`

**Interfaces:**
- Produces: `record CreateUserCommand(String username, String password, String clientIp)` (clientIp nullable); `UserFacade.create(String username, String password, String clientIp)`; en el handler `static final int MAX_REGISTRATIONS_PER_IP = 3`, `static final Duration REGISTRATION_WINDOW = Duration.ofHours(1)`, clave `"register-ip:" + clientIp`.

- [ ] **Step 1: Tests que fallan** en `CreateUserCommandHandlerTest` (constructor nuevo con `@Mock LoginAttemptPort`; los `new CreateUserCommand(u, p)` existentes pasan a `(u, p, null)`):
  - `handle_ipAtLimit_throwsTooManyAttempts_andDoesNotSave`: `failureCount("register-ip:1.2.3.4")` → 3; espera `TooManyAttemptsException` con `getRetryAfter()` = 1h; `verify(userRepository, never()).saveUser(any())`, `verifyNoInteractions(passwordHashPort)`.
  - `handle_ipBelowLimit_savesAndRecordsRegistration`: count 2 → guarda y `verify(loginAttemptPort).recordFailure("register-ip:1.2.3.4", Duration.ofHours(1))`.
  - `handle_failedRegistration_doesNotConsumeQuota`: `existsByUsernameIgnoreCase` → true; lanza `IllegalStateException`; `verify(loginAttemptPort, never()).recordFailure(any(), any())`.
  - `handle_withoutIp_doesNotTouchRateLimit`: clientIp null → guarda y `verifyNoInteractions(loginAttemptPort)`.
  - `UserFacadeTest`: `create` envía `CreateUserCommand` con `clientIp` "1.2.3.4" (mismo patrón que el test de login, línea ~66).
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml -q -pl application -am test -Dtest='CreateUserCommandHandlerTest,UserFacadeTest' -Dsurefire.failIfNoSpecifiedTests=false` → FAIL de compilación.
- [ ] **Step 3: Implementar.** Comprobar el límite después de validar null/vacío del comando pero antes del patrón, la política y `existsByUsernameIgnoreCase` (bloqueado = bloqueado). `recordFailure` tras `saveUser`. Mensaje: `"Demasiados registros desde esta conexión. Vuelve a intentarlo en 60 minutos."`. Controller: `createUser(@RequestBody UserRequest user, HttpServletRequest request)` → `userFacade.create(..., clientIp(request))`.
- [ ] **Step 4:** mismo comando → PASS.
- [ ] **Step 5: IT** en `SessionIntegrationTest`: `registrations_areRateLimitedPerIp_butOtherIpsCanRegister`: desde `198.51.100.7` registrar `trainer1..3` (200), `trainer4` → 429 con `Retry-After`; desde `198.51.100.8` `trainer4` → 200. Usar `client().from(ip).post("/v1/user", body)`.
- [ ] **Step 6: Commit** `feat(seguridad): limite de 3 registros por IP y hora (S6)`.

### Task 2: Tope de 10 ligas creadas por usuario

**Files:**
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/LeagueRepository.java`
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/LeagueRepositoryImpl.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/league/CreateLeagueCommandHandler.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/league/CreateLeagueCommandHandlerTest.java`, `src/infrastructure/src/test/java/com/villu/pokefantasy/repository/LeagueRepositoryImplTest.java` (nuevo, `MongoTemplate` mockeado como `UserRepositoryImplAvatarTest`)

**Interfaces:**
- Produces: `long LeagueRepository.countByCreatedBy(String username)`; `static final int MAX_LEAGUES_PER_USER = 10` en el handler.

- [ ] **Step 1: Tests que fallan:**
  - `handle_userAtLeagueLimit_throwsConflict_andDoesNotSave`: `countByCreatedBy("ash")` → 10; `IllegalStateException` con mensaje `"Has llegado al máximo de 10 ligas creadas."`; `verify(leagueRepository, never()).save(any())`.
  - `handle_userBelowLeagueLimit_createsLeague`: count 9 → guarda.
  - `LeagueRepositoryImplTest.countByCreatedBy_countsByCreator`: captura la `Query` de `mongoTemplate.count(query, LeagueEntity.class)`; `getQueryObject()` = `new Document("createdBy", "ash")`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml -q -pl application,infrastructure -am test -Dtest='CreateLeagueCommandHandlerTest,LeagueRepositoryImplTest' -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3: Implementar.** Comprobar tras las validaciones de nombre/usuario.
- [ ] **Step 4:** PASS.
- [ ] **Step 5: Commit** `feat(seguridad): maximo de 10 ligas creadas por usuario (S6)`.

### Task 3: Tope de 3 tokens FCM por usuario

**Files:**
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/UserRepository.java`
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/UserRepositoryImpl.java:87-93`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/users/pushtoken/RegisterPushTokenCommandHandler.java`
- Test: `RegisterPushTokenCommandHandlerTest`, `UserRepositoryImplAvatarTest` → test nuevo en `src/infrastructure/src/test/java/com/villu/pokefantasy/repository/UserRepositoryImplFcmTokenTest.java`, IT en `QueriesIntegrationTest`

**Interfaces:**
- Produces: `void UserRepository.addFcmToken(String username, String token, int maxTokens)` (sustituye a la firma de 2 args); `static final int MAX_FCM_TOKENS = 3` en el handler.

- [ ] **Step 1: Tests que fallan:**
  - Handler: `handle_removesTokenFromEveryoneThenAddsWithLimit`: `InOrder` → `removeFcmToken("tok")` y luego `addFcmToken("ash", "tok", 3)`.
  - Repo unit: el `Update` de `updateFirst` tiene `$push.fcmTokens` = `{$each: ["tok"], $slice: -3}` y `$inc.version` = 1.
  - IT `fcmTokens_keepOnlyTheNewestThree`: usuario con tokens `a,b,c`; `addFcmToken("ash","d",3)` → en Mongo `fcmTokens` = `[b, c, d]`.
- [ ] **Step 2:** ejecutar los tests → FAIL.
- [ ] **Step 3: Implementar** con `new Update().push("fcmTokens").slice(-maxTokens).each(token).inc("version", 1)`. Comentario: el handler ya quitó el token a todos, así que `$push` no duplica; `$slice` negativo deja los más recientes.
- [ ] **Step 4:** PASS.
- [ ] **Step 5: Commit** `feat(seguridad): maximo de 3 tokens FCM por usuario (S6)`.

### Task 4: Verificación, PR y vault

- [ ] `./mvnw -B -ntp -f src/pom.xml clean verify` (Docker arrancado para Testcontainers) → BUILD SUCCESS.
- [ ] Push y PR a `develop` por la API de GitHub.
- [ ] Vault: S6 en la nota de auditoría con el PR y los límites; `Seguridad y auth` si describe los límites de login.
