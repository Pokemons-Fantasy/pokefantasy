# Sesión en el mismo sitio (Safari/iOS) y 401 sin sesión: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el login, la sesión y el link de invitación funcionen en Safari/iOS, y que quien no tenga sesión vaya al login (y vuelva a donde estaba) en vez de recibir un 403.

**Architecture:** La web deja de llamar a `onrender.com` y llama a `/api` en su propio dominio; Netlify reenvía `/api/*` a Render, así que las cookies de sesión pasan a ser del propio sitio y Safari no las bloquea. La app Android sigue llamando a Render. El backend responde 401 `UNAUTHENTICATED` (ProblemDetail) cuando no hay sesión; el front, al recibirlo, borra el usuario guardado y `ProtectedRoute` lleva al login con `from`. El SSE, que el proxy corta cada <26 s, se reconecta solo.

**Tech Stack:** Spring Boot 3 + Spring Security (back), React 19 + Vite + Axios + React Query + Zustand + Vitest (front), Netlify `_redirects`.

**Spec:** diseño aprobado en la conversación del 2026-09-28 (sin design doc por preferencia del usuario). Resumen:
1. Back: `authenticationEntryPoint` → 401 ProblemDetail `code: UNAUTHENTICATED`; 403 solo para `FORBIDDEN`.
2. Front: 401 `UNAUTHENTICATED` → borrar usuario guardado → `/login` con `from`; si el 401 llega justo tras un login correcto, aviso de cookies bloqueadas en vez de bucle.
3. Front: `RegisterPage` conserva `from`.
4. Front: proxy de Netlify `/api/*` → Render; una sola `API_BASE_URL` (`/api` en web, Render en nativo, `VITE_API_URL` manda); proxy equivalente en `vite dev`.
5. Front: SSE con reconexión (espera creciente) y refresco al reconectar; polling de respaldo mientras está caído.
6. Vault: ADR nuevo + ADR-002, Seguridad y auth, Gotchas, feature Invitaciones.

## Global Constraints

- Backend: rama `fix/sesion-401-proxy` desde `origin/develop` en `C:\PokeFantasy\wt-back`; PR contra `develop`.
- Frontend: rama `fix/sesion-mismo-sitio` desde `origin/main` en `C:\PokeFantasy\wt-web`; PR contra `main`.
- Maven con `$env:JAVA_HOME='E:\jdk-23.0.1'` y `.\mvnw.cmd -B -ntp -f src/pom.xml ...` desde `C:\PokeFantasy\wt-back`.
- Antes de push: back `clean verify` (gate JaCoCo 80 %); front `npm run lint`, `npm run api:check`, `npm test`, `npm run build`.
- Commits en PowerShell con `$msg = @'...'@`; sin rutas con `/` en el mensaje; terminar con `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Front: `import type` para tipos; ninguna llamada HTTP ni `EventSource` fuera de `src/api/`.
- El front tolera el back viejo (403 sin `code` → comportamiento actual) y el back nuevo no rompe el front viejo (solo cambia 403 por 401 cuando no hay sesión).
- Textos visibles en español; sin emojis nuevos.
- URL de Render: `https://pokefantasy.onrender.com`. Web: `https://pokefantasy.netlify.app`.

## Review Focus

1. **Login con contraseña incorrecta** (401 `INVALID_CREDENTIALS`): no debe tratarse como sesión caducada ni borrar nada. Test en Task 4 (`isSessionExpired` solo con `UNAUTHENTICATED`).
2. **Navegador que bloquea todas las cookies**: tras un login correcto no debe entrar en bucle login → 401 → login. Test en Task 5 (aviso de cookies y usuario sin guardar).
3. **Backend dormido o caído mientras el SSE reintenta**: la espera entre reintentos crece hasta 60 s y no se reintenta tras desmontar. Test en Task 6.
4. **Error de red en la comprobación tras el login** (no 401): el login no debe fallar por eso. Test en Task 5.
5. **Petición sin sesión a un endpoint SSE** (`Accept: text/event-stream`): debe acabar en 401, no en 200 vacío. Test en Task 1 (entry point con respaldo `sendError(401)` si el resolver no puede escribir).

---

## Task 1: Backend, 401 `UNAUTHENTICATED` sin sesión

**Files:**
- Modify: `src/api-rest/src/main/java/com/villu/pokefantasy/ApiExceptionHandler.java`
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/security/SecurityConfig.java`
- Modify: `src/api-rest/src/test/java/com/villu/pokefantasy/ApiExceptionHandlerTest.java`
- Modify: `src/infrastructure/src/test/java/com/villu/pokefantasy/security/SecurityConfigTest.java`
- Modify: `src/boot/src/test/java/com/villu/pokefantasy/it/SessionIntegrationTest.java`
- Modify: `CLAUDE.md` (tabla "Excepción → HTTP")

**Interfaces:**
- Produces: respuesta `401` con cuerpo ProblemDetail `{ status: 401, code: "UNAUTHENTICATED", message: "Sesión no iniciada o caducada", ... }` para cualquier petición autenticada sin sesión válida. El front (Task 4) depende exactamente de `status === 401 && code === 'UNAUTHENTICATED'`.
- Produces: `static AuthenticationEntryPoint SecurityConfig.problemDetailEntryPoint(HandlerExceptionResolver resolver)`.

- [ ] **Step 1: Tests que fallan**

En `ApiExceptionHandlerTest`, añadir el caso al `switch` de `ThrowingController.boom` (import `org.springframework.security.authentication.InsufficientAuthenticationException`):

```java
                case "unauthenticated" -> new InsufficientAuthenticationException("Full authentication is required");
```

y el test:

```java
    @Test
    void missingSession_is401Unauthenticated_notConfusedWithBadCredentials() throws Exception {
        problem("unauthenticated", 401, "UNAUTHENTICATED")
                .andExpect(jsonPath("$.message").value("Sesión no iniciada o caducada"));
        problem("credentials", 401, "INVALID_CREDENTIALS");
    }
```

En `SecurityConfigTest` (imports: `jakarta.servlet.http.HttpServletRequest`, `jakarta.servlet.http.HttpServletResponse`, `org.springframework.security.authentication.InsufficientAuthenticationException`, `org.springframework.web.servlet.HandlerExceptionResolver`, `org.springframework.web.servlet.ModelAndView`, `static org.mockito.ArgumentMatchers.*`, `static org.mockito.Mockito.*`):

```java
    @Test
    void entryPoint_delegatesToTheMvcExceptionHandler() throws Exception {
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        InsufficientAuthenticationException exception = new InsufficientAuthenticationException("x");
        when(resolver.resolveException(request, response, null, exception)).thenReturn(new ModelAndView());

        SecurityConfig.problemDetailEntryPoint(resolver).commence(request, response, exception);

        verify(resolver).resolveException(request, response, null, exception);
        verify(response, never()).sendError(anyInt());
    }

    @Test
    void entryPoint_fallsBackToPlain401_whenTheHandlerCannotWriteTheResponse() throws Exception {
        // p. ej. un EventSource (Accept: text/event-stream) sin sesión
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(resolver.resolveException(any(), any(), isNull(), any())).thenReturn(null);

        SecurityConfig.problemDetailEntryPoint(resolver)
                .commence(mock(HttpServletRequest.class), response, new InsufficientAuthenticationException("x"));

        verify(response).sendError(401);
    }
```

En `SessionIntegrationTest`, sustituir `withoutSession_protectedEndpointsAreRejected`:

```java
    @Test
    void withoutSession_protectedEndpointsAre401Unauthenticated() throws Exception {
        HttpResponse<String> response = client().get("/v1/leagues/my");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }
```

- [ ] **Step 2: Comprobar que fallan**

Run: `.\mvnw.cmd -B -ntp -q -f src/pom.xml test -pl api-rest,infrastructure -am "-Dtest=ApiExceptionHandlerTest,SecurityConfigTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: fallo de compilación (`problemDetailEntryPoint` no existe) y, una vez compile, `UNAUTHENTICATED` esperado pero `INTERNAL_ERROR`/500.

- [ ] **Step 3: Implementación**

`ApiExceptionHandler` (import `org.springframework.security.core.AuthenticationException`), justo después de `handleBadCredentials` (el handler más específico gana, así que `BadCredentialsException` sigue en `INVALID_CREDENTIALS`):

```java
    /** Sin sesión o con sesión caducada: llega desde el entry point de Spring Security. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleUnauthenticated() {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sesión no iniciada o caducada");
    }
```

`SecurityConfig` (imports `org.springframework.beans.factory.annotation.Qualifier`, `org.springframework.security.web.AuthenticationEntryPoint`, `org.springframework.web.servlet.HandlerExceptionResolver`, `jakarta.servlet.http.HttpServletResponse`): nuevo parámetro en `filterChain` y `exceptionHandling` en la cadena:

```java
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                           SecurityContextRepository securityContextRepository,
                                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        ...
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(problemDetailEntryPoint(resolver)))
                .build();
    }

    /**
     * Sin sesión: 401 con el mismo ProblemDetail que el resto de errores ({@code ApiExceptionHandler},
     * código {@code UNAUTHENTICATED}). Sin esto Spring Security responde 403 y el front no distingue
     * "no has iniciado sesión" de "no tienes permiso".
     */
    static AuthenticationEntryPoint problemDetailEntryPoint(HandlerExceptionResolver resolver) {
        return (request, response, exception) -> {
            if (resolver.resolveException(request, response, null, exception) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
        };
    }
```

`CLAUDE.md` del backend, tabla "Excepción → HTTP": añadir fila tras `BadCredentialsException`:

```markdown
| `AuthenticationException` (sin sesión, desde el entry point de `SecurityConfig`) | 401 | `UNAUTHENTICATED` |
```

- [ ] **Step 4: Verificar**

Run: `.\mvnw.cmd -B -ntp -f src/pom.xml clean verify`
Expected: `BUILD SUCCESS`, `All coverage checks have been met`. Si Docker está arrancado, `SessionIntegrationTest` pasa; si no, se salta (decirlo en el PR).

- [ ] **Step 5: Commit, push y PR**

```powershell
$msg = @'
fix: 401 UNAUTHENTICATED cuando no hay sesion

Sin authenticationEntryPoint, Spring Security respondia 403 a las
peticiones sin sesion, sin ProblemDetail, y el front no podia distinguir
"no has iniciado sesion" de "no tienes permiso": quien tenia la sesion
caducada o la cookie bloqueada recibia un 403 al canjear una invitacion.

El entry point delega en ApiExceptionHandler (401, code UNAUTHENTICATED);
si no puede escribir la respuesta (EventSource), 401 sin cuerpo.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git add -A; git commit -m $msg; git push -u origin fix/sesion-401-proxy
```

PR contra `develop` por la API de GitHub.

---

## Task 2: Front, `API_BASE_URL` única y proxy `/api`

**Files:**
- Create: `src/utils/apiBaseUrl.ts`, `src/utils/apiBaseUrl.test.ts`
- Modify: `src/api/client.ts`, `public/_redirects`, `vite.config.ts`, `CLAUDE.md` (§3 y "Comandos")

**Interfaces:**
- Produces: `export const API_BASE_URL: string` en `src/api/client.ts` (lo usa Task 6); `resolveApiBaseUrl(envUrl: string | undefined, isNative: boolean): string`; `RENDER_API_URL`.

- [ ] **Step 1: Test que falla** (`src/utils/apiBaseUrl.test.ts`)

```ts
import { describe, it, expect } from 'vitest';
import { resolveApiBaseUrl, RENDER_API_URL } from './apiBaseUrl';

describe('resolveApiBaseUrl', () => {
  it('on the web goes through the same-site /api proxy so Safari keeps the session cookie', () => {
    expect(resolveApiBaseUrl(undefined, false)).toBe('/api');
  });

  it('the native app calls Render directly (its origin is https://localhost, the proxy is not there)', () => {
    expect(resolveApiBaseUrl(undefined, true)).toBe(RENDER_API_URL);
  });

  it('VITE_API_URL always wins (local backend)', () => {
    expect(resolveApiBaseUrl('http://localhost:8080', false)).toBe('http://localhost:8080');
    expect(resolveApiBaseUrl('http://localhost:8080', true)).toBe('http://localhost:8080');
  });
});
```

- [ ] **Step 2:** `npx vitest run src/utils/apiBaseUrl.test.ts` → FAIL (módulo no existe).

- [ ] **Step 3: Implementación**

`src/utils/apiBaseUrl.ts`:

```ts
export const RENDER_API_URL = 'https://pokefantasy.onrender.com';

/**
 * Base de la API. En la web se usa `/api`, que Netlify (`public/_redirects`) y `vite dev` reenvían a
 * Render: así las cookies de sesión son del propio sitio y Safari/iOS no las bloquea como de terceros.
 * La app nativa (origin `https://localhost`) llama a Render directamente. `VITE_API_URL` manda siempre.
 */
export function resolveApiBaseUrl(envUrl: string | undefined, isNative: boolean): string {
  return envUrl ?? (isNative ? RENDER_API_URL : '/api');
}
```

`src/api/client.ts`:

```ts
import axios from 'axios';
import { Capacitor } from '@capacitor/core';
import { resolveApiBaseUrl } from '../utils/apiBaseUrl';

export const API_BASE_URL = resolveApiBaseUrl(import.meta.env.VITE_API_URL, Capacitor.isNativePlatform());

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});
```

`public/_redirects` (el proxy antes del fallback de la SPA):

```
/api/*  https://pokefantasy.onrender.com/:splat  200
/* /index.html 200
```

`vite.config.ts`, dentro de `defineConfig({...})`:

```ts
  server: {
    proxy: {
      // Igual que public/_redirects: sin VITE_API_URL, `npm run dev` habla con producción a través de /api
      '/api': {
        target: 'https://pokefantasy.onrender.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
```

`CLAUDE.md` del front: en §3 cambiar la viñeta "Sin `VITE_API_URL` se apunta a producción" por:

```markdown
- **La web llama a la API en `/api` de su propio dominio** y Netlify (`public/_redirects`) la reenvía a Render: así la cookie de sesión no es de terceros y Safari/iOS la acepta. La app nativa llama a Render directamente. Sin `VITE_API_URL`, `npm run dev` usa el mismo proxy (`vite.config.ts`) contra producción a propósito; en local se usa `.env.local` (ADR-013).
```

y en "Comandos" la línea de `npm run dev` queda `# sin VITE_API_URL habla con PRODUCCIÓN por el proxy /api (ver abajo)`.

- [ ] **Step 4:** `npx vitest run src/utils/apiBaseUrl.test.ts` → PASS.
- [ ] **Step 5:** `git add -A; git commit` (mensaje: `feat: API en /api del propio sitio via proxy de Netlify` + Co-Authored-By).

---

## Task 3: Front, `RegisterPage` conserva `from`

**Files:**
- Modify: `src/pages/RegisterPage.tsx`, `src/pages/LoginPage.tsx` (solo el `Link` a registro), `src/pages/RegisterPage.test.tsx`

- [ ] **Step 1: Test que falla** (añadir a `RegisterPage.test.tsx`; imports `Route, Routes, useLocation` de `react-router-dom`)

```tsx
function LoginProbe() {
  const location = useLocation();
  return <p>login from {(location.state as { from?: { pathname: string } } | null)?.from?.pathname}</p>;
}

it('after registering goes to login keeping where the user came from (e.g. an invite link)', async () => {
  mockedRegister.mockResolvedValue(undefined as never);
  const user = userEvent.setup();
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
      <MemoryRouter initialEntries={[{ pathname: '/register', state: { from: { pathname: '/invite/tok' } } }]}>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  await user.type(screen.getByPlaceholderText('Usuario'), 'ash_k');
  await user.type(screen.getByPlaceholderText('Contraseña'), 'pikachu123');
  await user.click(screen.getByRole('button', { name: 'Registrarse' }));

  expect(await screen.findByText('login from /invite/tok')).toBeInTheDocument();
});
```

- [ ] **Step 2:** `npx vitest run src/pages/RegisterPage.test.tsx` → FAIL (no encuentra el texto).

- [ ] **Step 3: Implementación**

`RegisterPage.tsx`: importar `useLocation, type Location` de `react-router-dom`; dentro del componente:

```tsx
  const location = useLocation();
  // Viene del login, que a su vez guarda la ruta protegida que se intentó abrir (p. ej. /invite/:token)
  const from = (location.state as { from?: Location } | null)?.from;
```

y en la mutación:

```tsx
    onSuccess: () => {
      addToast('success', 'Cuenta creada. Inicia sesión para continuar.');
      navigate('/login', { state: { from } });
    },
```

Si `RegisterPage` tiene un enlace "Inicia sesión", pasarle también `state={{ from }}`.

`LoginPage.tsx`: `<Link to="/register" state={{ from }}>Regístrate</Link>`.

- [ ] **Step 4:** `npx vitest run src/pages/RegisterPage.test.tsx` → PASS (los 3 tests).
- [ ] **Step 5:** commit `fix: el registro conserva la pagina de origen (links de invitacion)`.

---

## Task 4: Front, 401 `UNAUTHENTICATED` → vuelta al login

**Files:**
- Create: `src/utils/session.ts`, `src/utils/session.test.ts`, `src/store/authStore.test.ts`
- Modify: `src/api/client.ts`, `src/store/authStore.ts`, `src/main.tsx`

**Interfaces:**
- Consumes: contrato de Task 1 (`401` + `code: 'UNAUTHENTICATED'`).
- Produces: `isSessionExpired(err: unknown): boolean` (lo usa Task 5); `onSessionExpired(handler: () => void): () => void` en `client.ts`; `expireSession(): void` en `authStore`.

- [ ] **Step 1: Tests que fallan**

`src/utils/session.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { isSessionExpired } from './session';

const httpError = (status: number, data?: unknown) => ({ response: { status, data } });

describe('isSessionExpired', () => {
  it('is true only for 401 UNAUTHENTICATED', () => {
    expect(isSessionExpired(httpError(401, { code: 'UNAUTHENTICATED' }))).toBe(true);
  });

  it('a wrong password (401 INVALID_CREDENTIALS) is not an expired session', () => {
    expect(isSessionExpired(httpError(401, { code: 'INVALID_CREDENTIALS' }))).toBe(false);
  });

  it('ignores 403s, network errors and old backends without code', () => {
    expect(isSessionExpired(httpError(403, { code: 'FORBIDDEN' }))).toBe(false);
    expect(isSessionExpired(httpError(403, { error: 'Forbidden' }))).toBe(false);
    expect(isSessionExpired(new Error('Network Error'))).toBe(false);
    expect(isSessionExpired(null)).toBe(false);
  });
});
```

`src/store/authStore.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from './authStore';
import { useToastStore } from './toastStore';

describe('authStore.expireSession', () => {
  beforeEach(() => useToastStore.setState({ toasts: [] }));

  it('forgets the user (ProtectedRoute then sends to /login with from) and says why', () => {
    useAuthStore.setState({ username: 'ash' });
    useAuthStore.getState().expireSession();
    expect(useAuthStore.getState().username).toBeNull();
    expect(useToastStore.getState().toasts.map((t) => t.message)).toEqual(['Tu sesión ha caducado. Vuelve a entrar.']);
  });

  it('does nothing when nobody is logged in (e.g. the cookie check right after login)', () => {
    useAuthStore.setState({ username: null });
    useAuthStore.getState().expireSession();
    expect(useToastStore.getState().toasts).toEqual([]);
  });
});
```

- [ ] **Step 2:** `npx vitest run src/utils/session.test.ts src/store/authStore.test.ts` → FAIL.

- [ ] **Step 3: Implementación**

`src/utils/session.ts`:

```ts
interface HttpErrorLike {
  response?: { status?: number; data?: { code?: string } | unknown };
}

/** El backend dice que no hay sesión (cookie ausente, caducada o bloqueada por el navegador). */
export function isSessionExpired(err: unknown): boolean {
  const response = (err as HttpErrorLike | null)?.response;
  return response?.status === 401 && (response.data as { code?: string } | undefined)?.code === 'UNAUTHENTICATED';
}
```

`src/api/client.ts`, al final:

```ts
/** Ejecuta `handler` cada vez que el backend responde que no hay sesión. Devuelve la función para quitarlo. */
export function onSessionExpired(handler: () => void): () => void {
  const id = apiClient.interceptors.response.use(undefined, (error) => {
    if (isSessionExpired(error)) handler();
    return Promise.reject(error);
  });
  return () => apiClient.interceptors.response.eject(id);
}
```

(import `isSessionExpired` de `../utils/session`).

`src/store/authStore.ts`: import `useToastStore` de `./toastStore`; en la interfaz `expireSession: () => void;` y en el store:

```ts
      expireSession: () => {
        if (!get().username) return;
        set({ username: null });
        useToastStore.getState().addToast('info', 'Tu sesión ha caducado. Vuelve a entrar.');
      },
```

`src/main.tsx`: importar `onSessionExpired` junto a `apiClient` y `useAuthStore`; antes de `createRoot`:

```ts
// Sin sesión en el backend: se olvida el usuario y ProtectedRoute lleva a /login recordando la ruta
onSessionExpired(() => useAuthStore.getState().expireSession())
```

- [ ] **Step 4:** `npx vitest run src/utils/session.test.ts src/store/authStore.test.ts` → PASS; `./node_modules/.bin/tsc --noEmit -p tsconfig.app.json` sin errores.
- [ ] **Step 5:** commit `fix: sin sesion se vuelve al login recordando la ruta`.

---

## Task 5: Front, comprobación de cookie tras el login

**Files:**
- Modify: `src/pages/LoginPage.tsx`
- Create: `src/pages/LoginPage.test.tsx`

**Interfaces:**
- Consumes: `isSessionExpired` (Task 4), `getMyLeagues` (`src/api/leagues.ts`), query key `['my-leagues']` (la usan HomePage, LeaguesPage, MyProfilePage: el resultado queda cacheado y ahorra esa llamada).
- Produces: `export const COOKIES_BLOCKED_MESSAGE` en `LoginPage.tsx`.

- [ ] **Step 1: Test que falla** (`src/pages/LoginPage.test.tsx`)

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import LoginPage, { COOKIES_BLOCKED_MESSAGE } from './LoginPage';
import * as authApi from '../api/auth';
import * as leaguesApi from '../api/leagues';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';

vi.mock('../api/auth', async (importOriginal) => ({ ...(await importOriginal<typeof authApi>()), login: vi.fn() }));
vi.mock('../api/leagues', async (importOriginal) => ({ ...(await importOriginal<typeof leaguesApi>()), getMyLeagues: vi.fn() }));

function renderLogin() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: { pathname: '/invite/tok' } } }]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/invite/:token" element={<p>invite page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

async function submit() {
  const user = userEvent.setup();
  await user.type(screen.getByPlaceholderText('Usuario'), 'ash');
  await user.type(screen.getByPlaceholderText('Contraseña'), 'pikachu123');
  await user.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('LoginPage', () => {
  beforeEach(() => {
    useAuthStore.setState({ username: null });
    useToastStore.setState({ toasts: [] });
    vi.mocked(authApi.login).mockResolvedValue({ username: 'ash' });
  });

  it('logs in and returns to the page the user wanted (the invite link)', async () => {
    vi.mocked(leaguesApi.getMyLeagues).mockResolvedValue([]);
    renderLogin();
    await submit();
    expect(await screen.findByText('invite page')).toBeInTheDocument();
    expect(useAuthStore.getState().username).toBe('ash');
  });

  it('if the browser drops the session cookie, explains it instead of looping back to login', async () => {
    vi.mocked(leaguesApi.getMyLeagues).mockRejectedValue({ response: { status: 401, data: { code: 'UNAUTHENTICATED' } } });
    renderLogin();
    await submit();
    await waitFor(() =>
      expect(useToastStore.getState().toasts.map((t) => t.message)).toContain(COOKIES_BLOCKED_MESSAGE));
    expect(useAuthStore.getState().username).toBeNull();
    expect(screen.queryByText('invite page')).not.toBeInTheDocument();
  });

  it('a network error in the check does not turn a good login into a failure', async () => {
    vi.mocked(leaguesApi.getMyLeagues).mockRejectedValue(new Error('Network Error'));
    renderLogin();
    await submit();
    expect(await screen.findByText('invite page')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2:** `npx vitest run src/pages/LoginPage.test.tsx` → FAIL (`COOKIES_BLOCKED_MESSAGE` no exportado).

- [ ] **Step 3: Implementación** (`LoginPage.tsx`; imports `useQueryClient`, `getMyLeagues` de `../api/leagues`, `isSessionExpired` de `../utils/session`)

```tsx
export const COOKIES_BLOCKED_MESSAGE =
  'Tu navegador está bloqueando las cookies de sesión. Permite las cookies de este sitio o prueba con otro navegador.';
```

Dentro del componente, `const queryClient = useQueryClient();` y la mutación:

```tsx
  const mutation = useMutation({
    mutationFn: async () => {
      const result = await login(username, password);
      try {
        // Comprueba que el navegador ha guardado la cookie antes de dar el login por bueno; si no, cada
        // petición daría 401 y se volvería al login en bucle. El resultado queda en caché para la home.
        await queryClient.fetchQuery({ queryKey: ['my-leagues'], queryFn: getMyLeagues, retry: false });
      } catch (err) {
        if (isSessionExpired(err)) throw new Error(COOKIES_BLOCKED_MESSAGE);
      }
      return result;
    },
    onSuccess: ({ username: loggedUsername }) => {
      setAuth(loggedUsername);
      navigate(from ?? '/', { replace: true });
    },
    onError: (err) => addToast('error', extractErrorMessage(err, 'Usuario o contraseña incorrectos')),
  });
```

(`expireSession` no hace nada aquí porque aún no hay `username` guardado: ver Task 4.)

- [ ] **Step 4:** `npx vitest run src/pages/LoginPage.test.tsx` → PASS.
- [ ] **Step 5:** commit `fix: aviso de cookies bloqueadas tras el login en vez de bucle`.

---

## Task 6: Front, SSE con reconexión

**Files:**
- Create: `src/api/sse.ts`, `src/api/sse.test.ts`, `src/hooks/useNotificationSse.test.tsx`
- Modify: `src/hooks/useNotificationSse.ts`, `src/pages/DraftPage.tsx`

**Interfaces:**
- Consumes: `API_BASE_URL` (Task 2).
- Produces:

```ts
export interface EventStreamHandlers {
  listeners: Record<string, (event: MessageEvent) => void>;
  /** Conexión abierta; `reconnected` es false la primera vez. */
  onOpen?: (reconnected: boolean) => void;
  /** Conexión caída; se reintentará sola. */
  onDown?: () => void;
}
export const SSE_RETRY_MIN_MS = 2_000;
export const SSE_RETRY_MAX_MS = 60_000;
export function openEventStream(path: string, handlers: EventStreamHandlers): () => void;
```

- [ ] **Step 1: Test que falla** (`src/api/sse.test.ts`)

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { openEventStream, SSE_RETRY_MIN_MS, SSE_RETRY_MAX_MS } from './sse';

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  listeners: Record<string, (e: MessageEvent) => void> = {};
  closed = false;
  constructor(public url: string, public init?: EventSourceInit) { FakeEventSource.instances.push(this); }
  addEventListener(type: string, listener: (e: MessageEvent) => void) { this.listeners[type] = listener; }
  close() { this.closed = true; }
}
const last = () => FakeEventSource.instances[FakeEventSource.instances.length - 1];

describe('openEventStream', () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal('EventSource', FakeEventSource);
    vi.useFakeTimers();
  });
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

  it('connects with credentials to the API base and dispatches events', () => {
    const onEvent = vi.fn();
    openEventStream('/v1/users/events', { listeners: { steal: onEvent } });
    expect(last().url).toBe('/api/v1/users/events');
    expect(last().init).toEqual({ withCredentials: true });
    const event = new MessageEvent('steal', { data: '{}' });
    last().listeners.steal(event);
    expect(onEvent).toHaveBeenCalledWith(event);
  });

  it('when the connection drops (the Netlify proxy cuts it) it reconnects with a growing wait', () => {
    const onDown = vi.fn();
    openEventStream('/x', { listeners: {}, onDown });
    const first = last();
    first.onerror!();
    expect(first.closed).toBe(true);
    expect(onDown).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(SSE_RETRY_MIN_MS - 1);
    expect(FakeEventSource.instances).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(FakeEventSource.instances).toHaveLength(2);
    last().onerror!();
    vi.advanceTimersByTime(SSE_RETRY_MIN_MS * 2 - 1);
    expect(FakeEventSource.instances).toHaveLength(2);
    vi.advanceTimersByTime(1);
    expect(FakeEventSource.instances).toHaveLength(3);
  });

  it('never waits more than the maximum while the backend is down', () => {
    openEventStream('/x', { listeners: {} });
    for (let i = 0; i < 10; i++) { last().onerror!(); vi.advanceTimersByTime(SSE_RETRY_MAX_MS); }
    expect(FakeEventSource.instances).toHaveLength(11);
  });

  it('tells whether an open is a reconnection and resets the wait', () => {
    const onOpen = vi.fn();
    openEventStream('/x', { listeners: {}, onOpen });
    last().onopen!();
    expect(onOpen).toHaveBeenLastCalledWith(false);
    last().onerror!(); vi.advanceTimersByTime(SSE_RETRY_MIN_MS);
    last().onopen!();
    expect(onOpen).toHaveBeenLastCalledWith(true);
    last().onerror!(); vi.advanceTimersByTime(SSE_RETRY_MIN_MS);
    expect(FakeEventSource.instances).toHaveLength(3);
  });

  it('after close() it does not reconnect', () => {
    const close = openEventStream('/x', { listeners: {} });
    const es = last();
    close();
    expect(es.closed).toBe(true);
    es.onerror!();
    vi.advanceTimersByTime(SSE_RETRY_MAX_MS);
    expect(FakeEventSource.instances).toHaveLength(1);
  });
});
```

- [ ] **Step 2:** `npx vitest run src/api/sse.test.ts` → FAIL.

- [ ] **Step 3: Implementación** (`src/api/sse.ts`)

```ts
import { API_BASE_URL } from './client';

export interface EventStreamHandlers {
  listeners: Record<string, (event: MessageEvent) => void>;
  /** Conexión abierta; `reconnected` es false la primera vez. */
  onOpen?: (reconnected: boolean) => void;
  /** Conexión caída; se reintentará sola. */
  onDown?: () => void;
}

export const SSE_RETRY_MIN_MS = 2_000;
export const SSE_RETRY_MAX_MS = 60_000;

/**
 * SSE autenticado (cookie de sesión) que se reconecta solo: el proxy de Netlify corta las conexiones en
 * menos de 26 s. La espera entre reintentos crece hasta 1 min mientras el backend no responda y vuelve al
 * mínimo al conectar. Devuelve la función que la cierra para siempre.
 */
export function openEventStream(path: string, handlers: EventStreamHandlers): () => void {
  let source: EventSource | null = null;
  let retryTimer: ReturnType<typeof setTimeout> | null = null;
  let retryMs = SSE_RETRY_MIN_MS;
  let openedBefore = false;
  let stopped = false;

  const connect = () => {
    source = new EventSource(`${API_BASE_URL}${path}`, { withCredentials: true });
    for (const [type, listener] of Object.entries(handlers.listeners)) {
      source.addEventListener(type, listener as EventListener);
    }
    source.onopen = () => {
      retryMs = SSE_RETRY_MIN_MS;
      handlers.onOpen?.(openedBefore);
      openedBefore = true;
    };
    source.onerror = () => {
      source?.close(); // el reintento lo lleva este módulo (con espera creciente), no el navegador
      if (stopped) return;
      handlers.onDown?.();
      retryTimer = setTimeout(connect, retryMs);
      retryMs = Math.min(retryMs * 2, SSE_RETRY_MAX_MS);
    };
  };

  connect();
  return () => {
    stopped = true;
    if (retryTimer) clearTimeout(retryTimer);
    source?.close();
  };
}
```

- [ ] **Step 4:** `npx vitest run src/api/sse.test.ts` → PASS.

- [ ] **Step 5: DraftPage** — sustituir el `useEffect` de SSE (líneas 39-60) por (import `openEventStream` de `../api/sse`):

```tsx
  // SSE del draft. El proxy de Netlify corta la conexión cada <26 s: se reconecta sola y al volver se
  // refresca por si se perdió algún evento; mientras está caída, polling cada 10 s.
  useEffect(() => {
    if (!leagueId) return;
    const refresh = () => queryClient.invalidateQueries({ queryKey: ['draft-status', leagueId] });
    let fallback: ReturnType<typeof setInterval> | null = null;
    const stopFallback = () => {
      if (fallback) clearInterval(fallback);
      fallback = null;
    };
    const close = openEventStream(`/v1/leagues/${leagueId}/draft/events`, {
      listeners: { 'draft-updated': refresh },
      onOpen: (reconnected) => {
        stopFallback();
        if (reconnected) refresh();
      },
      onDown: () => {
        if (!fallback) fallback = setInterval(refresh, 10_000);
      },
    });
    return () => {
      close();
      stopFallback();
    };
  }, [leagueId, queryClient]);
```

`DraftPage.test.tsx` no cambia (su `FakeEventSource` admite `onopen`/`onerror`); comprobar que pasa.

- [ ] **Step 6: useNotificationSse, test que falla** (`src/hooks/useNotificationSse.test.tsx`)

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useNotificationSse } from './useNotificationSse';
import * as sse from '../api/sse';
import * as tradesApi from '../api/trades';
import * as leaguesApi from '../api/leagues';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import type { Trade } from '../api/trades';

vi.mock('../api/sse', () => ({ openEventStream: vi.fn(() => () => {}) }));
vi.mock('../api/trades', async (importOriginal) => ({ ...(await importOriginal<typeof tradesApi>()), getMyPendingTrades: vi.fn() }));
vi.mock('../api/leagues', async (importOriginal) => ({ ...(await importOriginal<typeof leaguesApi>()), getMyLeagues: vi.fn() }));

const trade = (id: string) => ({ id, proposer: 'brock', leagueId: 'l1' }) as Trade;
const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0 } } })}>{children}</QueryClientProvider>
);

describe('useNotificationSse', () => {
  beforeEach(() => {
    useAuthStore.setState({ username: 'ash' });
    useToastStore.setState({ toasts: [] });
    vi.mocked(leaguesApi.getMyLeagues).mockResolvedValue([]);
  });

  it('on reconnection shows trades proposed while the stream was down, but not the ones already known', async () => {
    // La carga inicial ve t1; después (conexión caída) llega t2
    vi.mocked(tradesApi.getMyPendingTrades).mockResolvedValueOnce([trade('t1')]);
    vi.mocked(tradesApi.getMyPendingTrades).mockResolvedValue([trade('t1'), trade('t2')]);
    renderHook(() => useNotificationSse(), { wrapper });
    const handlers = vi.mocked(sse.openEventStream).mock.calls[0][1];

    // Se reintenta la reconexión hasta que la carga inicial ha terminado (antes, checkTrades no hace nada);
    // llamarla varias veces no duplica toasts porque los ids vistos se recuerdan
    await waitFor(() => {
      handlers.onOpen!(true);
      expect(useToastStore.getState().toasts.map((t) => t.message)).toEqual(['Nueva propuesta de intercambio de brock']);
    });
  });
});
```

(Ajustar `trade()` a los campos obligatorios reales de `Trade` en `src/api/trades.ts` si `tsc` lo exige; el `as Trade` evita rellenar los que no usa el hook.)

- [ ] **Step 7:** `npx vitest run src/hooks/useNotificationSse.test.tsx` → FAIL (el hook aún crea `EventSource` directamente).

- [ ] **Step 8: useNotificationSse, implementación** — quitar `API_BASE`, el `new EventSource`, `es.onerror`, `es.onopen` y `es.close()`; importar `openEventStream` de `../api/sse`. Estructura final del `useEffect`:

```ts
  useEffect(() => {
    if (!username) return;

    let ready = false; // hasta cargar lo ya existente, nada se anuncia como nuevo

    async function initRefs() { /* igual que ahora */ }

    // Propuestas de intercambio que no se han visto: tras una reconexión y en el polling de respaldo
    async function checkTrades() {
      if (!ready) return;
      try {
        const trades = await queryClient.fetchQuery({
          queryKey: ['my-pending-trades'],
          queryFn: getMyPendingTrades,
          staleTime: 0,
        });
        for (const trade of trades) {
          if (!seenTradeIds.current.has(trade.id)) {
            seenTradeIds.current.add(trade.id);
            addToast('info', `Nueva propuesta de intercambio de ${trade.proposer}`, `/leagues/${trade.leagueId}/activity`);
          }
        }
      } catch {
        // red caída: se reintenta en el siguiente tick o reconexión
      }
    }

    let fallbackInterval: ReturnType<typeof setInterval> | null = null;
    const stopFallback = () => {
      if (fallbackInterval) clearInterval(fallbackInterval);
      fallbackInterval = null;
    };

    // El proxy de Netlify corta la conexión cada <26 s: se reconecta sola; mientras está caída, polling cada 120 s
    const close = openEventStream('/v1/users/events', {
      listeners: {
        steal: (e) => { /* cuerpo actual del listener 'steal' */ },
        'trade-proposed': (e) => { /* cuerpo actual del listener 'trade-proposed' */ },
      },
      onOpen: (reconnected) => {
        stopFallback();
        if (reconnected) checkTrades();
      },
      onDown: () => {
        if (!fallbackInterval) fallbackInterval = setInterval(checkTrades, 120_000);
      },
    });

    initRefs().then(() => { ready = true; }).catch(() => { ready = true; });

    return () => {
      close();
      stopFallback();
    };
  }, [username, queryClient, addToast]);
```

Los cuerpos de los listeners `steal` y `trade-proposed` se copian tal cual del código actual (líneas 50-85), cambiando `e: MessageEvent` por el parámetro `e` ya tipado por `EventStreamHandlers`.

- [ ] **Step 9:** `npx vitest run src/hooks src/api src/pages/DraftPage.test.tsx` → PASS.
- [ ] **Step 10:** commit `feat: SSE con reconexion (el proxy de Netlify corta las conexiones)`.

---

## Task 7: Front, checks de CI, push y PR

- [ ] **Step 1:** `npm run lint` (0 errores), `npm run api:check`, `npm test`, `npm run build`.
- [ ] **Step 2:** `git push -u origin fix/sesion-mismo-sitio` y PR contra `main` por la API. En la descripción: orden de merge (backend primero) y que el front tolera el backend viejo.

---

## Task 8: Verificación en el deploy preview (antes de mergear)

El PR del front genera `https://deploy-preview-<N>--pokefantasy.netlify.app`.

- [ ] **Step 1: el proxy responde.** `Invoke-WebRequest https://deploy-preview-<N>--pokefantasy.netlify.app/api/v3/api-docs` → 200 con JSON (no el `index.html`).
- [ ] **Step 2: Set-Cookie pasa por el proxy y queda en el propio dominio.** `Invoke-WebRequest -Method Post .../api/v1/user/logout` (público) → cabeceras `Set-Cookie: jwt=; ... Max-Age=0` y `refresh=...` **sin atributo `Domain`** (host-only, luego del dominio de Netlify).
- [ ] **Step 3: sin sesión → 401.** `POST .../api/v1/invite/x/redeem` → 401 con `"code":"UNAUTHENTICATED"` (requiere el backend de Task 1 desplegado; si aún no, 403 y se repite tras el merge del back).
- [ ] **Step 4: IP real del cliente.** El límite de logins fallidos usa la primera IP de `X-Forwarded-For` (`UserController.java:83`). Comprobar qué recibe el backend a través del proxy: commit temporal en la rama del front con `/__headers  https://httpbin.org/headers  200` al principio de `_redirects`, `Invoke-RestMethod .../__headers` y comprobar que `X-Forwarded-For` empieza por la IP pública propia (`Invoke-RestMethod https://api.ipify.org`). Revertir el commit temporal. **Si no llega la IP del cliente: PARAR y consultar al usuario** (todos los logins web compartirían contador por IP).
- [ ] **Step 5: prueba en iPhone (usuario).** Con Safari en iPhone, en el deploy preview: login, abrir un link de invitación `https://deploy-preview-<N>--pokefantasy.netlify.app/invite/<token>` y unirse; navegar por la liga; recargar la página y seguir dentro. Es la confirmación de que el problema de iOS está resuelto.
- [ ] **Step 6:** merge del back (#PR back) → esperar deploy en Render → merge del front.

---

## Task 9: Vault

`git pull` en `C:\PokeFantasy\vault` antes de editar; commit directo a `main` con `git commit -F` (archivo sin BOM: `[IO.File]::WriteAllText($f, $msg, (New-Object Text.UTF8Encoding $false))`).

- [ ] **Step 1:** `70 Decisiones/ADR-013 API en el mismo sitio via proxy de Netlify.md` desde `Plantillas/Plantilla Decisión.md`: contexto (Safari/iOS bloquea cookies de terceros; 403 al canjear invitaciones), decisión (proxy `/api/*`, nativo directo a Render, `VITE_API_URL` manda), consecuencias (timeout de 26 s del proxy: backend dormido → 504 en la primera petición, SSE reconectando; cookies del propio sitio; `X-Forwarded-For`), alternativas descartadas (dominio propio: coste; token en cabecera: contradice ADR-002). `estado: aceptada`, `fecha: 2026-09-28`, PRs.
- [ ] **Step 2:** `ADR-002 JWT en cookie httpOnly.md`: la consecuencia "Front y API en dominios distintos: obliga a `SameSite=None`" pasa a "en la web van por el proxy (ADR-013); `SameSite=None` sigue siendo necesario para la app Android".
- [ ] **Step 3:** `20 Arquitectura/Seguridad y auth.md`: 401 `UNAUTHENTICATED` sin sesión, `expireSession` en el front, proxy `/api`; `20 Arquitectura/API REST.md`: nota de que la web llama con prefijo `/api`.
- [ ] **Step 4:** `60 Operaciones/Gotchas.md`: "Safari/iOS y cookies de terceros (2026-09-28)" con síntoma (403 al canjear invitaciones), causa y arreglo.
- [ ] **Step 5:** `50 Features/Invitaciones y miembros.md`: quitar el "Pendiente" del registro (resuelto) y añadir PRs.
- [ ] **Step 6:** `40 Frontend/Estructura frontend.md`: `api/sse.ts` (SSE con reconexión), `utils/apiBaseUrl.ts`, `utils/session.ts`.
- [ ] **Step 7:** commit y push del vault.
