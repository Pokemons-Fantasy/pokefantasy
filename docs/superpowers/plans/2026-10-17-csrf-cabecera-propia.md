# CSRF con cabecera propia: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quitar `csrf.disable()` (alerta CodeQL #1, `java/spring-disabled-csrf-protection`) y exigir la cabecera `X-Requested-With` en las peticiones que modifican estado y llevan cookie de sesión.

**Architecture:** Se activa el `CsrfFilter` de Spring Security con un `requireCsrfProtectionMatcher` propio: rechaza (403) los métodos no seguros que traen cookie `jwt` o `refresh` y no traen `X-Requested-With`. Una web ajena no puede añadir esa cabecera sin preflight, y el preflight ya lo corta CORS ([[ADR-015 Orígenes CORS exactos]]). Las peticiones con Bearer y sin cookies no se tocan. El front (web y Android comparten `apiClient`) manda la cabecera en todas las peticiones.

**Tech Stack:** Spring Boot 4.1.1 / Spring Security 7, JUnit 5 + AssertJ, ITs con `java.net.http` (`ApiClient`); front React + axios + vitest.

**Spec:** diseño aprobado en chat el 2026-10-17 (sin fichero, por preferencia del usuario): enfoque 1 de la sesión de brainstorming. Contexto en `vault/60 Operaciones/Auditoría seguridad y legal 2026-09.md` (S4).

## Global Constraints

- Cabecera: `X-Requested-With`. Basta con que esté presente y no esté en blanco (cualquier valor). El front manda `XMLHttpRequest`.
- Métodos protegidos: todos menos `GET`, `HEAD`, `OPTIONS`, `TRACE`.
- Solo se protege si la petición trae la cookie `AuthCookies.ACCESS` (`jwt`) o `AuthCookies.REFRESH` (`refresh`).
- Rechazo: 403 `ProblemDetail` con `code` = `CSRF_REJECTED` y `detail` = `Petición rechazada: falta la cabecera X-Requested-With`.
- El rechazo no puede crear sesión HTTP ni poner cookies (`JSESSIONID`, `XSRF-TOKEN`).
- En `SecurityConfig` no puede quedar ninguna llamada a `csrf.disable()` / `AbstractHttpConfigurer::disable` sobre `csrf`.
- Orden de despliegue: el PR del front se mergea antes y se regenera el APK; el back sale en la siguiente release de `develop` a `main`.
- Back: PR a `develop` desde `fix/csrf-cabecera-propia`. Front: PR a `main` desde `fix/csrf-cabecera-propia`. Commits con `git commit -F <archivo>`.

## Review Focus

1. Sesión con solo la cookie `refresh` (el `jwt` caducó): un POST sin cabecera tiene que rechazarse igual (test en Task 1).
2. Cabecera presente pero vacía o en blanco: se trata como ausente (test en Task 1).
3. El rechazo no deja `Set-Cookie` (ni sesión ni token CSRF) (test en Task 1).
4. Login y registro sin cookies y sin cabecera siguen funcionando, para no bloquear clientes que aún no tienen sesión (test en Task 1).
5. Las APKs viejas no mandan la cabecera: cuando salga la release del back, cualquier escritura desde ellas dará 403. No hay test que lo cubra; queda en la descripción del PR y en la nota de Android (Task 3).

---

### Task 1: Back: matcher, CSRF activo y error `CSRF_REJECTED`

**Files:**
- Create: `src/infrastructure/src/main/java/com/villu/pokefantasy/security/CsrfHeaderRequestMatcher.java`
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/security/SecurityConfig.java` (`filterChain`)
- Modify: `src/api-rest/src/main/java/com/villu/pokefantasy/ApiExceptionHandler.java`
- Modify: `src/boot/src/test/java/com/villu/pokefantasy/it/ApiClient.java`
- Create: `src/infrastructure/src/test/java/com/villu/pokefantasy/security/CsrfHeaderRequestMatcherTest.java`
- Create: `src/boot/src/test/java/com/villu/pokefantasy/it/CsrfIntegrationTest.java`
- Test: `src/api-rest/src/test/java/com/villu/pokefantasy/ApiExceptionHandlerTest.java`

**Interfaces:**
- Produces: `public final class CsrfHeaderRequestMatcher implements RequestMatcher` con `public static final String HEADER = "X-Requested-With"` y `boolean matches(HttpServletRequest)`. Devuelve `true` (exige protección y por tanto rechaza) cuando el método no es seguro, hay cookie `jwt` o `refresh` y `HEADER` falta o está en blanco.
- Produces: `ApiExceptionHandler.handleCsrf(CsrfException)` → 403 `CSRF_REJECTED`.
- Produces: `ApiClient` manda `X-Requested-With: XMLHttpRequest` por defecto. `ApiClient withoutCsrfHeader()` lo desactiva para ese cliente.

- [ ] **Step 1: Tests unitarios del matcher (`CsrfHeaderRequestMatcherTest`, con `MockHttpServletRequest`)**
  - `postWithSessionCookieAndNoHeader_requiresProtection` → `true`
  - `postWithOnlyRefreshCookieAndNoHeader_requiresProtection` → `true`
  - `postWithSessionCookieAndBlankHeader_requiresProtection` (valor `"  "`) → `true`
  - `postWithSessionCookieAndHeader_doesNotRequireProtection` → `false`
  - `putAndDeleteWithSessionCookieAndNoHeader_requireProtection` → `true` en los dos
  - `getWithSessionCookieAndNoHeader_doesNotRequireProtection` → `false` (y lo mismo con `HEAD` y `OPTIONS`)
  - `postWithoutCookies_doesNotRequireProtection` (con `Authorization: Bearer x`) → `false`
  - `postWithUnrelatedCookie_doesNotRequireProtection` (cookie `pf-theme`) → `false`

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest=CsrfHeaderRequestMatcherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: no compila (`CsrfHeaderRequestMatcher` no existe).

- [ ] **Step 3: Implementar `CsrfHeaderRequestMatcher`**

Métodos seguros en un `Set.of("GET", "HEAD", "OPTIONS", "TRACE")`. Cookies con `AuthCookies.ACCESS` / `AuthCookies.REFRESH`. Javadoc: por qué basta una cabecera (preflight + CORS exacto) y por qué solo con cookie (con Bearer no hay credencial ambiente que robar).

- [ ] **Step 4: Ejecutar y ver que pasan** (mismo comando). Expected: PASS.

- [ ] **Step 5: Test del handler en `ApiExceptionHandlerTest`**

`csrfRejection_isForbiddenWithCode`: `handleCsrf(new InvalidCsrfTokenException(...))`, o la subclase de `CsrfException` que use el test, da 403, `code` = `CSRF_REJECTED`, `detail` = `Petición rechazada: falta la cabecera X-Requested-With`. Siguiendo el estilo de los tests que ya hay en esa clase.

- [ ] **Step 6: `@ExceptionHandler(CsrfException.class) handleCsrf` en `ApiExceptionHandler`** con `respond(HttpStatus.FORBIDDEN, "CSRF_REJECTED", ...)`. Va antes del handler genérico de `Exception`.

- [ ] **Step 7: IT `CsrfIntegrationTest`** (mismo estilo que `CorsIntegrationTest`: `ApiClient` + `loggedInAs`)
  - `writeWithSessionCookieAndNoHeader_isRejected`: `loggedInAs(...).withoutCsrfHeader()` y `post("/v1/leagues/invite/no-existe/redeem", "")` dan 403, el cuerpo contiene `CSRF_REJECTED` y ningún `Set-Cookie` contiene `JSESSIONID` ni `XSRF-TOKEN`.
  - `writeWithOnlyRefreshCookieAndNoHeader_isRejected`: `forgetCookie("jwt")` y lo mismo → 403 `CSRF_REJECTED`.
  - `writeWithSessionCookieAndHeader_reachesTheController`: el mismo POST con cabecera da un status distinto de 403 (el de token inexistente).
  - `writeWithBearerAndNoCookies_isNotCsrfChecked`: `HttpClient` directo con `Authorization: Bearer <cookie jwt de un login>`, sin cookies ni cabecera → status distinto de 403.
  - `loginAndRegisterWithoutCookiesOrHeader_work`: cliente `withoutCsrfHeader()` que se registra y hace login → 200 en el login.
  - `readWithSessionCookieAndNoHeader_works`: `get("/v1/leagues/my")` sin cabecera → 200.

- [ ] **Step 8: `ApiClient`**: campo `csrfHeader = true`, `withoutCsrfHeader()` y en `send` `builder.header("X-Requested-With", "XMLHttpRequest")` cuando `csrfHeader` esté activo. Así los IT que ya hay siguen comportándose como el front.

- [ ] **Step 9: `SecurityConfig.filterChain`**
  - Cambiar `.csrf(csrf -> csrf.disable())` por `.csrf(csrf -> csrf.requireCsrfProtectionMatcher(new CsrfHeaderRequestMatcher()).csrfTokenRepository(<repo sin estado>))`.
  - Repo sin estado: `CsrfTokenRepository` privado (`static`) cuyo `loadToken` devuelve `null`, `generateToken` crea un `DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", UUID aleatorio)` y `saveToken` no hace nada. Ningún cliente manda token, así que toda petición que encaje con el matcher se rechaza, y sin crear sesión.
  - `exceptionHandling`: añadir `.accessDeniedHandler(...)` con un `DelegatingAccessDeniedHandler` que mande `CsrfException` al `resolver` (como `problemDetailEntryPoint`) y el resto a `new AccessDeniedHandlerImpl()`. Así las denegaciones de autorización se siguen comportando igual que ahora.
  - Javadoc del bean: enlazar el ADR-018.

- [ ] **Step 10: Ejecutar todo con Docker abierto**

Run: `./mvnw -B -ntp -f src/pom.xml clean verify`
Expected: BUILD SUCCESS, con los IT ejecutados (no saltados) y el gate de JaCoCo superado. `CorsIntegrationTest.simplePostFromAnotherSite_isRejectedBeforeReachingTheController` sigue en 403.

- [ ] **Step 11: Commit y PR a `develop`**: `fix(seguridad): CSRF activo con cabecera X-Requested-With (S4, CodeQL #1)`. La descripción avisa del orden de despliegue (front y APK antes de la release).

### Task 2: Front: `apiClient` manda la cabecera

**Files:**
- Modify: `C:\PokeFantasy\pokefantasy-web\src\api\client.ts`
- Create: `C:\PokeFantasy\pokefantasy-web\src\api\client.test.ts` (si ya existe, añadir el test ahí)

**Interfaces:**
- Consumes: contrato de Task 1 (`X-Requested-With`, cualquier valor no vacío).

- [ ] **Step 1: Test** `sends X-Requested-With on every request`: `apiClient.defaults.headers['X-Requested-With']` es `'XMLHttpRequest'`.
- [ ] **Step 2: Ver que falla**: `rtk proxy npx vitest run src/api/client.test.ts` → FAIL.
- [ ] **Step 3:** En `axios.create`, `headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }`, con un comentario de una línea que enlace con el CSRF del back.
- [ ] **Step 4: Ver que pasa** (mismo comando) y ejecutar `rtk proxy npx vitest run`, `npx tsc -p tsconfig.app.json --noEmit` y `npm run lint`. Todo en verde.
- [ ] **Step 5: Commit y PR a `main`**: `fix(seguridad): cabecera X-Requested-With en el cliente de la API`. Funciona con el back viejo (la cabecera se ignora).

### Task 3: Vault y remotes

**Files:**
- Create: `vault/70 Decisiones/ADR-018 CSRF con cabecera propia.md` (desde `Plantillas/Plantilla Decisión.md`, `estado: aceptada`, `area: full`, PRs de Task 1 y 2)
- Modify: `vault/30 Backend/Seguridad y auth.md` (línea de CSRF)
- Modify: `vault/60 Operaciones/Auditoría seguridad y legal 2026-09.md` (S4: resuelto con los PRs; estado en cabecera)
- Modify: `vault/70 Decisiones/ADR-015 Orígenes CORS exactos.md` (quitar "Queda abierto el CSRF..." y enlazar ADR-018)
- Modify: `vault/40 Frontend/App Android.md` (las APKs anteriores a este cambio no pueden escribir con el back nuevo: regenerar e instalar)
- Modify: `pokefantasy/CLAUDE.md`: fila `CsrfException` → 403 `CSRF_REJECTED` en la tabla de errores, y una línea de CSRF en "Seguridad" (va en el PR de Task 1)

- [ ] **Step 1:** Escribir el ADR-018: contexto (S4, CodeQL), opciones (`csrf.spa()` rompe Android porque la cookie `XSRF-TOKEN` queda en `onrender.com`; filtro propio con `disable()` deja la alerta), decisión y consecuencias (orden de despliegue, APKs viejas).
- [ ] **Step 2:** Actualizar el resto de notas. Luego `git pull`, commit y push a `main` del vault.
- [ ] **Step 3:** Quitar el token de la URL de los remotes (Git Credential Manager ya está configurado y el vault funciona sin token):
  `git -C C:\PokeFantasy\pokefantasy remote set-url origin https://github.com/Pokemons-Fantasy/pokefantasy.git` y lo mismo en `pokefantasy-web`.
  Verificar: `git ls-remote origin HEAD` responde en los dos sin pedir nada, o con el diálogo de GCM una sola vez.
- [ ] **Step 4:** Cuando el back esté en `main` y CodeQL haya analizado, comprobar que la alerta #1 sale como `fixed` (`GET .../code-scanning/alerts/1` → `state`).
