# Borrado de cuenta y privacidad (L2): diseño

Fecha: 2026-10-18. Origen: auditoría de seguridad y legal 2026-09, punto L2 (`vault/60 Operaciones/Auditoría seguridad y legal 2026-09.md`). Nota del roadmap: `vault/50 Features/Borrado de cuenta.md`.

## Objetivo

Cumplir lo mínimo del RGPD para una web abierta a cualquiera:
- Derecho de supresión (art. 17): el usuario borra su cuenta desde la app.
- Información (art. 13): una página pública de privacidad.
- Edad mínima de 14 años (LOPDGDD art. 7), aceptada al registrarse.

Éxito: tras borrar la cuenta no queda en la base de datos ningún dato que identifique a la persona. Las ligas en las que estaba siguen funcionando, sin huecos ni errores.

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Qué pasa en cada liga | Sale como en una expulsión. Sus Pokémon vuelven a la banca, que es lo que no es de nadie. |
| Único admin de una liga | Pasa a admin el miembro más antiguo que quede, el primero de `members`: los miembros se guardan en orden de entrada y no hay fecha de alta. |
| Liga que se queda vacía | Se borra con todo lo suyo. |
| Su nombre en el historial | Se anonimiza a `Usuario eliminado` en actividad, trades y partidos. |
| Trades pendientes | Se cancelan (`CANCELLED`). |
| Borrado inmediato o diferido | Inmediato, en una transacción. Sin periodo de arrepentimiento (YAGNI). |
| Confirmación | Contraseña actual. Sus fallos cuentan en el mismo límite que el login. |

## Back

### `LeagueMemberRemoval`: un dueño para "sacar a un miembro de una liga"

Hoy esa lógica vive dentro de `RemoveMemberFromLeagueCommandHandler`: quita al miembro, quita sus picks del draft activo, recalcula los turnos y, si el draft estaba completado, lo vuelve a cerrar. Se extrae a un servicio de `application` (`league/LeagueMemberRemoval`), que pasa a ser el dueño de la tabla 2 del `CLAUDE.md`.

- `remove(LeagueEntity league, String username)`: lo que hace hoy el handler a partir de `leagueRepository.removeMember`.
- `RemoveMemberFromLeagueCommandHandler` mantiene sus comprobaciones (quién puede expulsar, que el objetivo sea miembro, que la liga no se quede sin admin) y delega en el servicio. Su comportamiento no cambia.

### `DeleteAccountCommand(username, password)`

Un comando por el mediator, en una transacción con reintentos (ADR-007). Relee todo dentro de la transacción. El handler:

1. **Contraseña.** Usa el mismo límite que `ChangePasswordCommandHandler`: clave `user:<nombre en minúsculas>`, `LoginUserCommandHandler.MAX_FAILURES_PER_USER` y `LOCKOUT_WINDOW`.
   - Si está bloqueado: `TooManyAttemptsException` (429).
   - Si la contraseña es incorrecta: registra el fallo y lanza `BadCredentialsException` (401 `INVALID_CREDENTIALS`).
   - Si es correcta: limpia los fallos.
2. **Cada liga** (`leagueRepository.findByMemberUsername`):
   - Si es el único miembro, se borra la liga con todo lo suyo: draft o drafts, calendario, trades, eventos de actividad y lista cerrada de tiers. Para eso hacen falta métodos de borrado por `leagueId` en esos repositorios.
   - Si no, y es el único admin, el primer miembro restante pasa a `LeagueRole.ADMIN`.
   - Después, `LeagueMemberRemoval.remove(league, username)`.
3. **Anonimización** en las ligas que siguen existiendo. Valor: `DeletedUser.NAME = "Usuario eliminado"`. Lleva un espacio, así que no puede chocar con un username válido (`^[A-Za-z0-9_-]{3,20}$`).

   | Entidad | Campos |
   |---|---|
   | `ActivityEventEntity` | `actorUsername`, `targetUsername` |
   | `TradeEntity` | `proposer`, `responder`. Los `PENDING` pasan antes a `CANCELLED` con `resolvedAt = now`. |
   | `ScheduleEntity` (partidos) | `player1`, `player2`, `winnerUsername` |
   | `DraftEntity` de temporadas anteriores (no el activo, del que ya se quitaron los picks) | `DraftPick.username` |
   | `LeagueEntity` | `createdBy` |

   Cada repositorio gana un método `anonymizeUser(String leagueId, String username, String replacement)` con un update de Mongo por consulta. No se cargan y guardan documentos enteros: así no se pisan datos ajenos (regla 6).
4. **Borrado:** `AvatarRepository.deleteByUsername` y el `UserEntity`, que incluye sus tokens FCM. Para esto hace falta `UserRepository.deleteByUsername`.
5. **Después del commit:** `RefreshTokenPort.revokeAll(username)` cierra todas sus sesiones. Redis no forma parte de la transacción de Mongo. Si la revocación fallase, los refresh tokens apuntarían a un usuario que ya no existe y `JwtAuthFilter` ya los revoca al no encontrarlo (`UsernameNotFoundException`).
6. **Tiempo real:** el comando devuelve los `leagueId` de las ligas que siguen existiendo. Tras el commit, el controller llama a `RealtimeNotifier.draftUpdated(leagueId)` en cada una, para que los demás vean en el momento los Pokémon que vuelven a la banca. La expulsión normal no emite nada hoy, y esto no lo cambia.

Todo cambio en una liga pasa por el `@Version` de las entidades (regla 6).

### Endpoint

`DELETE /v1/users/me` con cuerpo `{ "password": "..." }` y sesión obligatoria. Responde 204 y borra las cookies `jwt` y `refresh` (`AuthCookies.clear`).

- No va en `DELETE /v1/user`: `/v1/user` está en `SecurityConfig.PUBLIC_PATHS`, que no distingue método, y quedaría accesible sin sesión.
- Va con `@AuthenticationPrincipal`: el usuario sale del token, nunca del cuerpo.
- Errores: 401 `INVALID_CREDENTIALS`, 429 `TOO_MANY_ATTEMPTS`, 400 `BAD_REQUEST` si falta la contraseña.

## Front

- **Mi perfil:** sección "Borrar cuenta" al final, con un botón destructivo que abre `DeleteAccountModal`.
  - El modal explica qué pasa: sales de tus ligas, tus Pokémon vuelven a la banca, tu nombre aparece como "Usuario eliminado" en el historial y no se puede deshacer.
  - Pide la contraseña y tiene un botón destructivo con estado de carga.
  - Con `role="dialog"`, foco gestionado y `prefers-reduced-motion`.
  - Al terminar: `authStore.logout` local, `queryClient.clear()`, navegación a `/login` y un toast "Cuenta borrada".
  - Los errores salen con `extractErrorMessage`, sin cerrar el modal.
- **`src/api/auth.ts`:** función `deleteAccount(password)`, y su entrada en `contract.ts` si devuelve tipo (204 no lo hace).
- **Registro:** casilla obligatoria "Tengo 14 años o más y he leído la [política de privacidad]". Sin marcar, el botón no envía y se muestra el motivo. No se guarda en el back: el registro solo se completa con ella marcada.
- **`/privacy`:** página pública, como `/about`. Contenido:
  - Responsable y contacto.
  - Datos que se tratan: username, hash de la contraseña, foto de perfil, IP (límite de intentos y logs del servidor), tokens de push y actividad en las ligas.
  - Finalidad y base legal: prestar el servicio (art. 6.1.b).
  - Encargados y transferencias: Render, Netlify, MongoDB Atlas, Upstash, Firebase, Sentry y GitHub. Están fuera de la UE o tienen presencia allí, cubiertos por el EU-US Data Privacy Framework o cláusulas contractuales tipo.
  - Conservación: mientras la cuenta exista. Las IP de los límites, 15 minutos a 30 días según el límite. Los logs, lo que guarde Render.
  - Cookies técnicas: `jwt`, `refresh`, `auth-storage`, `pf-theme`. No hace falta banner.
  - Sprites desde la CDN de GitHub.
  - Derechos (acceso, rectificación, supresión con el botón de borrar cuenta, oposición, portabilidad) y reclamación ante la AEPD.
  - Edad mínima: 14 años.
- **`src/legal.ts`** (ya existe, front #127): `/privacy` usa `LEGAL_CONTROLLER` y `LEGAL_CONTACT_EMAIL`.
- **Enlaces a privacidad:** en `FanDisclaimer` (login y registro), en `/about` y en Mi perfil.

### Responsable y contacto

- Responsable: **Adrian Rafael Rego Rodriguez**.
- Contacto: **villuprise@gmail.com**.

Ya están en `src/legal.ts` (`LEGAL_CONTROLLER`, `LEGAL_CONTACT_EMAIL`) desde front #127, con `legal.test.ts` y el contacto en `/about`. L2 solo los reutiliza en `/privacy`.

## Compatibilidad

- El endpoint es nuevo: el front viejo no lo usa, y el front nuevo con el back viejo daría 404 en el botón de borrar. Orden: primero el back, luego el front.
- La casilla del registro y `/privacy` no dependen del back.

## Pruebas

**Back (unitarios, Mockito):**
- `DeleteAccountCommandHandler`:
  - contraseña incorrecta: registra el fallo y no toca nada;
  - bloqueado: 429;
  - liga con más miembros: llama a `LeagueMemberRemoval` y anonimiza;
  - único admin: el primer miembro restante pasa a admin;
  - único miembro: se borra la liga y no se anonimiza;
  - trades pendientes: pasan a `CANCELLED`;
  - borra la foto y el usuario;
  - `revokeAll` va después del commit.
- `LeagueMemberRemoval`: los tests que hoy cubren el handler de expulsión pasan a cubrir el servicio. El handler conserva los de permisos.
- Repositorios (`*RepositoryImplTest`): cada `anonymizeUser` cambia solo los campos de ese usuario en esa liga, y los borrados por `leagueId`.

**Back (IT en `boot/.../it/`):**
- `AccountDeletionIntegrationTest`: ash está en dos ligas, en una es el único admin y en la otra hay un trade pendiente. Tras `DELETE /v1/users/me`:
  - 204 y cookies borradas;
  - sus Pokémon están en la banca;
  - el otro miembro es admin;
  - el trade está `CANCELLED` y a nombre de `Usuario eliminado`;
  - el login con su usuario da 401;
  - registrarse de nuevo con ese nombre funciona.
- Liga donde es el único miembro: tras borrar la cuenta, la liga ya no existe.

**Front (vitest):**
- `DeleteAccountModal`: pide la contraseña, llama a `deleteAccount`, cierra la sesión y navega; un error se muestra sin cerrar.
- `RegisterPage`: no envía sin la casilla marcada.
- `PrivacyPage`: muestra responsable, contacto, encargados y el enlace a borrar cuenta.
- (`legal.test.ts` ya existe.)

## Fuera de alcance

- L5 (normas de uso y moderación por un admin global): spec aparte.
- Exportar los datos (portabilidad): por ahora se atiende por email.
- Borrado diferido o con periodo de arrepentimiento.
- Registrar en el back la aceptación de la edad mínima y la privacidad.
