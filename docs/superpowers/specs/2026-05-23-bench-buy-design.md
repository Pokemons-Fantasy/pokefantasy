# Spec: Comprar Pokémon de la banca con monedas

_Fecha: 2026-05-23_

## Contexto

Tras el draft, los Pokémon no reclamados quedan en la "banca". Actualmente los jugadores solo
pueden interactuar con la banca mediante el swap 1x1 (entregan uno de sus Pokémon y reciben uno
de la banca). Esta feature añade una segunda opción: comprar un Pokémon de la banca pagando
monedas, sin ceder ninguno del equipo propio. Amplía la economía de la liga y da salida a las
monedas acumuladas.

---

## Decisiones de diseño acordadas

| Pregunta | Decisión |
|----------|----------|
| Ventana de tiempo | Misma que el swap (abierta hasta el viernes 16:00 de la semana de la jornada activa) |
| UX en frontend | Modal unificado: click en carta de banca -> elegir "Intercambiar" o "Comprar" |
| Arquitectura backend | Handler separado (no extender SwapWithBenchCommandHandler) |
| round en DraftPick | 0 (centinela "comprado de la banca"; el draft usa rounds 1..N) |

---

## 1. Backend

### Nuevo caso de uso CQRS

Carpeta: `application/src/main/java/com/villu/pokefantasy/commands/bench/`

**`BuyFromBenchCommand.java`**
```java
public record BuyFromBenchCommand(String leagueId, String username, String pokemonName)
    implements Command {}
```

**`BuyFromBenchCommandHandler.java`** — `@Service implements CommandHandler<BuyFromBenchCommand, Void>`

Validaciones en orden:
1. Draft status = COMPLETED — `IllegalStateException` si no
2. Swap window abierta: `jornadaWindowService.isSwapWindowOpen(schedule)` — `IllegalStateException` si cerrada
3. Usuario es miembro de la liga — `IllegalArgumentException` si no
4. `pokemonName` existe en `ClosedListEntity` del leagueId — `IllegalArgumentException` si no
5. Pokemon NO esta en el equipo de ningun jugador (banca real) — `IllegalStateException` si ya tomado
6. Equipo del comprador no supera `maxTeamSize` (`settings.getMaxTeamSize() ?? 20`) — `IllegalStateException` si lleno
7. Comprador tiene monedas suficientes (`buyerMember.getCoinBalance() >= price`) — `IllegalStateException` si no

Actualizaciones (en este orden):
1. `buyerMember.setCoinBalance(balance - price)` -> `leagueRepository.save(league)`
2. Construir `Pokemons` desde `ClosedListEntity` y añadir a `buyer.getPokemons()` -> `userRepository.updateUserWithPokemons(buyer)`
3. Construir `DraftPick{username, pokemonName, pokemonId, round=0, pickedAt=now(), lockedUntilRound=null, customStealPrice=null}` y añadir a `draft.getPicks()` -> `draftRepository.save(draft)`
4. Loguear `ActivityEvent{type=BENCH_PURCHASE, leagueId, actor=username, pokemonName, coinsSpent=price}`

**`BuyFromBenchFacade.java`** — `@Service`
```java
public void buyFromBench(String leagueId, String username, String pokemonName) {
    mediator.send(new BuyFromBenchCommand(leagueId, username, pokemonName));
}
```

### Nuevo endpoint

Archivo: `api-rest/src/main/java/com/villu/pokefantasy/BenchController.java` — añadir:

```java
@PostMapping("/buy")
public ResponseEntity<Void> buyFromBench(
    @PathVariable String leagueId,
    @AuthenticationPrincipal UserDetails user,
    @RequestBody BuyFromBenchRequest request) {
    buyFromBenchFacade.buyFromBench(leagueId, user.getUsername(), request.pokemonName());
    return ResponseEntity.ok().build();
}
```

**`BuyFromBenchRequest.java`** (record en api-rest):
```java
public record BuyFromBenchRequest(String pokemonName) {}
```

Ruta: `POST /v1/leagues/{leagueId}/bench/buy`

---

## 2. Frontend

### Cambio en TeamsPage.tsx

El click en una carta de banca llama a `handleBenchClick(entry)` que hoy abre `SwapModal`.
Cambio: el mismo handler setea `modalBench` pero ahora el modal renderizado es `BenchActionModal`.
`SwapModal` sigue existiendo sin cambios en su logica; `BenchActionModal` lo llama internamente.

### Nuevo componente `BenchActionModal`

Estado interno: `view: 'choose' | 'swap' | 'buy'` (inicia en `'choose'`)

**Vista `'choose'`:** dos botones de accion con icono, nombre y descripcion breve.
Si `myTeam.picks.length === 0`, se omite la opcion "Intercambiar".

**Vista `'swap'`:** renderiza el contenido actual del `SwapModal` + boton "← Volver".

**Vista `'buy'`:** resumen de saldo (actual / coste / resultado), boton "Confirmar compra",
boton "← Volver". Muestra `buyError` si la mutation falla.

### Nueva funcion API

`src/api/pokemons.ts`:
```ts
export async function buyFromBench(leagueId: string, pokemonName: string): Promise<void> {
  await api.post(`/leagues/${leagueId}/bench/buy`, { pokemonName });
}
```

### Nueva mutation en TeamsPage

```ts
const { mutate: doBuy, isPending: buying } = useMutation({
  mutationFn: (pokemonName: string) => buyFromBench(leagueId!, pokemonName),
  onSuccess: () => {
    setModalBench(null);
    setBuyError('');
    queryClient.invalidateQueries({ queryKey: ['draft-status', leagueId] });
    queryClient.invalidateQueries({ queryKey: ['bench', leagueId] });
    queryClient.invalidateQueries({ queryKey: ['my-coins', leagueId] });
  },
  onError: (err: Error) => setBuyError(err.message ?? 'Error al comprar'),
});
```

---

## 3. ActivityEvent

Tipo nuevo: `BENCH_PURCHASE`.
- Backend enum value: `BENCH_PURCHASE`
- Descripcion frontend (ActivityPage.tsx): `"{actor} compró {pokemonName} de la banca por {coinsSpent} monedas"`

---

## 4. Tests (application module)

Archivo: `BuyFromBenchCommandHandlerTest.java` — Mockito puro, patron identico a
`SwapWithBenchCommandHandlerTest`.

| Test | Resultado esperado |
|------|--------------------|
| Compra exitosa | coinBalance reducido, UserEntity actualizado, DraftPick añadido con round=0 |
| Draft no COMPLETED | IllegalStateException |
| Ventana cerrada | IllegalStateException |
| Pokemon ya tomado (no en banca) | IllegalStateException |
| Equipo lleno (maxTeamSize) | IllegalStateException |
| Monedas insuficientes | IllegalStateException |
| Pokemon no existe en closed list | IllegalArgumentException |

---

## 5. Verificacion

```bash
# Backend
./mvnw -B -ntp test -pl application -am
./mvnw -B -ntp clean verify

# Frontend
./node_modules/.bin/tsc --noEmit
npm run build
```
