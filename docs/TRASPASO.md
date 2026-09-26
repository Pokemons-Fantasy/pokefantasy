# Traspaso: qué se hizo, qué tener en cuenta y cómo trabajar en local

Resumen de la tanda de mejoras de septiembre de 2026 (backend PRs #107–#115, frontend PRs #75–#78). Todo está mergeado y desplegado.
La referencia detallada y viva es `CLAUDE.md`; este documento es el "por qué" y la guía para seguir.

---

## 1. Qué se ha hecho

### Seguridad (backend #107)
- **Sesión con refresh token**: JWT de acceso de 15 min + cookie `refresh` opaca guardada en Redis (`refresh:<sha256>`), válida 30 días sin uso. `JwtAuthFilter` renueva el JWT solo; el frontend no hace nada. El logout revoca el refresh.
- **Búsqueda de usuarios** solo para admins de la liga (`leagueId` obligatorio).
- **Draft**: auto-pick y SSE solo para miembros de la liga; máximo 3 conexiones SSE por usuario y liga.

### Robustez (backend #108–#111)
- **Caché de Pokémon en segundo plano** (`PokemonCacheLoader`, `@Scheduled`): la app arranca aunque PokeAPI o Redis fallen y reintenta cada minuto. Mientras no hay caché, nominar da 409.
- **Auto-pick en servidor** (`DraftTurnTimeoutJob`, cada 15 s): el draft ya no se queda atascado si nadie tiene la app abierta.
- **Docker** sin root, con límites de memoria para los 512 MB de Render; **CI** con build de la imagen y CodeQL.
- **Errores `ProblemDetail`** con `code` estable y `requestId`.
- **Corregir/deshacer resultados** (`PUT`/`DELETE .../result`): se devuelven exactamente las monedas que se dieron.
- **SSE multi-instancia** por Redis Pub/Sub (`RealtimeNotifier`). Si Redis cae, se entrega en local y la app sigue arrancando.
- **Timeouts de Redis a 2 s**: antes, con Redis caído, cada petición se colgaba 60 s.

### Calidad (backend #112)
- **Tests de integración reales** con Testcontainers (Mongo en replica set + Redis) y tests de todos los controladores. Cobertura mínima del 80 % en `application`, `infrastructure` y `api-rest`.
- **`TeamTransferService`**: todas las reglas de robo/trade/swap/compra/liberación en un sitio.
- **OpenAPI** (`/v3/api-docs`, `/swagger-ui.html`), **request id** en logs y errores, **métricas** por comando, **logs JSON** y **Sentry** opcionales.
- **Índices de MongoDB**: descubrimos que **no se estaba creando ninguno** (la clave de configuración de Boot 4 era otra). Ahora los crea `MongoIndexInitializer` al arrancar.
- Búsqueda de usuarios indexada y límite en el historial de trades.

### Features y ajustes (backend #113–#115, frontend #75–#78)
- Frontend al día con el backend: SSE con cookies, corregir/deshacer resultados, reglas de registro visibles antes de enviar.
- **Historial de monedas cuadrado**: al anular un resultado se registra `COIN_REVOKED` (−X).
- **Tipos TypeScript generados desde la API** (`openapi.json` → `src/api/schema.d.ts`) y `src/api/contract.ts`, que hace fallar `tsc` si el frontend y el backend dejan de cuadrar.
- **Cambio de contraseña** (Mi perfil → Cuenta): pide la actual, cierra las sesiones de los demás dispositivos.
- **Push "¡Te toca en el draft!"** y **aviso 3 h antes de que cierre la ventana** de robos o swaps.
- **Marcador opcional** en los partidos (p. ej. 3–1); la clasificación desempata por diferencia antes que por monedas.

---

## 2. Cosas importantes para próximos desarrollos

**Reglas de la arquitectura (las más fáciles de romper)**
- Cada caso de uso: `XCommand` (**debe implementar `Command`**, o el mediator lo ignora) → `XCommandHandler` → fachada → controlador. Nunca llames a un handler desde otro sitio.
- Cada comando corre **en una transacción de Mongo y se reintenta hasta 5 veces** si hay conflicto. Por eso:
  - relee de BD todo lo que valides dentro del handler;
  - no hagas efectos externos (emails, llamadas HTTP) dentro del handler: se repetirían en cada reintento. Los push ya se envían tras el commit;
  - no escribas "compensaciones" manuales: si algo falla, se deshace todo solo.
- **Equipos**: la única fuente de verdad son los `DraftPick` del último draft. Cualquier movimiento de equipo, por `TeamTransferService`.
- **Resultados**: siempre por `MatchResultService` (monedas, marcador y eventos de actividad van juntos).
- **Tiempo real**: emite con `RealtimeNotifier`, nunca con los registros SSE directamente.
- **Horas de las ventanas**: son hora de España (`JornadaWindowService.LEAGUE_ZONE`); el servidor de Render está en UTC.

**Cuando cambies la API**
1. Cambia el backend (DTO/endpoint).
2. En el frontend: `npm run api:spec -- <url del backend>` y `npm run api:types`.
3. Si `tsc` falla en `src/api/contract.ts`, es que un tipo escrito a mano ya no cuadra: corrígelo.
4. Commitea `openapi.json` y `src/api/schema.d.ts` (la CI comprueba que corresponden).

**MongoDB**
- Si añades una entidad con índices, añádela a `INDEXED_ENTITIES` en `MongoIndexInitializer`.
- Los cambios de esquema de documentos antiguos se hacen con una migración al arrancar (mira `VersionFieldMigration` o `UserNameLowerMigration` como ejemplo). Campos nuevos que puedan venir `null` en documentos viejos: trátalos siempre como opcionales.
- Todas las entidades importantes tienen `@Version`: un `save()` con datos viejos falla en vez de pisar cambios.

**Tests**
- La CI exige 80 % de cobertura: si añades código sin tests, el PR se pone rojo.
- Para Mongo con Testcontainers 2 hace falta `.withReplicaSet()`; sin él no hay transacciones y los tests mienten.

**Pendientes y avisos**
- **Revisa que no haya nombres de usuario duplicados en producción**, o el índice único de `users.name` no se habrá creado (sale un ERROR en el log al arrancar). En la consola de Atlas:
  `db.users.aggregate([{$group:{_id:"$name",n:{$sum:1}}},{$match:{n:{$gt:1}}}])` — debe devolver vacío.
- El WARN "Could not load Pokémon cache… Unable to connect to Redis" al arrancar en Render es inofensivo: se reintenta al minuto.
- **Los push nuevos no se han probado en un móvil real** (FCM no funciona en el emulador estándar). Pruébalos en el próximo draft.
- Las anulaciones de resultados hechas **antes** del cambio siguen mostrando solo el "+X" en el historial de monedas.
- **Recuperar contraseña** necesita elegir antes un servicio de email (Resend, SendGrid, Brevo… tienen plan gratuito). El envío debe ir fuera de la transacción (tras el commit), igual que los push.
- La lista de features pendientes está en `docs/MEJORAS.md`. Las más baratas: push al registrar un resultado y al abrirse la ventana (reutilizan `PushNotificationPort`). Las grandes (playoffs, temporadas) conviene diseñarlas antes de tocar código.

---

## 3. Consejos para desarrollar en local (Windows)

**Backend**
1. Levanta Mongo (replica set) y Redis: `cd src && docker-compose up -d`. Necesitas Docker Desktop abierto.
2. Define `JWT_SECRET` (clave Base64 de 256 bits). En PowerShell, para generar una:
   `[Convert]::ToBase64String((1..32 | % { [byte](Get-Random -Max 256) }))`
   y luego `$env:JWT_SECRET = "<la clave>"`.
   (Esa clave es solo para local; la de producción está en Render y no se toca.)
3. Arranca desde IntelliJ (clase `Application` del módulo `boot`) o compilando y lanzando el jar:
   `./mvnw -B -ntp -f src/pom.xml clean package -DskipTests` y después `java -jar src/boot/target/boot-1.1.0.jar`.
   Por defecto usa `mongodb://root:password@localhost:27017` y Redis en `localhost:6379`, que es lo que levanta el docker-compose.
4. Comandos útiles (desde la raíz del repo; Maven no está en el PATH, usa siempre `./mvnw`):
   - Todo, como la CI: `./mvnw -B -ntp -f src/pom.xml clean verify`
   - Solo lógica (rápido, sin Docker): `./mvnw -B -ntp -f src/pom.xml test -pl application -am`
   - Un test: añade `-Dtest=NombreDelTest`
   - Sin Docker, los tests de integración se **saltan** (no fallan): antes de un PR grande, ejecútalos con Docker abierto.
5. Solo quieres la spec OpenAPI y no tienes Mongo/Redis: arranca con `SPRING_MAIN_LAZY_INITIALIZATION=true` y abre `http://localhost:8080/v3/api-docs`.
6. Swagger UI en `http://localhost:8080/swagger-ui.html`: la forma más cómoda de probar endpoints a mano (haz login primero; la cookie se guarda sola).

**Frontend**
1. `npm install` y `npm run dev`.
2. **Ojo**: si no defines `VITE_API_URL`, el frontend local habla con **producción**. Para usar tu backend local crea `pokefantasy-web/.env.local` con:
   `VITE_API_URL=http://localhost:8080`
   (`.env.local` no se commitea).
3. Antes de hacer push, lo mismo que la CI: `npm run lint`, `npm test`, `npm run api:check` y `npm run build`. El build de Netlify falla si importas un tipo sin `import type`.

**Flujo de trabajo**
- Nunca push directo a `develop`/`main`: rama → PR. Render despliega el backend al mergear en `develop` y Netlify el frontend al mergear en `main`.
- Si un cambio toca backend y frontend, haz que el frontend tolere el backend viejo (campos opcionales) y el backend no rompa el frontend viejo; así da igual el orden de los merges.
- Antes de empezar, mira si hay PRs abiertos en los dos repos.
- Si algo raro pasa en producción, busca el `requestId` del error (sale en la respuesta y en cada línea de log de Render).
