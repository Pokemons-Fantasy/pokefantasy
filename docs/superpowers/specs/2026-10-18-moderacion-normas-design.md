# Normas de uso y moderación (L5): diseño

Fecha: 2026-10-18. Origen: auditoría de seguridad y legal 2026-09, punto L5 (`vault/60 Operaciones/Auditoría seguridad y legal 2026-09.md`): "nada impide contenido ofensivo en nombres de liga, usernames o fotos". Se apoya en el borrado de cuenta (L2, back #178, front #128).

## Objetivo

Publicar unas normas de uso y que el admin global (el responsable del proyecto) pueda retirar contenido ofensivo cuando le llegue un aviso al email de contacto. Lo hace desde un panel en la web, también desde el móvil.

Éxito: con el rol `ADMIN`, en menos de un minuto se encuentra un usuario o una liga y se quita una foto, se renombra una liga o se borra una cuenta. Nadie sin ese rol puede hacer nada de eso.

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Forma de moderar | Panel `/admin` en la web, solo para el rol global `ADMIN`. |
| Acciones | Quitar la foto de perfil, renombrar una liga y borrar un usuario. Sin bloqueo temporal. |
| Renombrar usuarios | No: el username es su identidad en todas partes. Un nombre ofensivo se resuelve borrando la cuenta. |
| Asignar el rol | A mano en Atlas (`users.role = "ADMIN"`), como hoy. Sin endpoint. |
| Nombre de liga | Regla común `LeagueNamePolicy` al crear y al renombrar: sin espacios sobrantes, no vacío, como mucho 40 caracteres. |
| Normas | Página pública `/terms`. La casilla del registro pasa a cubrir privacidad y normas. |

## Back

### Seguridad
- Rutas bajo `/v1/admin/**`. En `SecurityConfig`, `hasRole("ADMIN")`, igual que `/actuator/metrics`. Sin rol, 403 (el `AccessDeniedHandlerImpl` por defecto).
- El controller nuevo (`AdminController`) solo usa una `AdminFacade` (regla 4).
- Cada acción deja `log.info("Admin {} …", admin, objetivo)`. Sin tabla de auditoría (YAGNI).

### Endpoints
| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/v1/admin/users?q=` | Hasta 20 usuarios por prefijo (`findByUsernamePrefix`, ya existe): `[{ username, avatarVersion, admin }]`. |
| GET | `/v1/admin/leagues?q=` | Hasta 20 ligas cuyo nombre contiene `q`, sin distinguir mayúsculas (regex con `Pattern.quote`): `[{ id, name, members }]`. |
| DELETE | `/v1/admin/users/{username}/avatar` | Quita la foto, reutilizando `DeleteAvatarCommand`. 204. |
| PUT | `/v1/admin/leagues/{id}/name` | `{ name }` → `RenameLeagueCommand`. 204. 400 si no cumple `LeagueNamePolicy`; 400 "Liga no encontrada". |
| DELETE | `/v1/admin/users/{username}` | Borra la cuenta sin contraseña. 204. 403 si el objetivo es `ADMIN`; 400 si no existe. |

### Borrado de un usuario por el admin
- Se extrae de `DeleteAccountCommandHandler` un servicio `AccountDeletionService.delete(String username) → List<String>`, con todo lo que hace hoy después de comprobar la contraseña: reservar el nombre, salir de las ligas, anonimizar, borrar foto, sesiones y usuario. Pasa a ser el dueño de la tabla 2 del `CLAUDE.md`.
- `DeleteAccountCommandHandler` comprueba la contraseña y delega en el servicio.
- `AdminDeleteUserCommand(adminUsername, targetUsername)` comprueba que el objetivo exista y no sea `ADMIN`, y delega en el servicio.
- El controller hace lo mismo que el borrado propio tras el commit: `revokeSessions`, `UserSseEmitterRegistry.closeAll` y `draftUpdated` en las ligas que quedan.

### `GET /v1/user/me`
`CurrentUserResponse` gana `admin` (boolean, `role == ADMIN`). Es un campo nuevo y opcional: el front viejo lo ignora. Se regeneran `openapi.json` y `schema.d.ts` en el front.

### `LeagueNamePolicy`
- `static String normalize(String name)`: recorta los espacios y valida. Error: `IllegalArgumentException("El nombre de la liga debe tener entre 1 y 40 caracteres.")`.
- La usan `CreateLeagueCommandHandler` (sustituye su comprobación de "no vacío" y guarda el nombre recortado) y `RenameLeagueCommandHandler`.
- Las ligas existentes con más de 40 caracteres no se tocan.

## Front

### `/terms` (pública, mismo formato que `/about`)
Normas breves:
1. Sé respetuoso: nada de insultos, odio, contenido sexual ni datos personales de otros en nombres de usuario, nombres de liga o fotos.
2. Una cuenta por persona. No suplantes a otros jugadores.
3. No intentes romper ni saturar el servicio.
4. Si algo incumple las normas, el administrador puede quitar fotos, cambiar nombres de liga o borrar cuentas, sin aviso previo.
5. Avisos y dudas: `LEGAL_CONTACT_EMAIL`.
6. Proyecto fan sin garantías: puede cambiar o cerrar en cualquier momento.

Enlaces: en `FanDisclaimer` ("Normas"), en `/about` y en la casilla del registro, que pasa a decir "Tengo 14 años o más y he leído la política de privacidad y las normas de uso".

### `/admin`
- Ruta dentro de `ProtectedRoute`. Si `me.admin` no es true, redirige a `/` (el back lo protege igualmente).
- Enlace "Panel de administración" en Mi perfil, solo para admins.
- Dos secciones, Usuarios y Ligas. Cada una con un buscador (debounce, mínimo 2 caracteres) y una lista de resultados.
  - En un usuario: avatar y nombre. Botones "Quitar foto" (solo si tiene foto) y "Borrar cuenta" (no aparece en otros admins). Los dos piden confirmación con `ConfirmDialog` (`danger`).
  - En una liga: nombre y nº de miembros. Botón "Renombrar", que abre un campo con el nombre actual y lo valida con la misma regla (1-40).
- Después de cada acción: toast y se invalida la búsqueda. Errores con `extractErrorMessage`.
- Llamadas en `src/api/admin.ts` y tipos en `contract.ts`.

## Compatibilidad
- El back nuevo con el front viejo no rompe nada: `admin` es un campo extra.
- El front nuevo con el back viejo: sin `admin`, el panel no aparece.
- Orden: L2 (#178, #128) antes que esto, porque la rama sale de la del borrado de cuenta.

## Pruebas
- **Back unitarios:**
  - `LeagueNamePolicy`: recorta; vacío o 41 caracteres da error; 40 vale.
  - `RenameLeagueCommandHandler`: renombra, aplica la regla y da "Liga no encontrada".
  - `AdminDeleteUserCommandHandler`: un objetivo `ADMIN` da 403; si no existe, 400; si no, delega en el servicio.
  - `AccountDeletionService`: hereda los tests actuales del handler del borrado.
  - Los handlers de búsqueda.
- **Back IT** (`AdminIntegrationTest`):
  - un `USER` recibe 403 en los cinco endpoints;
  - un `ADMIN` busca, quita una foto, renombra una liga y borra un usuario, que queda anonimizado;
  - no puede borrar a otro admin;
  - `GET /v1/user/me` devuelve `admin`.
- **Front:**
  - `AdminPage`: un no admin es redirigido; buscar, confirmar y llamar a la API; renombrar con validación.
  - `TermsPage`.
  - La casilla del registro con los dos enlaces.
  - Mi perfil muestra el enlace al panel solo a admins.

## Fuera de alcance
- Bloqueo temporal de cuentas.
- Panel de avisos o denuncias dentro de la app: siguen llegando por email.
- Registro persistente de acciones de moderación.
- Gestión de roles desde la app.
