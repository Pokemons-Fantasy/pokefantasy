# Player-to-player trades — Design spec

> Estado: aprobado para implementacion · Fecha: 2026-05-22

## 1. Contexto y objetivo

Un jugador (A) puede proponer a otro jugador (B) un intercambio 1×1 de Pokemon, con
monedas opcionales que A ofrece a B. B recibe la propuesta y decide aceptarla o
rechazarla. El intercambio solo se ejecuta si B acepta.

Es el siguiente paso del roadmap. Hoy existen dos mecanicas relacionadas que se
reutilizan como patron:

- `StealPokemonCommandHandler` — transfiere un `DraftPick` entre jugadores, mueve
  monedas, sincroniza `user.getPokemons()` + `DraftEntity`, aplica bloqueo por
  jornada (`lockedUntilRound`), valida ventana de tiempo.
- `SwapWithBenchCommandHandler` — intercambio 1×1 con el pool de la banca.

No existe sistema de notificaciones: B descubre las propuestas consultando un
endpoint nuevo (polling desde el frontend).

## 2. Reglas de producto

| Regla | Decision |
|-------|----------|
| Restriccion de tier | **Ninguna.** Cualquier Pokemon por cualquier Pokemon. El acuerdo mutuo entre dos personas es la salvaguarda; las monedas son el unico mecanismo de equilibrio. |
| Direccion de monedas | **Solo A → B.** `coinsOffered` es 0 o positivo; A endulza su oferta. Nunca puede pedir monedas a B. |
| Ventana de tiempo | Proponer es libre en cualquier momento (con draft `COMPLETED`). **Aceptar** solo dentro de la ventana de swap (`JornadaWindowService.isSwapWindowOpen`, vie 16:00). |
| Bloqueo post-trade | Los dos Pokemon intercambiados quedan con `lockedUntilRound = jornada activa`, igual que los Pokemon robados. |
| Propuestas multiples | Permitidas. A puede tener varias propuestas `PENDING`, incluso sobre el mismo Pokemon. Los conflictos se resuelven al aceptar (ver seccion 5). |
| Contraoferta | No. B solo acepta o rechaza. |
| Ambito de los Pokemon | Solo Pokemon del equipo (los `DraftPick` del jugador), no la banca. |

## 3. Modelo de datos

### Enum `TradeStatus`

`domain/.../dto/TradeStatus.java`, junto a `DraftStatus` y `MatchStatus`:

```
PENDING · ACCEPTED · REJECTED · CANCELLED
```

### `TradeEntity`

`domain/.../repository/entity/TradeEntity.java`, coleccion Mongo `trades`:

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | `String` PK | |
| `leagueId` | `String` FK | |
| `proposer` | `String` | username de A |
| `responder` | `String` | username de B |
| `proposerPokemonName` | `String` | Pokemon que A entrega |
| `proposerPokemonId` | `int` | resuelto desde el `DraftPick` de A |
| `responderPokemonName` | `String` | Pokemon que A quiere de B |
| `responderPokemonId` | `int` | resuelto desde el `DraftPick` de B |
| `coinsOffered` | `int` | >= 0, A paga a B |
| `status` | `TradeStatus` | empieza en `PENDING` |
| `createdAt` | `Instant` | |
| `resolvedAt` | `Instant` | null mientras `PENDING` |

### Puerto `TradeRepository`

`domain/.../repository/TradeRepository.java` + adaptador Mongo en `infrastructure`,
siguiendo el patron de `DraftRepository`. El adaptador se mantiene fino (delega en
Spring Data) para no hundir la cobertura JaCoCo.

- `TradeEntity save(TradeEntity)`
- `Optional<TradeEntity> findById(String id)`
- `List<TradeEntity> findByLeagueIdAndParticipant(String leagueId, String username)`
  — trades donde el usuario es `proposer` **o** `responder` (query `$or`); alimenta el `GET`.
- `List<TradeEntity> findPendingByLeagueId(String leagueId)` — para el auto-cancelado
  de propuestas en conflicto.

## 4. Backend — CQRS y endpoints

Carpeta `application/.../commands/trade/`. Cada caso de uso sigue el patron del
proyecto: `XCommand` (record que implementa `Command`) + `XCommandHandler`
(`@Service`) + entrada en `TradeFacade` + `TradeController` que inyecta solo la facade.

### Endpoints

El username del solicitante sale del JWT (mismo patron que `steal`).

| Metodo | Ruta | Accion |
|--------|------|--------|
| `POST` | `/v1/leagues/{id}/trades` | A propone |
| `GET` | `/v1/leagues/{id}/trades` | Lista trades donde el usuario participa (todos los estados) |
| `POST` | `/v1/leagues/{id}/trades/{tradeId}/respond` | B acepta o rechaza |
| `DELETE` | `/v1/leagues/{id}/trades/{tradeId}` | A cancela |

### Contrato de request/response

`POST /v1/leagues/{id}/trades` — body:

```json
{
  "responder": "brock",
  "proposerPokemonName": "pikachu",
  "responderPokemonName": "onix",
  "coinsOffered": 150
}
```

`POST /v1/leagues/{id}/trades/{tradeId}/respond` — body:

```json
{ "accept": true }
```

`GET /v1/leagues/{id}/trades` — devuelve una lista de objetos trade con todos los
campos de `TradeEntity` (id, leagueId, proposer, responder, los dos Pokemon con
nombre e id, coinsOffered, status, createdAt, resolvedAt).

`DELETE` y las respuestas de mutacion devuelven 200 sin cuerpo (o el trade
actualizado). Los errores siguen el mapping de `ApiExceptionHandler`:
`IllegalArgumentException` 400, `IllegalStateException` 409,
`ForbiddenOperationException` 403.

### `ProposeTradeCommandHandler`

`ProposeTradeCommand(leagueId, proposer, responder, proposerPokemonName,
responderPokemonName, coinsOffered)`.

Validaciones:
- Draft de la liga en estado `COMPLETED`.
- A y B son miembros de la liga; `proposer != responder`.
- A posee `proposerPokemon` (existe un `DraftPick` suyo con ese nombre); B posee
  `responderPokemon`.
- `coinsOffered >= 0`.
- Ninguno de los dos Pokemon esta bloqueado (`lockedUntilRound` activo).
- A tiene saldo de monedas >= `coinsOffered` (chequeo blando; el chequeo duro es al
  aceptar).

Resuelve `proposerPokemonId` y `responderPokemonId` desde los picks. Crea un
`TradeEntity` en estado `PENDING` con `createdAt = now`. No bloquea propuestas
multiples sobre el mismo Pokemon.

### `RespondToTradeCommandHandler`

`RespondToTradeCommand(leagueId, tradeId, respondingUser, accept)`.

- Carga el trade; debe existir y estar `PENDING`.
- `respondingUser` debe ser el `responder` del trade; si no →
  `ForbiddenOperationException` (403).
- **Reject** → `status = REJECTED`, `resolvedAt = now`, guarda.
- **Accept** → re-valida todo (red de seguridad dura, ver seccion 5) y ejecuta el
  intercambio.

### Ejecucion del intercambio (al aceptar)

Reutiliza la mecanica de `StealPokemonCommandHandler`:

1. **Monedas**: `A.coinBalance -= coinsOffered`; `B.coinBalance += coinsOffered`;
   guarda `LeagueEntity`.
2. **`user.getPokemons()`**: A pierde `proposerPokemon` y gana `responderPokemon`;
   B pierde `responderPokemon` y gana `proposerPokemon`. Se reconstruyen las
   entradas `Pokemons` (id, name, leagueId, stats, types desde `ClosedListEntity`).
   `userRepository.updateUserWithPokemons` para ambos.
3. **`DraftPick`**: el pick de A pasa a `username = B`; el de B pasa a
   `username = A`. Ambos con `lockedUntilRound = jornada activa` y
   `pickedAt = now`. Guarda `DraftEntity`.
4. `trade.status = ACCEPTED`, `resolvedAt = now`; guarda el trade.
5. **Auto-cancelado**: las demas propuestas `PENDING` de la liga que referencien
   cualquiera de los dos Pokemon intercambiados se marcan `CANCELLED` con
   `resolvedAt = now` (limpieza de UI).

### `CancelTradeCommandHandler`

`CancelTradeCommand(leagueId, tradeId, requestingUser)`. Solo el `proposer` puede
cancelar y solo si el trade esta `PENDING`. No-proponente →
`ForbiddenOperationException` (403). Resultado: `status = CANCELLED`,
`resolvedAt = now`.

### `GetTradesCommandHandler`

`GetTradesCommand(leagueId, username)`. Devuelve
`findByLeagueIdAndParticipant(leagueId, username)`. El frontend separa entrantes y
salientes y cuenta las `PENDING` entrantes para el badge.

## 5. Casos limite y validacion

- **proposer == responder** → rechazado al proponer (400).
- **coinsOffered negativo** → rechazado al proponer (400).
- **Pokemon no poseido** por quien corresponde → rechazado al proponer (400) y
  re-chequeado al aceptar.
- **Pokemon bloqueado** (`lockedUntilRound` activo) → rechazado al proponer y al
  aceptar (409).
- **Draft no `COMPLETED`** → 409.
- **Ventana de swap cerrada al aceptar** → 409 con mensaje claro.
- **Trade no `PENDING`** al responder o cancelar → 409.
- **No-responder responde / no-proposer cancela** → 403.
- **Saldo insuficiente de A al aceptar** → 409.
- **Propuesta obsoleta**: si al aceptar la re-validacion de propiedad falla (el
  Pokemon ya se movio por otro trade, robo o swap), el trade se auto-marca
  `CANCELLED` con `resolvedAt = now` en vez de quedar colgado `PENDING`. La
  re-validacion al aceptar es la red de seguridad dura: cubre toda condicion de
  carrera entre proponer y aceptar.

## 6. Frontend

El agente de frontend usa la skill `frontend-design` para el diseno visual; el spec
fija solo los requisitos funcionales.

- **Punto de entrada para proponer**: en `TeamsPage`, clic en el Pokemon de un rival
  (igual que el flujo de robo). Abre un modal: "Recibes _Y_ de _B_" prefijado; el
  usuario elige cual de SUS Pokemon entrega, fija las monedas a ofrecer (0 o
  positivo), confirma.
- **Panel de trades**: boton en la cabecera de `TeamsPage` con un **badge** que
  muestra el numero de propuestas `PENDING` entrantes. Abre un modal con:
  - Entrantes (`PENDING`, soy `responder`): proponente, los dos Pokemon, monedas,
    botones Aceptar / Rechazar.
  - Salientes (`PENDING`, soy `proposer`): destinatario, Pokemon, monedas, boton
    Cancelar.
  - Historico resuelto (`ACCEPTED`/`REJECTED`/`CANCELLED`): seccion colapsada,
    secundaria.
- **Capa de datos**: funciones nuevas en la capa API; hook `useTrades(leagueId)` y
  mutaciones propose/respond/cancel con React Query.
  - Invalidaciones: propose/cancel/reject → `['trades', leagueId]`. Accept → ademas
    `['draft', leagueId]` (cambian los equipos) y `['my-coins', leagueId]`.
  - Polling moderado por la restriccion de Render free tier: refetch al montar y al
    recuperar foco (defaults de React Query) + un `refetchInterval` discreto
    mientras se esta en `TeamsPage`. El agente de frontend fija el intervalo exacto.
- **Errores**: los mensajes 409/403/400 del backend se muestran en el modal
  correspondiente.

## 7. Tests

- **Backend**: suite unitaria Mockito, una clase por handler —
  `ProposeTradeCommandHandlerTest`, `RespondToTradeCommandHandlerTest`,
  `CancelTradeCommandHandlerTest`, `GetTradesCommandHandlerTest`. Cubrir todas las
  ramas de validacion de la seccion 5, la ejecucion del intercambio y el
  auto-cancelado de propuestas en conflicto. El gate JaCoCo 80% (instruccion y rama,
  nivel BUNDLE) debe seguir verde; verificar con `./mvnw -B -ntp clean verify`.
- **Frontend**: `./node_modules/.bin/tsc --noEmit` sin errores; verificacion manual
  del flujo propuesta → aceptar / rechazar / cancelar.

## 8. Despliegue

Tarea acoplada por contrato de API → flujo de dos agentes **secuencial**:

1. **Agente backend** (Sonnet, repo `pokefantasy`): rama `feature/player-trades`
   (ya creada, contiene este spec), skill `test-driven-development`, implementa
   todo, `mvn verify` verde, commit, push, PR a `develop`.
2. **Agente frontend** (Sonnet, repo `pokefantasy-web`): arranca con el contrato de
   API real del paso 1, rama `feature/player-trades`, skill `frontend-design`,
   `tsc --noEmit` limpio, commit, push, PR a `main`.
3. Al cerrar: actualizar el roadmap de `CLAUDE.md` (trades → Done) y
   `docs/DIAGRAMS.md`.
