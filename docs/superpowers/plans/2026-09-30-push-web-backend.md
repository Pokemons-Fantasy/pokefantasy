# Notificaciones push en la web: backend

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que los avisos push que ya reciben los móviles Android (turno del draft, robo, propuesta de intercambio, cierre de ventana) lleguen también a los navegadores que los activen, con un enlace a la pantalla correcta, y que un navegador pueda darse de baja.

**Architecture:** la web usa FCM como la app: su token se guarda en el mismo `UserEntity.fcmTokens` y el mismo `FirebasePushNotificationAdapter` envía a todos. Cada aviso pasa a ser un `PushMessage` con ruta y etiqueta opcionales; el adaptador añade un `WebpushConfig` (enlace absoluto a la web, etiqueta e icono) que solo usan los navegadores. Un endpoint nuevo quita el token de un usuario, y registrar un token lo quita antes de cualquier otro usuario.

**Tech Stack:** Java 21, Spring Boot 4, Firebase Admin 9.10.0, MongoDB (`MongoTemplate`), JUnit 5 + Mockito + AssertJ.

**Spec:** diseño aprobado en la conversación del 2026-09-30 (resumen en "Diseño aprobado" abajo). Plan hermano del frontend: `pokefantasy-web/docs/superpowers/plans/2026-09-30-push-web-frontend.md`.

## Diseño aprobado (resumen)
- Enfoque: FCM también en la web (no Web Push propio con VAPID).
- Todos los avisos que ya tiene la app llegan a la web (opción A).
- Enlace por aviso: turno del draft → `/leagues/{id}/draft`; robo, intercambio y cierre de ventana → `/leagues/{id}/teams`. URL base de la web por variable de entorno.
- Etiqueta (`tag`) para que los avisos repetidos se sustituyan: turno del draft y cierre de ventana. Robos e intercambios sin etiqueta (cada uno es un aviso distinto y no deben taparse).
- Android no cambia (mismo título y texto).
- `DELETE /v1/users/push-token` con `{token}`: quita el token solo del usuario que lo pide.
- Un token es de un solo usuario: registrarlo lo quita antes de cualquier otro (mismo navegador, otra cuenta con la sesión caducada).

## Global Constraints
- Rama `feature/push-web` desde `develop`; PR contra `develop`. Commits con `git commit -F <fichero>` (UTF-8 sin BOM) y la línea `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Build y tests: `$env:JAVA_HOME='E:\jdk-23.0.1'; .\mvnw.cmd -B -ntp -f src/pom.xml clean verify` (JaCoCo exige 80 %). Los IT de `boot` se saltan sin Docker; la CI (Ubuntu) los ejecuta.
- CQRS: cada operación es `Command` + `CommandHandler` (`@Service`) + método en la `Facade`; el controller solo llama a la facade.
- Textos para el usuario y mensajes de error en español de España.
- Firebase Admin 9.10.0: `WebpushConfig.builder().setNotification(WebpushNotification).setFcmOptions(WebpushFcmOptions.withLink(String)).putData(String, String)`; `WebpushNotification.builder().setTitle/setBody/setIcon/setTag/setRenotify`. `withLink` exige HTTPS.
- Variable nueva: `WEB_URL` (por defecto `https://pokefantasy.netlify.app`), propiedad `pokefantasy.web-url`.

## Review Focus
1. **Token de otro usuario:** si `brock` registra un token que `ash` ya tenía (mismo navegador, `ash` no cerró sesión), `ash` deja de recibir los avisos de `brock`: `RegisterPushTokenCommandHandler` quita el token de todos antes de añadirlo (test en Task 3).
2. **Borrar el token de otro:** `DELETE /v1/users/push-token` con un token de otro usuario no debe quitárselo: la consulta filtra por `name` y por el token (test en Task 3).
3. **URL base sin HTTPS** (p. ej. `WEB_URL=http://localhost:5173` en local): `WebpushFcmOptions.withLink` lanzaría y no llegaría ningún aviso, tampoco a Android. El adaptador solo pone `fcmOptions` con `https://` y deja el enlace siempre en `data.link` (test en Task 1).
4. **URL base con barra final** (`https://pokefantasy.netlify.app/`): el enlace no debe quedar con `//leagues` (test en Task 1).
5. **Avisos sin ruta:** un `PushMessage` sin ruta no lleva `WebpushConfig` y se envía igual que hoy a Android (test en Task 1).

---

## File Structure
- Create `src/domain/src/main/java/com/villu/pokefantasy/dto/PushMessage.java`: el aviso (título, texto, ruta y etiqueta opcionales) y sus fábricas por tipo.
- Modify `src/domain/src/main/java/com/villu/pokefantasy/repository/PushNotificationPort.java`: `send(List<String>, PushMessage)`.
- Modify `src/infrastructure/src/main/java/com/villu/pokefantasy/push/FirebasePushNotificationAdapter.java`: `WebpushConfig` y URL base.
- Modify `src/boot/src/main/resources/application.yml`: `pokefantasy.web-url`.
- Modify los 4 emisores: `DraftTurnNotifier`, `WindowReminderService`, `StealPokemonCommandHandler`, `ProposeTradeCommandHandler`.
- Modify `UserRepository` / `UserRepositoryImpl`: `removeFcmToken(String username, String token)`.
- Modify `RegisterPushTokenCommandHandler`; Create `UnregisterPushTokenCommand` + handler; Modify `UserFacade`, `UserController`.
- Tests: los de cada fichero tocado.

---

### Task 1: `PushMessage` y el adaptador con configuración web

**Files:**
- Create: `src/domain/src/main/java/com/villu/pokefantasy/dto/PushMessage.java`
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/PushNotificationPort.java`
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/push/FirebasePushNotificationAdapter.java`
- Modify: `src/boot/src/main/resources/application.yml`
- Test: `src/infrastructure/src/test/java/com/villu/pokefantasy/push/FirebasePushNotificationAdapterTest.java`

**Interfaces:**
- Produces:
  - `record PushMessage(String title, String body, String path, String tag)` con fábricas `PushMessage.draftTurn(String leagueId, String title, String body)`, `PushMessage.teams(String leagueId, String title, String body)`, `PushMessage.window(String leagueId, String windowKey, String title, String body)`.
  - `PushNotificationPort.send(List<String> fcmTokens, PushMessage message)` (sustituye a `send(tokens, title, body)`).
  - `static MulticastMessage FirebasePushNotificationAdapter.buildMessage(List<String> tokens, PushMessage message, String webUrl)` (visible para el test).

- [ ] **Step 1: Crear `PushMessage`**

```java
package com.villu.pokefantasy.dto;

/**
 * Aviso push. {@code path} (p. ej. {@code /leagues/l1/draft}) es la pantalla que abre en la web al pulsarlo;
 * {@code tag} hace que un aviso nuevo con la misma etiqueta sustituya al anterior en el navegador.
 * Android usa solo título y texto.
 */
public record PushMessage(String title, String body, String path, String tag) {

    /** "Te toca en el draft": abre el draft y sustituye al aviso de turno anterior de esa liga. */
    public static PushMessage draftTurn(String leagueId, String title, String body) {
        return new PushMessage(title, body, "/leagues/" + leagueId + "/draft", "draft-turn-" + leagueId);
    }

    /** Robo o intercambio: abre Equipos. Sin etiqueta: cada uno es un aviso distinto. */
    public static PushMessage teams(String leagueId, String title, String body) {
        return new PushMessage(title, body, "/leagues/" + leagueId + "/teams", null);
    }

    /** Cierre de ventana ({@code windowKey}: steal o swap): abre Equipos y sustituye al aviso anterior de esa ventana. */
    public static PushMessage window(String leagueId, String windowKey, String title, String body) {
        return new PushMessage(title, body, "/leagues/" + leagueId + "/teams", "window-" + windowKey + "-" + leagueId);
    }
}
```

- [ ] **Step 2: Cambiar el puerto**

`PushNotificationPort.java` completo:

```java
package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.PushMessage;

import java.util.List;

public interface PushNotificationPort {
    /**
     * Envía el aviso a todos los tokens (app Android y navegadores). Si la lista está vacía o Firebase
     * no está inicializado, no hace nada. No lanza excepciones.
     */
    void send(List<String> fcmTokens, PushMessage message);
}
```

- [ ] **Step 3: Escribir los tests nuevos del adaptador (fallan: no compila)**

En `FirebasePushNotificationAdapterTest`: cambiar `setUp` a `adapter = new FirebasePushNotificationAdapter(userRepository, "https://pokefantasy.netlify.app");`, sustituir en todo el fichero `adapter.send(X, "title", "body")` por `adapter.send(X, new PushMessage("title", "body", null, null))` (import `com.villu.pokefantasy.dto.PushMessage`) y añadir:

```java
    @Test
    void buildMessage_withPath_addsWebLinkTagAndIcon() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                PushMessage.draftTurn("l1", "¡Te toca en el draft!", "Liga Kanto · ronda 1"),
                "https://pokefantasy.netlify.app");

        Object webpush = ReflectionTestUtils.getField(message, "webpushConfig");
        assertThat(webpush).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) ReflectionTestUtils.getField(webpush, "data");
        assertThat(data).containsEntry("link", "https://pokefantasy.netlify.app/leagues/l1/draft")
                .containsEntry("tag", "draft-turn-l1");
        Object fcmOptions = ReflectionTestUtils.getField(webpush, "fcmOptions");
        assertThat(ReflectionTestUtils.getField(fcmOptions, "link")).isEqualTo("https://pokefantasy.netlify.app/leagues/l1/draft");
        @SuppressWarnings("unchecked")
        Map<String, Object> notification = (Map<String, Object>) ReflectionTestUtils.getField(webpush, "notification");
        assertThat(notification).containsEntry("title", "¡Te toca en el draft!")
                .containsEntry("tag", "draft-turn-l1")
                .containsEntry("icon", "https://pokefantasy.netlify.app/icons/icon-192.png");
    }

    @Test
    void buildMessage_withoutPath_isAndroidOnlyAsBefore() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                new PushMessage("title", "body", null, null), "https://pokefantasy.netlify.app");

        assertThat(ReflectionTestUtils.getField(message, "webpushConfig")).isNull();
        assertThat(ReflectionTestUtils.getField(message, "notification")).isNotNull();
    }

    @Test
    void buildMessage_trailingSlashInWebUrl_doesNotDoubleIt() {
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                PushMessage.teams("l1", "Te han robado un Pokémon", "texto"), "https://pokefantasy.netlify.app/");

        Object webpush = ReflectionTestUtils.getField(message, "webpushConfig");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) ReflectionTestUtils.getField(webpush, "data");
        assertThat(data).containsEntry("link", "https://pokefantasy.netlify.app/leagues/l1/teams").doesNotContainKey("tag");
    }

    @Test
    void buildMessage_httpWebUrl_keepsLinkInDataButSkipsFcmOptions() {
        // WebpushFcmOptions.withLink exige HTTPS: con una URL local no debe romper el envío
        MulticastMessage message = FirebasePushNotificationAdapter.buildMessage(List.of("t1"),
                PushMessage.teams("l1", "t", "b"), "http://localhost:5173");

        Object webpush = ReflectionTestUtils.getField(message, "webpushConfig");
        assertThat(ReflectionTestUtils.getField(webpush, "fcmOptions")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) ReflectionTestUtils.getField(webpush, "data");
        assertThat(data).containsEntry("link", "http://localhost:5173/leagues/l1/teams");
    }
```

(imports nuevos: `java.util.Map`.)

- [ ] **Step 4: Ejecutar y ver que falla**

Run: `$env:JAVA_HOME='E:\jdk-23.0.1'; .\mvnw.cmd -B -ntp -f src/pom.xml -pl infrastructure -am test -Dtest=FirebasePushNotificationAdapterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FALLA de compilación (`buildMessage` y el constructor de dos argumentos no existen; los emisores aún llaman a `send` con tres argumentos).

- [ ] **Step 5: Implementar el adaptador**

En `FirebasePushNotificationAdapter`:

```java
import com.villu.pokefantasy.dto.PushMessage;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.google.firebase.messaging.WebpushNotification;
import org.springframework.beans.factory.annotation.Value;

    private final UserRepository userRepository;
    /** Origen de la web (sin barra final) para los enlaces y el icono de los avisos en el navegador. */
    private final String webUrl;
    private volatile boolean initialized = false;

    public FirebasePushNotificationAdapter(UserRepository userRepository,
                                           @Value("${pokefantasy.web-url:https://pokefantasy.netlify.app}") String webUrl) {
        this.userRepository = userRepository;
        this.webUrl = webUrl;
    }

    @Override
    public void send(List<String> fcmTokens, PushMessage message) {
        if (!initialized || fcmTokens == null || fcmTokens.isEmpty()) return;
        List<String> tokens = List.copyOf(fcmTokens);
        // Dentro de un comando transaccional se envía tras el commit: si la transacción
        // se aborta o se reintenta, no llega una notificación de algo que no ha ocurrido.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(tokens, message);
                }
            });
        } else {
            doSend(tokens, message);
        }
    }

    /**
     * Mensaje para la app y los navegadores. Con ruta, añade la parte web: enlace absoluto (en
     * {@code fcmOptions} si es HTTPS, que es lo que exige Firebase, y siempre en {@code data.link} para el
     * service worker), etiqueta e icono.
     */
    static MulticastMessage buildMessage(List<String> tokens, PushMessage message, String webUrl) {
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .addAllTokens(tokens);
        if (message.path() == null) {
            return builder.build();
        }
        String origin = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
        String link = origin + message.path();
        WebpushNotification.Builder notification = WebpushNotification.builder()
                .setTitle(message.title())
                .setBody(message.body())
                .setIcon(origin + "/icons/icon-192.png");
        WebpushConfig.Builder webpush = WebpushConfig.builder().putData("link", link);
        if (message.tag() != null) {
            // renotify: un aviso con la misma etiqueta sustituye al anterior y vuelve a sonar
            notification.setTag(message.tag()).setRenotify(true);
            webpush.putData("tag", message.tag());
        }
        if (link.startsWith("https://")) {
            webpush.setFcmOptions(WebpushFcmOptions.withLink(link));
        }
        return builder.setWebpushConfig(webpush.setNotification(notification.build()).build()).build();
    }

    private void doSend(List<String> fcmTokens, PushMessage pushMessage) {
        try {
            MulticastMessage message = buildMessage(fcmTokens, pushMessage, webUrl);
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("Push sent: {}/{} successful for title='{}'", response.getSuccessCount(), fcmTokens.size(), pushMessage.title());
            cleanupStaleTokens(fcmTokens, response);
        } catch (FirebaseMessagingException | RuntimeException e) {
            log.error("Failed to send push notification: {}", e.getMessage(), e);
        }
    }
```

(Se borra el `send(List, String, String)` y el `doSend(List, String, String)` antiguos.)

En `application.yml`, al final:

```yaml
pokefantasy:
  # Web (Netlify) a la que llevan los avisos push en el navegador. En local, WEB_URL=http://localhost:5173
  web-url: ${WEB_URL:https://pokefantasy.netlify.app}
```

- [ ] **Step 6: No compila hasta la Task 2** (los emisores). Seguir con la Task 2 antes de ejecutar; el commit de las dos va junto al final de la Task 2.

---

### Task 2: los emisores mandan `PushMessage` con su pantalla

**Files:**
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/draft/DraftTurnNotifier.java:44-45`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/schedule/WindowReminderService.java:89-93`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/steal/StealPokemonCommandHandler.java:101-104`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/trade/ProposeTradeCommandHandler.java:99-103`
- Test: `DraftTurnNotifierTest`, `WindowReminderServiceTest`, `StealPokemonCommandHandlerTest`, `ProposeTradeCommandHandlerTest` (mismos paquetes en `src/application/src/test/java/...`)

**Interfaces:**
- Consumes: `PushMessage.draftTurn/teams/window`, `PushNotificationPort.send(List<String>, PushMessage)` (Task 1).

- [ ] **Step 1: Actualizar los tests (fallan)**

`DraftTurnNotifierTest`: el `draft` de los tests debe tener `leagueId` (`draft.setLeagueId("l1")` en su creación si no lo tiene) y las verificaciones pasan a:

```java
        verify(pushNotificationPort).send(List.of("brock-phone"), PushMessage.draftTurn("l1", "¡Te toca en el draft!",
                "Liga Kanto · ronda 3: elige tu Pokémon. Tienes 2 min."));
```
```java
        verify(pushNotificationPort).send(List.of("brock-phone"), PushMessage.draftTurn("l1", "¡Te toca en el draft!",
                "Tu liga · ronda 3: elige tu Pokémon."));
```
```java
        verify(pushNotificationPort, never()).send(any(), any());
```

`WindowReminderServiceTest` (la liga del test tiene id; si no, `league.setId("l1")` en el `setUp`):

```java
        verify(pushNotificationPort).send(eq(List.of("ash-phone", "brock-phone", "brock-tablet")),
                argThat(m -> m.title().contains("robos") && m.body().contains("Liga Kanto: tienes hasta las 23:59")
                        && m.path().equals("/leagues/l1/teams") && m.tag().equals("window-steal-l1")));
```
```java
        verify(pushNotificationPort, never()).send(anyList(), any());
```
```java
        verify(pushNotificationPort).send(anyList(),
                argThat(m -> m.title().contains("intercambios") && m.body().contains("hasta las 16:00")
                        && m.tag().equals("window-swap-l1")));
```
```java
        verify(pushNotificationPort, never()).send(any(), any());
```

`StealPokemonCommandHandlerTest`:

```java
        verify(pushNotificationPort).send(
                eq(List.of("token-brock")),
                eq(PushMessage.teams(LEAGUE_ID, "Te han robado un Pokémon",
                        STEALER + " te ha robado a " + TARGET + " y recibes 300 monedas")));
```

`ProposeTradeCommandHandlerTest`:

```java
        verify(pushNotificationPort).send(
                eq(List.of("token-brock-android")),
                argThat(m -> m.title().equals("Propuesta de intercambio") && m.path().equals("/leagues/l1/teams")
                        && m.tag() == null));
```
```java
        verify(pushNotificationPort, never()).send(anyList(), any());
```

(En cada test: `import com.villu.pokefantasy.dto.PushMessage;` y `import static org.mockito.ArgumentMatchers.argThat;` donde haga falta. Buscar con `git grep -n "pushNotificationPort" -- src/application/src/test` cualquier otra verificación de `send` con tres argumentos y adaptarla igual.)

- [ ] **Step 2: Cambiar los emisores**

`DraftTurnNotifier` (línea 44):

```java
        pushNotificationPort.send(user.getFcmTokens(), PushMessage.draftTurn(draft.getLeagueId(), "¡Te toca en el draft!",
                leagueName + " · ronda " + draft.getCurrentRound() + ": elige tu Pokémon." + timer));
```

`WindowReminderService.sendDue` (dentro del `for`):

```java
            pushNotificationPort.send(tokens, PushMessage.window(league.getId(),
                    reminder.window() == Window.STEAL ? "steal" : "swap",
                    "⏰ Cierra la ventana de " + reminder.window().name,
                    league.getName() + ": tienes hasta las " + HOUR.format(reminder.deadline())
                            + " para " + reminder.window().action + "."));
```

`StealPokemonCommandHandler`:

```java
            pushNotificationPort.send(
                    victimUser.getFcmTokens(),
                    PushMessage.teams(leagueId, "Te han robado un Pokémon",
                            stealer + " te ha robado a " + targetName + " y recibes " + stealPrice + " monedas"));
```

`ProposeTradeCommandHandler`:

```java
            pushNotificationPort.send(
                    responderUser.getFcmTokens(),
                    PushMessage.teams(leagueId, "Propuesta de intercambio",
                            proposer + " quiere intercambiar " + trade.getProposerPokemonName()
                                    + " por tu " + trade.getResponderPokemonName()));
```

(En los cuatro: `import com.villu.pokefantasy.dto.PushMessage;`.)

- [ ] **Step 3: Ejecutar todo**

Run: `$env:JAVA_HOME='E:\jdk-23.0.1'; .\mvnw.cmd -B -ntp -f src/pom.xml clean verify`
Expected: BUILD SUCCESS. Si falla algún test de otra clase que verifique `send` con tres argumentos, adaptarlo como en el Step 1.

- [ ] **Step 4: Commit**

```
feat(push): los avisos llevan a su pantalla en la web

PushMessage (título, texto, ruta y etiqueta) sustituye a send(tokens, título,
texto). El adaptador añade la parte web (enlace absoluto, etiqueta e icono)
para los navegadores; Android no cambia. Turno del draft y cierre de ventana
se sustituyen al repetirse; robos e intercambios no. URL base: WEB_URL.
```

---

### Task 3: un token es de un solo usuario y se puede dar de baja

**Files:**
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/UserRepository.java`
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/UserRepositoryImpl.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/users/pushtoken/RegisterPushTokenCommandHandler.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/users/pushtoken/UnregisterPushTokenCommand.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/users/pushtoken/UnregisterPushTokenCommandHandler.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/users/UserFacade.java`
- Modify: `src/api-rest/src/main/java/com/villu/pokefantasy/UserController.java`
- Test: `UserRepositoryImplAvatarTest` (renombrar no hace falta: añadir ahí), `RegisterPushTokenCommandHandlerTest`, Create `UnregisterPushTokenCommandHandlerTest`, `UserFacadeTest`, `DraftScheduleUserControllersTest`

**Interfaces:**
- Produces: `UserRepository.removeFcmToken(String username, String token)`; `UserFacade.unregisterPushToken(String username, String token)`; `DELETE /v1/users/push-token` con cuerpo `{"token": "..."}` → 204.

- [ ] **Step 1: Tests (fallan)**

`UserRepositoryImplAvatarTest`, añadir:

```java
    @Test
    void removeFcmTokenOfUser_pullsOnlyFromThatUser() {
        repository.removeFcmToken("ash", "tok-1");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("name", "ash").append("fcmTokens", "tok-1"));
        assertThat(update.getValue().getUpdateObject().get("$pull", Document.class)).containsEntry("fcmTokens", "tok-1");
    }
```

`RegisterPushTokenCommandHandlerTest`, cambiar `handle_validToken_callsAddFcmToken` por:

```java
    @Test
    void handle_validToken_takesItFromAnyOtherUserFirst() throws Exception {
        handler.handle(new RegisterPushTokenCommand("ash", "fcm-token-123"));

        InOrder order = inOrder(userRepository);
        order.verify(userRepository).removeFcmToken("fcm-token-123");
        order.verify(userRepository).addFcmToken("ash", "fcm-token-123");
    }
```
(import `org.mockito.InOrder`, `static org.mockito.Mockito.inOrder`.)

Create `UnregisterPushTokenCommandHandlerTest`:

```java
package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UnregisterPushTokenCommandHandlerTest {

    @Mock private UserRepository userRepository;
    private UnregisterPushTokenCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UnregisterPushTokenCommandHandler(userRepository);
    }

    @Test
    void handle_removesTheTokenOnlyFromTheRequester() {
        handler.handle(new UnregisterPushTokenCommand("ash", "tok-1"));
        verify(userRepository).removeFcmToken("ash", "tok-1");
    }

    @Test
    void handle_blankToken_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> handler.handle(new UnregisterPushTokenCommand("ash", " ")));
        verifyNoInteractions(userRepository);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(UnregisterPushTokenCommand.class);
    }
}
```

`UserFacadeTest`: copiar el test existente de `registerPushToken` para `unregisterPushToken` verificando que el mediator recibe `new UnregisterPushTokenCommand("ash", "tok-1")`.

`DraftScheduleUserControllersTest`, junto a la línea de `push-token`:

```java
        mvc.perform(json(delete("/v1/users/push-token"), "{\"token\":\"fcm-1\"}")).andExpect(status().isNoContent());
        verify(userFacade).unregisterPushToken(ME, "fcm-1");
```
(import estático `delete` de `MockMvcRequestBuilders` si no está.)

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `$env:JAVA_HOME='E:\jdk-23.0.1'; .\mvnw.cmd -B -ntp -f src/pom.xml clean verify`
Expected: FALLA de compilación (`removeFcmToken(String, String)`, `UnregisterPushTokenCommand`, `unregisterPushToken` no existen).

- [ ] **Step 3: Implementar**

`UserRepository`: añadir

```java
    /** Quita el token solo a ese usuario (dar de baja un navegador). */
    void removeFcmToken(String username, String token);
```

`UserRepositoryImpl`:

```java
    @Override
    public void removeFcmToken(String username, String token) {
        Query query = new Query(Criteria.where("name").is(username).and("fcmTokens").is(token));
        Update update = new Update().pull("fcmTokens", token).inc("version", 1);
        mongoTemplate.updateFirst(query, update, UserEntity.class);
    }
```

`RegisterPushTokenCommandHandler.handle`:

```java
        if (command.token() == null || command.token().isBlank()) {
            throw new IllegalArgumentException("Falta el token de notificaciones.");
        }
        // Un token es de un dispositivo y de quien lo registra: si otra cuenta lo tenía (mismo navegador,
        // sesión caducada sin cerrar), deja de recibir los avisos de esta.
        userRepository.removeFcmToken(command.token());
        userRepository.addFcmToken(command.username(), command.token());
        return null;
```

`UnregisterPushTokenCommand.java`:

```java
package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.mediator.Command;

public record UnregisterPushTokenCommand(String username, String token) implements Command {}
```

`UnregisterPushTokenCommandHandler.java`:

```java
package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

/** Da de baja un dispositivo (un navegador que desactiva los avisos o cierra sesión). */
@Service
public class UnregisterPushTokenCommandHandler implements CommandHandler<UnregisterPushTokenCommand, Void> {

    private final UserRepository userRepository;

    public UnregisterPushTokenCommandHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(UnregisterPushTokenCommand command) {
        if (command.token() == null || command.token().isBlank()) {
            throw new IllegalArgumentException("Falta el token de notificaciones.");
        }
        userRepository.removeFcmToken(command.username(), command.token());
        return null;
    }

    @Override
    public Class<UnregisterPushTokenCommand> commandType() {
        return UnregisterPushTokenCommand.class;
    }
}
```

`UserFacade`, junto a `registerPushToken`:

```java
    public void unregisterPushToken(String username, String token) throws Exception {
        mediator.send(new UnregisterPushTokenCommand(username, token));
    }
```
(import `com.villu.pokefantasy.commands.users.pushtoken.UnregisterPushTokenCommand`.)

`UserController`, junto a `registerPushToken` (reutiliza `RegisterPushTokenRequest`, que solo lleva `token`):

```java
    @DeleteMapping("/users/push-token")
    public ResponseEntity<Void> unregisterPushToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody RegisterPushTokenRequest request) throws Exception {
        userFacade.unregisterPushToken(userDetails.getUsername(), request.getToken());
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 4: Ejecutar todo**

Run: `$env:JAVA_HOME='E:\jdk-23.0.1'; .\mvnw.cmd -B -ntp -f src/pom.xml clean verify`
Expected: BUILD SUCCESS y "All coverage checks have been met".

- [ ] **Step 5: Commit, push y PR**

Commit:
```
feat(push): un token es de un solo usuario y DELETE /v1/users/push-token

Registrar un token lo quita antes de cualquier otra cuenta (mismo navegador
con la sesión de otra caducada). El endpoint nuevo quita el token solo al
usuario que lo pide: lo usa la web al desactivar los avisos o cerrar sesión.
```
Push de `feature/push-web` y PR contra `develop` con `node C:\Users\mallu\AppData\Local\Temp\claude\C--PokeFantasy\1c30755d-3b1d-434b-8384-9f3bc6d7be5d\scratchpad/create-pr.mjs pokefantasy feature/push-web develop "<título>" <cuerpo.md>` (tras `. $PROFILE`). El cuerpo resume las tres tareas, avisa de la variable `WEB_URL` (opcional; por defecto la de Netlify) y de que sin el front nuevo nada cambia para la web.
