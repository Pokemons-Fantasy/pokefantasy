# Mejoras y features pendientes — Backend

Lista surgida de la revisión del backend (septiembre 2026). Se tacha cada punto al completarlo, indicando el PR.

## 🔴 Críticas (integridad de datos)

1. ~~**Actualizaciones perdidas en monedas y equipos.** `LeagueEntity`/`UserEntity` sin `@Version`; los handlers hacen leer → modificar → `save()` del documento completo, así que dos operaciones simultáneas se pisan `coinBalance` o `user.pokemons`.~~ ✅ PR #103 (`@Version` en league/user/schedule/trade/closed_list + updates atómicos que incrementan version).
2. ~~**Compensaciones manuales frágiles.** Steal/trade/swap/buy/steal-price/draft-pick escriben varios documentos por separado y "deshacen" con objetos en memoria desactualizados; si el proceso muere a mitad, el estado queda incoherente.~~ ✅ PR #103 (transacción MongoDB por comando en `SpringMediator` con reintentos; compensaciones eliminadas).
3. **Dos fuentes de verdad para los equipos.** `user.pokemons` y `draft.picks` se sincronizan a mano en cada operación. Hacer que `draft.picks` sea la única fuente y eliminar `UserEntity.pokemons`.
4. **Zona horaria de las ventanas de robo/swap.** `JornadaWindowService` usa `Clock.systemDefaultZone()` (UTC en Render): "viernes 16:00" es en realidad las 18:00 (verano) o 17:00 (invierno) en Madrid. Usar `ZoneId.of("Europe/Madrid")` o un campo `timezone` en `LeagueSettings`.

## 🟠 Seguridad

5. **Sin rate limiting en el login.** `POST /v1/user/login` permite fuerza bruta. Bucket4j o un contador en Redis por IP/usuario.
6. **Sin validación al registrarse.** No hay longitud mínima de contraseña ni restricción de caracteres en el username, y no hay ningún `@Valid` en el proyecto. Añadir `spring-boot-starter-validation`.
7. **Enumeración de usuarios.** `GET /v1/users/search` permite a cualquier usuario autenticado sacar todos los usernames por prefijo. Exigir un prefijo mínimo y ser admin de la liga.
8. **Auto-pick sin control de membresía.** `AutoPickDraftCommandHandler` deja que cualquier usuario autenticado dispare el auto-pick de una liga ajena.
9. **SSE del draft público.** `GET /draft/events` no requiere autenticación → posible DoS con conexiones abiertas. Autenticar por cookie, como `/users/events`.
10. **Sesión fija de 24 h.** Sin refresh token ni forma de revocar tokens (el logout solo borra la cookie). Añadir un refresh token en Redis.

## 🟡 Robustez e infraestructura

11. **Arranque dependiente de PokeAPI.** `PokemonCacheLoader` tumba el arranque si PokeAPI falla y Redis está vacío. Reintentos con backoff o carga en segundo plano.
12. **Auto-pick dependiente del cliente.** Si nadie tiene la app abierta, el draft se atasca. Añadir un `@Scheduled` en servidor que procese los turnos vencidos.
13. **Dockerfile.** Se ejecuta como root, no limita la memoria de la JVM (`-XX:MaxRAMPercentage=75`, clave con 512 MB), no cachea la capa de dependencias y usa `mvn` en vez de `./mvnw`.
14. **CI.** `workflow.yml` se lanza con push a `main`/`master`, pero la rama base es `develop`.
15. **Versiones mezcladas.** El `pom.xml` fija `spring-boot-starter-web` a `4.1.0` con parent `4.0.2`.
16. **SSE en memoria.** `SseEmitterRegistry` no escala a más de una instancia; haría falta Redis Pub/Sub.
17. **Errores como texto plano.** Migrar `ApiExceptionHandler` a `ProblemDetail` (RFC 7807) con un `code` estable.
18. **Sin corrección de resultados.** `RecordMatchResult` no permite corregir ni deshacer un resultado (revirtiendo también las monedas).

## 🔵 Calidad de código y tests

19. **Lógica duplicada** entre steal/trade/swap/buy/release: extraer un `TeamTransferService`.
20. **Tests.** `api-rest` no tiene tests e `infrastructure` tiene pocos. Faltan tests de integración con Testcontainers (Mongo + Redis), sobre todo de transacciones y concurrencia.
21. **Sin documentación de la API.** Añadir OpenAPI con springdoc y generar el cliente TS.
22. **Observabilidad.** Logs en JSON, métricas Micrometer de negocio y Sentry o similar para los errores 500.
23. **Paginación y rendimiento.** `findAllByLeagueId` y `findPendingByLeagueId` no paginan; el regex case-insensitive de `SearchUsers` impide usar el índice.

## ✨ Features nuevas

**Juego / competición**
- [ ] Playoffs entre los N primeros al acabar la liga regular.
- [ ] Marcador en los partidos (p. ej. 3–1) y diferencia como criterio de desempate.
- [ ] Temporadas: cerrar, archivar el histórico y empezar otra (redraft o keeper).
- [ ] Snake draft configurable y draft por subasta.
- [ ] Waiver wire / prioridad de banca para el peor clasificado.
- [ ] Trades 2-por-1, multi-Pokémon y contraofertas.
- [ ] Protección de Pokémon contra robos pagando monedas durante una jornada.

**Pokémon y datos**
- [ ] Estadísticas por Pokémon (victorias con él en el equipo, veces robado, ranking de "más robado").
- [ ] Importar resultados desde replays de Pokémon Showdown.
- [ ] Reglas por liga: bans de legendarios, límite por tipo o por generación.

**Social y notificaciones**
- [ ] Push por resultado registrado, apertura/cierre de ventana y "te toca en el draft".
- [ ] Chat/comentarios por liga o reacciones en el feed de actividad.
- [ ] Recordatorio programado antes de que cierre la ventana de robos/swaps.

**Cuenta**
- [ ] Cambio y recuperación de contraseña.
- [ ] Avatar.
- [ ] Borrado de cuenta (RGPD).
