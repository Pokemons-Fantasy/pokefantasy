# Foto de perfil (backend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Los usuarios pueden subir una foto de perfil (ya recortada por el cliente), reemplazarla, quitarla y verla servida con caché inmutable; el detalle de liga y un nuevo `GET /v1/user/me` exponen `avatarVersion`.

**Architecture:** La imagen (JPEG 256x256 generado en el navegador) se guarda en una colección nueva `avatars` (un documento por usuario, `_id = username`), y `UserEntity.avatarVersion` guarda el epoch ms de la última subida. Subir es un upsert + actualizar la versión en la misma transacción del comando (la foto anterior queda sobrescrita). El front construye `/v1/users/{u}/avatar?v={version}`, que se sirve con `Cache-Control: private, max-age=31536000, immutable`.

**Tech Stack:** Spring Boot 4.0.2 (Spring MVC multipart, Spring Data MongoDB `MongoTemplate`), `javax.imageio` (JDK) para validar la imagen, JUnit 5 + Mockito + AssertJ, MockMvc standalone, Testcontainers (Mongo replica set + Redis).

**Spec:** Diseño aprobado en chat el 2026-09-28 (sin design doc por preferencia del usuario). Resumen: sección "Diseño aprobado" abajo. Plan del front: `pokefantasy-web/docs/superpowers/plans/2026-09-28-foto-de-perfil-frontend.md`.

### Diseño aprobado (resumen del brainstorming)

- Se guarda **solo el recorte** (el cliente recorta con canvas y sube un JPEG 256x256). Reencuadrar = volver a elegir la foto.
- Almacenamiento en **MongoDB** (Render free tier no tiene disco persistente; nada de servicios externos).
- **Opción A de caché**: `avatarVersion` en el usuario, expuesto en `LeagueMemberResponse` y en `GET /v1/user/me`; URL versionada con caché inmutable; `null` = sin foto (el front pinta la inicial sin pedir nada).
- Alcance: subir/encuadrar, **quitar foto**, avatar en toda la app. "Pokémon como avatar" queda para una fase 2.

## Global Constraints

- Reglas de `pokefantasy/CLAUDE.md`: Command `record` que **implementa `Command`** → `@Service` Handler → método en `UserFacade` → controller que solo usa la Facade.
- Un comando = una transacción Mongo con reintentos: el handler debe ser idempotente ante reintentos (sobrescribir, no acumular), sin efectos externos.
- Campos nuevos **opcionales** (`avatarVersion` puede ser `null`); el front desplegado sigue funcionando.
- Errores: `IllegalArgumentException` → 400 `BAD_REQUEST` (ya mapeado en `ApiExceptionHandler`). No añadir mapeos nuevos.
- Límites: fichero de avatar **≤ 300 KB** (regla de negocio, 400) y multipart **≤ 1 MB** (`spring.servlet.multipart`, 413); JPEG de **≤ 512x512** px. `contentType` servido: `image/jpeg`.
- Caché del `GET`: `Cache-Control` con `max-age=31536000`, `private`, `immutable`.
- Nada de versiones de artefactos `org.springframework.boot` en los POM. Sin dependencias nuevas (ImageIO es del JDK; la imagen Docker es `eclipse-temurin:21-jre-alpine`, que incluye `java.desktop`).
- Gate JaCoCo 80 % (instrucciones y ramas) en `application`, `infrastructure`, `api-rest`: `./mvnw -B -ntp -f src/pom.xml clean verify` en verde antes del PR, con Docker abierto para los IT.
- Commits en PowerShell con here-string `$msg = @'...'@`; sin rutas con `/` en el mensaje. Terminar con `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Ningún patrón nuevo sin ADR: esta feature añade **ADR-014** (Task 6).

## Review Focus

1. **PNG (o cualquier no-JPEG) enviado con `Content-Type: image/jpeg`**: se rechaza con 400; el tipo se decide por el contenido, no por la cabecera. Test: `AvatarImagePolicyTest.png_isRejected` y el IT `upload_png_isRejected`.
2. **JPEG justo en el límite (512x512) y uno por encima (513x512)**: el primero se acepta, el segundo da 400. Test: `AvatarImagePolicyTest.maxSide_isAccepted` / `aboveMaxSide_isRejected`.
3. **Fichero vacío (0 bytes) o bytes basura**: 400 con mensaje claro, nunca 500. Test: `AvatarImagePolicyTest.empty_isRejected`, `garbage_isRejected`.
4. **Fichero entre 300 KB y 1 MB → 400; por encima de 1 MB → 413** (lo corta el multipart antes del handler). Test: IT `upload_tooLarge_isRejected`.
5. **Pedir el avatar de alguien sin foto o inexistente → 404; sin sesión → 401**, y reemplazar deja un único documento en `avatars`. Test: IT `replace_keepsSingleDocument_andDelete_returns404` y `avatar_requiresSession`.

---

## File Structure

| Fichero | Acción | Responsabilidad |
|---|---|---|
| `src/domain/src/main/java/com/villu/pokefantasy/repository/entity/AvatarEntity.java` | Crear | Documento `avatars` |
| `src/domain/src/main/java/com/villu/pokefantasy/repository/AvatarRepository.java` | Crear | Puerto de persistencia del avatar |
| `src/domain/src/main/java/com/villu/pokefantasy/repository/entity/UserEntity.java` | Modificar | Campo `avatarVersion` |
| `src/domain/src/main/java/com/villu/pokefantasy/repository/UserRepository.java` | Modificar | `findByUsernames`, `setAvatarVersion` |
| `src/domain/src/main/java/com/villu/pokefantasy/response/LeagueMemberResponse.java` | Modificar | Campo `avatarVersion` |
| `src/domain/src/main/java/com/villu/pokefantasy/response/CurrentUserResponse.java` | Crear | Respuesta de `GET /v1/user/me` |
| `src/domain/src/main/java/com/villu/pokefantasy/response/AvatarImageResponse.java` | Crear | Bytes + content type para el controller |
| `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/AvatarRepositoryImpl.java` | Crear | Adaptador Mongo |
| `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/UserRepositoryImpl.java` | Modificar | Implementa los dos métodos nuevos |
| `src/application/src/main/java/com/villu/pokefantasy/commands/users/avatar/AvatarImagePolicy.java` | Crear | Validación de la imagen (tamaño, formato, dimensiones) |
| `src/application/src/main/java/com/villu/pokefantasy/commands/users/avatar/{Upload,Delete,Get}Avatar{Command,CommandHandler}.java` | Crear | Casos de uso del avatar |
| `src/application/src/main/java/com/villu/pokefantasy/commands/users/me/GetCurrentUser{Command,CommandHandler}.java` | Crear | Caso de uso "usuario actual" |
| `src/application/src/main/java/com/villu/pokefantasy/commands/users/UserFacade.java` | Modificar | 4 métodos nuevos |
| `src/application/src/main/java/com/villu/pokefantasy/commands/league/GetLeagueDetailCommandHandler.java` | Modificar | Rellena `avatarVersion` de cada miembro |
| `src/api-rest/src/main/java/com/villu/pokefantasy/AvatarVersionResponse.java` | Crear | Respuesta del `PUT` |
| `src/api-rest/src/main/java/com/villu/pokefantasy/UserController.java` | Modificar | 4 endpoints |
| `src/boot/src/main/resources/application.yml` | Modificar | Límites multipart |
| `src/boot/src/test/java/com/villu/pokefantasy/it/ApiClient.java` | Modificar | `putMultipart`, `getBytes` |
| `src/boot/src/test/java/com/villu/pokefantasy/it/AvatarIntegrationTest.java` | Crear | Flujo completo contra Mongo/Redis reales |
| Tests unitarios junto a cada clase | Crear/Modificar | Ver cada task |

---

### Task 0: Rama aislada

El checkout `C:\PokeFantasy\pokefantasy` está en `docs/claude-md-architecture` con borrados sin commitear en `docs/superpowers/`. **No tocarlo**: trabajar en un worktree.

- [ ] **Step 1: Revisar PRs abiertos** (regla del repo) con el snippet de `CLAUDE.md`. Si hay alguno, avisar al usuario antes de seguir.

- [ ] **Step 2: Crear el worktree desde `origin/develop`**

```powershell
cd C:\PokeFantasy\pokefantasy
git fetch origin
git worktree add ..\pokefantasy-avatar -b feature/foto-de-perfil origin/develop
cd ..\pokefantasy-avatar
New-Item -ItemType Directory -Force docs\superpowers\plans | Out-Null
Copy-Item ..\pokefantasy\docs\superpowers\plans\2026-09-28-foto-de-perfil-backend.md docs\superpowers\plans\
```

Todas las rutas siguientes son relativas a `C:\PokeFantasy\pokefantasy-avatar`.

- [ ] **Step 3: Comprobar que compila y los tests pasan antes de tocar nada**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit del plan**

```powershell
git add docs\superpowers\plans\2026-09-28-foto-de-perfil-backend.md
$msg = @'
docs: plan de foto de perfil (backend)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git commit -m $msg
```

---

### Task 1: Persistencia (entidad, repositorios y `avatarVersion`)

**Files:**
- Create: `src/domain/src/main/java/com/villu/pokefantasy/repository/entity/AvatarEntity.java`
- Create: `src/domain/src/main/java/com/villu/pokefantasy/repository/AvatarRepository.java`
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/entity/UserEntity.java` (añadir campo al final)
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/UserRepository.java`
- Create: `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/AvatarRepositoryImpl.java`
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/repository/UserRepositoryImpl.java`
- Test: `src/infrastructure/src/test/java/com/villu/pokefantasy/repository/AvatarRepositoryImplTest.java`
- Test: `src/infrastructure/src/test/java/com/villu/pokefantasy/repository/UserRepositoryImplAvatarTest.java`

**Interfaces:**
- Produces:
  - `AvatarEntity(String username, byte[] data, String contentType, Instant updatedAt)` (Lombok `@Data @NoArgsConstructor @AllArgsConstructor`, `@Id username`, colección `avatars`)
  - `AvatarRepository`: `void save(AvatarEntity avatar)`, `Optional<AvatarEntity> findByUsername(String username)`, `void deleteByUsername(String username)`
  - `UserEntity.getAvatarVersion(): Long` / `setAvatarVersion(Long)`
  - `UserRepository.findByUsernames(Collection<String> usernames): List<UserEntity>` (solo `name` y `avatarVersion` rellenos) y `UserRepository.setAvatarVersion(String username, Long avatarVersion): void`

- [ ] **Step 1: Crear `AvatarEntity`**

```java
package com.villu.pokefantasy.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Foto de perfil de un usuario, ya recortada por el cliente (JPEG pequeño). Un documento por usuario
 * ({@code _id} = username): subir una foto nueva sobrescribe la anterior. Va aparte de {@code users}
 * para no cargar la imagen cada vez que se lee un usuario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "avatars")
public class AvatarEntity {
    @Id
    private String username;
    private byte[] data;
    private String contentType;
    private Instant updatedAt;
}
```

No se añade a `INDEXED_ENTITIES` de `MongoIndexInitializer`: no tiene más índice que `_id`.

- [ ] **Step 2: Crear `AvatarRepository`**

```java
package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.AvatarEntity;

import java.util.Optional;

public interface AvatarRepository {

    /** Crea o sobrescribe la foto del usuario ({@code _id} = username). */
    void save(AvatarEntity avatar);
    Optional<AvatarEntity> findByUsername(String username);
    void deleteByUsername(String username);
}
```

- [ ] **Step 3: Añadir `avatarVersion` a `UserEntity`** (después de `fcmTokens`)

```java
    /**
     * Epoch en ms de la última foto de perfil subida; {@code null} = sin foto. El front lo usa para
     * construir la URL de la foto ({@code ?v=}), que se cachea como inmutable.
     */
    private Long avatarVersion;
```

- [ ] **Step 4: Añadir los métodos a `UserRepository`**

```java
package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;

import java.util.Collection;
import java.util.List;

public interface UserRepository {

    void saveUser(UserEntity userEntity);
    UserEntity findByUsername(String username);
    List<UserEntity> findByUsernamePrefix(String prefix);
    /** Usuarios con esos nombres; solo trae {@code name} y {@code avatarVersion}. */
    List<UserEntity> findByUsernames(Collection<String> usernames);
    void setAvatarVersion(String username, Long avatarVersion);
    void addFcmToken(String username, String token);
    void removeFcmToken(String token);
}
```

- [ ] **Step 5: Escribir los tests que fallan**

`src/infrastructure/src/test/java/com/villu/pokefantasy/repository/AvatarRepositoryImplTest.java`:

```java
package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.AvatarEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarRepositoryImplTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final AvatarRepositoryImpl repository = new AvatarRepositoryImpl(mongoTemplate);

    @Test
    void save_upsertsById() {
        AvatarEntity avatar = new AvatarEntity("ash", new byte[]{1}, "image/jpeg", Instant.EPOCH);

        repository.save(avatar);

        verify(mongoTemplate).save(avatar);
    }

    @Test
    void findByUsername_found() {
        AvatarEntity avatar = new AvatarEntity("ash", new byte[]{1}, "image/jpeg", Instant.EPOCH);
        when(mongoTemplate.findById("ash", AvatarEntity.class)).thenReturn(avatar);

        assertThat(repository.findByUsername("ash")).containsSame(avatar);
    }

    @Test
    void findByUsername_missing_isEmpty() {
        assertThat(repository.findByUsername("misty")).isEmpty();
    }

    @Test
    void deleteByUsername_removesById() {
        repository.deleteByUsername("ash");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).remove(query.capture(), eq(AvatarEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("_id", "ash"));
    }
}
```

`src/infrastructure/src/test/java/com/villu/pokefantasy/repository/UserRepositoryImplAvatarTest.java`:

```java
package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserRepositoryImplAvatarTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final UserRepositoryImpl repository = new UserRepositoryImpl(mongoTemplate);

    @Test
    void findByUsernames_queriesByNameAndProjectsAvatarVersion() {
        UserEntity ash = new UserEntity();
        when(mongoTemplate.find(any(Query.class), eq(UserEntity.class))).thenReturn(List.of(ash));

        List<UserEntity> result = repository.findByUsernames(List.of("ash", "brock"));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject())
                .isEqualTo(new Document("name", new Document("$in", List.of("ash", "brock"))));
        assertThat(query.getValue().getFieldsObject())
                .isEqualTo(new Document("name", 1).append("avatarVersion", 1));
        assertThat(result).containsExactly(ash);
    }

    @Test
    void findByUsernames_empty_skipsQuery() {
        assertThat(repository.findByUsernames(List.of())).isEmpty();
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void setAvatarVersion_setsFieldAndBumpsVersion() {
        repository.setAvatarVersion("ash", 42L);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(UserEntity.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("name", "ash"));
        assertThat(update.getValue().getUpdateObject()).isEqualTo(new Document()
                .append("$set", new Document("avatarVersion", 42L))
                .append("$inc", new Document("version", 1)));
    }
}
```

- [ ] **Step 6: Ejecutar y ver que fallan**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest='AvatarRepositoryImplTest,UserRepositoryImplAvatarTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL de compilación (`AvatarRepositoryImpl` no existe; `UserRepositoryImpl` no implementa `findByUsernames`).

- [ ] **Step 7: Crear `AvatarRepositoryImpl`**

```java
package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.AvatarEntity;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AvatarRepositoryImpl implements AvatarRepository {

    private final MongoTemplate mongoTemplate;

    public AvatarRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(AvatarEntity avatar) {
        mongoTemplate.save(avatar);
    }

    @Override
    public Optional<AvatarEntity> findByUsername(String username) {
        return Optional.ofNullable(mongoTemplate.findById(username, AvatarEntity.class));
    }

    @Override
    public void deleteByUsername(String username) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(username)), AvatarEntity.class);
    }
}
```

- [ ] **Step 8: Implementar los métodos en `UserRepositoryImpl`** (añadir `import java.util.Collection;` y colocar tras `findByUsernamePrefix`)

```java
    @Override
    public List<UserEntity> findByUsernames(Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return List.of();
        }
        Query query = new Query(Criteria.where("name").in(usernames));
        query.fields().include("name", "avatarVersion");
        return mongoTemplate.find(query, UserEntity.class);
    }

    @Override
    public void setAvatarVersion(String username, Long avatarVersion) {
        Query query = new Query(Criteria.where("name").is(username));
        // Incrementa version, como addFcmToken: un save() posterior con el usuario desactualizado no la pisa.
        Update update = new Update().set("avatarVersion", avatarVersion).inc("version", 1);
        mongoTemplate.updateFirst(query, update, UserEntity.class);
    }
```

- [ ] **Step 9: Ejecutar los tests**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest='AvatarRepositoryImplTest,UserRepositoryImplAvatarTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (7 tests). Si `getFieldsObject()` sale con otro orden de claves, `Document.equals` no depende del orden: si falla, revisar que la proyección sea `include("name", "avatarVersion")`.

- [ ] **Step 10: Commit**

```powershell
git add src\domain src\infrastructure
$msg = @'
feat: persistencia de la foto de perfil y avatarVersion del usuario

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git commit -m $msg
```

---

### Task 2: Validación de la imagen y comandos subir / quitar / obtener

**Files:**
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/users/avatar/AvatarImagePolicy.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/users/avatar/UploadAvatarCommand.java`, `UploadAvatarCommandHandler.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/users/avatar/DeleteAvatarCommand.java`, `DeleteAvatarCommandHandler.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/users/avatar/GetAvatarCommand.java`, `GetAvatarCommandHandler.java`
- Create: `src/domain/src/main/java/com/villu/pokefantasy/response/AvatarImageResponse.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/users/avatar/AvatarImagePolicyTest.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/users/avatar/AvatarCommandHandlersTest.java`
- Test helper: `src/application/src/test/java/com/villu/pokefantasy/commands/users/avatar/TestImages.java`

**Interfaces:**
- Consumes: `AvatarRepository`, `AvatarEntity`, `UserRepository.setAvatarVersion` (Task 1).
- Produces:
  - `AvatarImagePolicy.validate(byte[] image): void` (lanza `IllegalArgumentException`), constantes `MAX_BYTES = 300 * 1024`, `MAX_SIDE = 512`, `CONTENT_TYPE = "image/jpeg"`
  - `record UploadAvatarCommand(String username, byte[] image) implements Command` → handler devuelve `Long` (la versión nueva)
  - `record DeleteAvatarCommand(String username) implements Command` → `Void`
  - `record GetAvatarCommand(String username) implements Command` → `Optional<AvatarImageResponse>`
  - `record AvatarImageResponse(byte[] data, String contentType)` (paquete `com.villu.pokefantasy.response`)

- [ ] **Step 1: Helper de imágenes para tests**

```java
package com.villu.pokefantasy.commands.users.avatar;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Imágenes reales generadas en memoria para los tests del avatar. */
public final class TestImages {

    private TestImages() {}

    public static byte[] jpeg(int width, int height) {
        return encode(width, height, "jpg");
    }

    public static byte[] png(int width, int height) {
        return encode(width, height, "png");
    }

    private static byte[] encode(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 2: Test de la política (falla)**

```java
package com.villu.pokefantasy.commands.users.avatar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarImagePolicyTest {

    @Test
    void jpeg256_isAccepted() {
        assertThatCode(() -> AvatarImagePolicy.validate(TestImages.jpeg(256, 256))).doesNotThrowAnyException();
    }

    @Test
    void maxSide_isAccepted() {
        assertThatCode(() -> AvatarImagePolicy.validate(TestImages.jpeg(512, 512))).doesNotThrowAnyException();
    }

    @Test
    void aboveMaxSide_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(TestImages.jpeg(513, 512)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("512");
        assertThatThrownBy(() -> AvatarImagePolicy.validate(TestImages.jpeg(512, 513)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("512");
    }

    @Test
    void png_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(TestImages.png(256, 256)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPEG");
    }

    @Test
    void empty_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vacía");
        assertThatThrownBy(() -> AvatarImagePolicy.validate(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vacía");
    }

    @Test
    void garbage_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(new byte[]{1, 2, 3, 4, 5}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no es una imagen");
    }

    @Test
    void truncatedJpeg_isRejected() {
        byte[] jpeg = TestImages.jpeg(256, 256);
        byte[] headerOnly = java.util.Arrays.copyOf(jpeg, 4); // SOI + inicio de segmento, sin SOF
        assertThatThrownBy(() -> AvatarImagePolicy.validate(headerOnly))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no se puede leer");
    }

    @Test
    void tooManyBytes_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(new byte[AvatarImagePolicy.MAX_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("300 KB");
    }
}
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=AvatarImagePolicyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL de compilación (`AvatarImagePolicy` no existe).

- [ ] **Step 4: Implementar `AvatarImagePolicy`**

```java
package com.villu.pokefantasy.commands.users.avatar;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Qué foto de perfil se acepta. El cliente ya la recorta y la reduce (JPEG de 256x256); aquí solo se
 * comprueba que es un JPEG de verdad (por su contenido, no por la cabecera HTTP) y que no es mayor de
 * lo esperado. Solo se leen las cabeceras de la imagen, no se decodifica entera.
 */
public final class AvatarImagePolicy {

    public static final int MAX_BYTES = 300 * 1024;
    public static final int MAX_SIDE = 512;
    public static final String CONTENT_TYPE = "image/jpeg";

    private AvatarImagePolicy() {}

    public static void validate(byte[] image) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("La imagen está vacía");
        }
        if (image.length > MAX_BYTES) {
            throw new IllegalArgumentException("La imagen no puede superar " + MAX_BYTES / 1024 + " KB");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("El fichero no es una imagen");
            }
            ImageReader reader = readers.next();
            try {
                if (!"jpeg".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IllegalArgumentException("La imagen debe ser JPEG");
                }
                reader.setInput(in);
                if (reader.getWidth(0) > MAX_SIDE || reader.getHeight(0) > MAX_SIDE) {
                    throw new IllegalArgumentException(
                            "La imagen no puede medir más de " + MAX_SIDE + "x" + MAX_SIDE + " px");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("La imagen no se puede leer", e);
        }
    }
}
```

- [ ] **Step 5: Ejecutar el test de la política**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=AvatarImagePolicyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 tests). Si `truncatedJpeg_isRejected` falla porque `getImageReaders` no reconoce 4 bytes como JPEG (mensaje "no es una imagen"), usar `Arrays.copyOf(jpeg, 20)` para que la firma se reconozca pero falte el SOF; el objetivo del test es cubrir la rama `IOException`.

- [ ] **Step 6: Crear los commands y el DTO**

```java
package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.Command;

public record UploadAvatarCommand(String username, byte[] image) implements Command {}
```

```java
package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.Command;

public record DeleteAvatarCommand(String username) implements Command {}
```

```java
package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.Command;

public record GetAvatarCommand(String username) implements Command {}
```

```java
package com.villu.pokefantasy.response;

/** Foto de perfil lista para servir. */
public record AvatarImageResponse(byte[] data, String contentType) {}
```

- [ ] **Step 7: Test de los handlers (falla)**

```java
package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.AvatarEntity;
import com.villu.pokefantasy.response.AvatarImageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarCommandHandlersTest {

    @Mock private AvatarRepository avatarRepository;
    @Mock private UserRepository userRepository;

    @Test
    void upload_savesAvatarAndSetsVersionToItsTimestamp() {
        byte[] image = TestImages.jpeg(256, 256);

        Long version = new UploadAvatarCommandHandler(avatarRepository, userRepository)
                .handle(new UploadAvatarCommand("ash", image));

        ArgumentCaptor<AvatarEntity> saved = ArgumentCaptor.forClass(AvatarEntity.class);
        verify(avatarRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("ash");
        assertThat(saved.getValue().getData()).isSameAs(image);
        assertThat(saved.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(version).isEqualTo(saved.getValue().getUpdatedAt().toEpochMilli());
        verify(userRepository).setAvatarVersion("ash", version);
    }

    @Test
    void upload_invalidImage_savesNothing() {
        UploadAvatarCommandHandler handler = new UploadAvatarCommandHandler(avatarRepository, userRepository);

        assertThatThrownBy(() -> handler.handle(new UploadAvatarCommand("ash", TestImages.png(256, 256))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(avatarRepository, never()).save(any());
        verify(userRepository, never()).setAvatarVersion(anyString(), anyLong());
    }

    @Test
    void delete_removesAvatarAndClearsVersion() {
        new DeleteAvatarCommandHandler(avatarRepository, userRepository).handle(new DeleteAvatarCommand("ash"));

        verify(avatarRepository).deleteByUsername("ash");
        verify(userRepository).setAvatarVersion("ash", null);
    }

    @Test
    void get_found_returnsBytesAndContentType() {
        byte[] data = {1, 2, 3};
        when(avatarRepository.findByUsername("ash"))
                .thenReturn(Optional.of(new AvatarEntity("ash", data, "image/jpeg", Instant.EPOCH)));

        Optional<AvatarImageResponse> result = new GetAvatarCommandHandler(avatarRepository)
                .handle(new GetAvatarCommand("ash"));

        assertThat(result).get().satisfies(r -> {
            assertThat(r.data()).isSameAs(data);
            assertThat(r.contentType()).isEqualTo("image/jpeg");
        });
    }

    @Test
    void get_missing_isEmpty() {
        when(avatarRepository.findByUsername("misty")).thenReturn(Optional.empty());

        assertThat(new GetAvatarCommandHandler(avatarRepository).handle(new GetAvatarCommand("misty"))).isEmpty();
    }

    @Test
    void commandTypes() {
        assertThat(new UploadAvatarCommandHandler(avatarRepository, userRepository).commandType())
                .isEqualTo(UploadAvatarCommand.class);
        assertThat(new DeleteAvatarCommandHandler(avatarRepository, userRepository).commandType())
                .isEqualTo(DeleteAvatarCommand.class);
        assertThat(new GetAvatarCommandHandler(avatarRepository).commandType())
                .isEqualTo(GetAvatarCommand.class);
    }
}
```

- [ ] **Step 8: Ejecutar y ver que falla**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=AvatarCommandHandlersTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL de compilación (handlers no existen).

- [ ] **Step 9: Implementar los handlers**

```java
package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.AvatarEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Guarda la foto de perfil (sobrescribe la anterior) y devuelve su versión, que es el instante de la
 * subida en ms. Repetirlo tras un reintento de la transacción solo cambia la versión.
 */
@Service
public class UploadAvatarCommandHandler implements CommandHandler<UploadAvatarCommand, Long> {

    private final AvatarRepository avatarRepository;
    private final UserRepository userRepository;

    public UploadAvatarCommandHandler(AvatarRepository avatarRepository, UserRepository userRepository) {
        this.avatarRepository = avatarRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Long handle(UploadAvatarCommand command) {
        AvatarImagePolicy.validate(command.image());
        Instant now = Instant.now();
        avatarRepository.save(new AvatarEntity(
                command.username(), command.image(), AvatarImagePolicy.CONTENT_TYPE, now));
        long version = now.toEpochMilli();
        userRepository.setAvatarVersion(command.username(), version);
        return version;
    }

    @Override
    public Class<UploadAvatarCommand> commandType() {
        return UploadAvatarCommand.class;
    }
}
```

```java
package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

/** Quita la foto de perfil: el usuario vuelve a verse con su inicial. */
@Service
public class DeleteAvatarCommandHandler implements CommandHandler<DeleteAvatarCommand, Void> {

    private final AvatarRepository avatarRepository;
    private final UserRepository userRepository;

    public DeleteAvatarCommandHandler(AvatarRepository avatarRepository, UserRepository userRepository) {
        this.avatarRepository = avatarRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(DeleteAvatarCommand command) {
        avatarRepository.deleteByUsername(command.username());
        userRepository.setAvatarVersion(command.username(), null);
        return null;
    }

    @Override
    public Class<DeleteAvatarCommand> commandType() {
        return DeleteAvatarCommand.class;
    }
}
```

```java
package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.response.AvatarImageResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Foto de perfil de cualquier usuario (la ven los demás jugadores); vacío si no tiene. */
@Service
public class GetAvatarCommandHandler implements CommandHandler<GetAvatarCommand, Optional<AvatarImageResponse>> {

    private final AvatarRepository avatarRepository;

    public GetAvatarCommandHandler(AvatarRepository avatarRepository) {
        this.avatarRepository = avatarRepository;
    }

    @Override
    public Optional<AvatarImageResponse> handle(GetAvatarCommand command) {
        return avatarRepository.findByUsername(command.username())
                .map(a -> new AvatarImageResponse(a.getData(), a.getContentType()));
    }

    @Override
    public Class<GetAvatarCommand> commandType() {
        return GetAvatarCommand.class;
    }
}
```

- [ ] **Step 10: Ejecutar los tests del paquete**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest='AvatarImagePolicyTest,AvatarCommandHandlersTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (14 tests).

- [ ] **Step 11: Commit**

```powershell
git add src\application src\domain
$msg = @'
feat: comandos para subir, quitar y obtener la foto de perfil

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git commit -m $msg
```

---

### Task 3: `avatarVersion` en el detalle de liga y usuario actual

**Files:**
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/response/LeagueMemberResponse.java`
- Create: `src/domain/src/main/java/com/villu/pokefantasy/response/CurrentUserResponse.java`
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/league/GetLeagueDetailCommandHandler.java`
- Create: `src/application/src/main/java/com/villu/pokefantasy/commands/users/me/GetCurrentUserCommand.java`, `GetCurrentUserCommandHandler.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/league/GetLeagueDetailCommandHandlerTest.java` (modificar)
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/users/me/GetCurrentUserCommandHandlerTest.java`

**Interfaces:**
- Consumes: `UserRepository.findByUsernames`, `UserRepository.findByUsername`, `UserEntity.getAvatarVersion()` (Task 1).
- Produces:
  - `LeagueMemberResponse.avatarVersion: Long` (builder `.avatarVersion(Long)`)
  - `CurrentUserResponse { String username; Long avatarVersion; }` (`@Data @Builder`)
  - `record GetCurrentUserCommand(String username) implements Command` → `CurrentUserResponse`
  - `GetLeagueDetailCommandHandler(LeagueRepository, LeagueMembershipGuard, UserRepository)` (constructor nuevo)

- [ ] **Step 1: Añadir el campo y el DTO**

`LeagueMemberResponse` (tras `leagueRole`):

```java
    /** Versión de su foto de perfil; {@code null} = sin foto. */
    private Long avatarVersion;
```

`CurrentUserResponse`:

```java
package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurrentUserResponse {
    private String username;
    /** Versión de su foto de perfil; {@code null} = sin foto. */
    private Long avatarVersion;
}
```

- [ ] **Step 2: Actualizar `GetLeagueDetailCommandHandlerTest`** (falla)

Añadir `@Mock private UserRepository userRepository;`, construir el handler con `new GetLeagueDetailCommandHandler(leagueRepository, leagueMembershipGuard, userRepository)` en `setUp`, añadir imports `com.villu.pokefantasy.repository.UserRepository` y `com.villu.pokefantasy.repository.entity.UserEntity`, y este test:

```java
    @Test
    void handle_includesEachMembersAvatarVersion() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0)));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        UserEntity ash = new UserEntity();
        ash.setName("ash");
        ash.setAvatarVersion(1700000000000L);
        UserEntity brock = new UserEntity();
        brock.setName("brock");
        when(userRepository.findByUsernames(List.of("ash", "brock"))).thenReturn(List.of(ash, brock));

        LeagueDetailResponse result = handler.handle(new GetLeagueDetailCommand("l1", "ash"));

        assertThat(result.getMembers()).extracting(LeagueMemberResponse::getAvatarVersion)
                .containsExactly(1700000000000L, null);
    }
```

(Añadir `import com.villu.pokefantasy.response.LeagueMemberResponse;`.) `handle_validLeague_mapsCorrectly` sigue pasando sin cambios: el mock devuelve lista vacía por defecto.

- [ ] **Step 3: Test de `GetCurrentUserCommandHandler`** (falla)

```java
package com.villu.pokefantasy.commands.users.me;

import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import com.villu.pokefantasy.response.CurrentUserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserCommandHandlerTest {

    @Mock private UserRepository userRepository;

    @Test
    void handle_returnsUsernameAndAvatarVersion() {
        UserEntity user = new UserEntity();
        user.setName("ash");
        user.setAvatarVersion(42L);
        when(userRepository.findByUsername("ash")).thenReturn(user);

        CurrentUserResponse result = new GetCurrentUserCommandHandler(userRepository)
                .handle(new GetCurrentUserCommand("ash"));

        assertThat(result.getUsername()).isEqualTo("ash");
        assertThat(result.getAvatarVersion()).isEqualTo(42L);
    }

    @Test
    void handle_unknownUser_throwsIllegalArgument() {
        assertThatThrownBy(() -> new GetCurrentUserCommandHandler(userRepository)
                .handle(new GetCurrentUserCommand("ghost")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commandType() {
        assertThat(new GetCurrentUserCommandHandler(userRepository).commandType())
                .isEqualTo(GetCurrentUserCommand.class);
    }
}
```

- [ ] **Step 4: Ejecutar y ver que fallan**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest='GetLeagueDetailCommandHandlerTest,GetCurrentUserCommandHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL de compilación.

- [ ] **Step 5: Implementar**

`GetCurrentUserCommand`:

```java
package com.villu.pokefantasy.commands.users.me;

import com.villu.pokefantasy.mediator.Command;

public record GetCurrentUserCommand(String username) implements Command {}
```

`GetCurrentUserCommandHandler`:

```java
package com.villu.pokefantasy.commands.users.me;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import com.villu.pokefantasy.response.CurrentUserResponse;
import org.springframework.stereotype.Service;

/** Datos del usuario en sesión que el front no guarda (por ahora, la versión de su foto). */
@Service
public class GetCurrentUserCommandHandler implements CommandHandler<GetCurrentUserCommand, CurrentUserResponse> {

    private final UserRepository userRepository;

    public GetCurrentUserCommandHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public CurrentUserResponse handle(GetCurrentUserCommand command) {
        UserEntity user = userRepository.findByUsername(command.username());
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + command.username());
        }
        return CurrentUserResponse.builder()
                .username(user.getName())
                .avatarVersion(user.getAvatarVersion())
                .build();
    }

    @Override
    public Class<GetCurrentUserCommand> commandType() {
        return GetCurrentUserCommand.class;
    }
}
```

`GetLeagueDetailCommandHandler` completo:

```java
package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.response.LeagueDetailResponse;
import com.villu.pokefantasy.response.LeagueMemberResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class GetLeagueDetailCommandHandler implements CommandHandler<GetLeagueDetailCommand, LeagueDetailResponse> {

    private final LeagueRepository leagueRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;
    private final UserRepository userRepository;

    public GetLeagueDetailCommandHandler(LeagueRepository leagueRepository,
                                         LeagueMembershipGuard leagueMembershipGuard,
                                         UserRepository userRepository) {
        this.leagueRepository = leagueRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
        this.userRepository = userRepository;
    }

    @Override
    public LeagueDetailResponse handle(GetLeagueDetailCommand command) {
        leagueMembershipGuard.requireMember(command.leagueId(), command.requestingUsername());

        var league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + command.leagueId()));

        // HashMap y no Collectors.toMap: la versión es null para quien no tiene foto.
        Map<String, Long> avatarVersions = new HashMap<>();
        userRepository.findByUsernames(league.getMembers().stream().map(LeagueMember::getUsername).toList())
                .forEach(user -> avatarVersions.put(user.getName(), user.getAvatarVersion()));

        return LeagueDetailResponse.builder()
                .id(league.getId())
                .name(league.getName())
                .createdBy(league.getCreatedBy())
                .status(league.getStatus())
                .members(league.getMembers().stream()
                        .map(m -> LeagueMemberResponse.builder()
                                .username(m.getUsername())
                                .leagueRole(m.getLeagueRole())
                                .avatarVersion(avatarVersions.get(m.getUsername()))
                                .build())
                        .toList())
                .build();
    }

    @Override
    public Class<GetLeagueDetailCommand> commandType() {
        return GetLeagueDetailCommand.class;
    }
}
```

- [ ] **Step 6: Buscar otros `new GetLeagueDetailCommandHandler(`** en tests (`git grep -n "new GetLeagueDetailCommandHandler(" -- src`) y añadir el tercer argumento (`mock(UserRepository.class)` o el `@Mock` del test).

- [ ] **Step 7: Ejecutar todo `application`**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am`
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```powershell
git add src\application src\domain
$msg = @'
feat: avatarVersion en el detalle de liga y comando de usuario actual

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git commit -m $msg
```

---

### Task 4: Facade, endpoints y límites multipart

**Files:**
- Modify: `src/application/src/main/java/com/villu/pokefantasy/commands/users/UserFacade.java`
- Test: `src/application/src/test/java/com/villu/pokefantasy/commands/users/UserFacadeTest.java`
- Create: `src/api-rest/src/main/java/com/villu/pokefantasy/AvatarVersionResponse.java`
- Modify: `src/api-rest/src/main/java/com/villu/pokefantasy/UserController.java`
- Test: `src/api-rest/src/test/java/com/villu/pokefantasy/DraftScheduleUserControllersTest.java`
- Modify: `src/boot/src/main/resources/application.yml`

**Interfaces:**
- Consumes: commands de Tasks 2 y 3, `AvatarImageResponse`, `CurrentUserResponse`.
- Produces (contrato HTTP que usa el front):
  - `PUT /v1/user/avatar` multipart, parte `file` → `200 { "avatarVersion": number }`; 400 si la imagen no vale; 413 si supera 1 MB
  - `DELETE /v1/user/avatar` → `204`
  - `GET /v1/users/{username}/avatar` → `200 image/jpeg` + `Cache-Control: max-age=31536000, private, immutable`, o `404`
  - `GET /v1/user/me` → `200 { "username": string, "avatarVersion": number | null }`
  - `UserFacade`: `long uploadAvatar(String username, byte[] image)`, `void deleteAvatar(String username)`, `Optional<AvatarImageResponse> getAvatar(String username)`, `CurrentUserResponse me(String username)`

- [ ] **Step 1: Tests de la facade (fallan)** — añadir a `UserFacadeTest` (imports de los 4 commands, `AvatarImageResponse`, `CurrentUserResponse`, `java.util.Optional`):

```java
    @Test
    void uploadAvatar_sendsCommandAndReturnsVersion() throws Exception {
        byte[] image = {1};
        when(mediator.send(any(UploadAvatarCommand.class))).thenReturn(42L);

        assertThat(facade.uploadAvatar("ash", image)).isEqualTo(42L);

        ArgumentCaptor<UploadAvatarCommand> captor = ArgumentCaptor.forClass(UploadAvatarCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
        assertThat(captor.getValue().image()).isSameAs(image);
    }

    @Test
    void deleteAvatar_sendsCommand() throws Exception {
        facade.deleteAvatar("ash");

        verify(mediator).send(new DeleteAvatarCommand("ash"));
    }

    @Test
    void getAvatar_sendsCommandAndReturnsImage() throws Exception {
        Optional<AvatarImageResponse> image = Optional.of(new AvatarImageResponse(new byte[]{1}, "image/jpeg"));
        when(mediator.send(new GetAvatarCommand("misty"))).thenReturn(image);

        assertThat(facade.getAvatar("misty")).isSameAs(image);
    }

    @Test
    void me_sendsCommand() throws Exception {
        CurrentUserResponse me = CurrentUserResponse.builder().username("ash").build();
        when(mediator.send(new GetCurrentUserCommand("ash"))).thenReturn(me);

        assertThat(facade.me("ash")).isSameAs(me);
    }
```

- [ ] **Step 2: Implementar en `UserFacade`** (imports: los 4 commands, `AvatarImageResponse`, `CurrentUserResponse`, `java.util.Optional`)

```java
    /** Guarda la foto de perfil (ya recortada por el cliente) y devuelve su versión. */
    public long uploadAvatar(String username, byte[] image) throws Exception {
        return mediator.send(new UploadAvatarCommand(username, image));
    }

    public void deleteAvatar(String username) throws Exception {
        mediator.send(new DeleteAvatarCommand(username));
    }

    public Optional<AvatarImageResponse> getAvatar(String username) throws Exception {
        return mediator.send(new GetAvatarCommand(username));
    }

    public CurrentUserResponse me(String username) throws Exception {
        return mediator.send(new GetCurrentUserCommand(username));
    }
```

Run: `./mvnw -B -ntp -f src/pom.xml test -pl application -am -Dtest=UserFacadeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 3: Tests del controller (fallan)** — añadir a `DraftScheduleUserControllersTest` (imports: `org.springframework.http.HttpMethod`, `org.springframework.mock.web.MockMultipartFile`, `static ...MockMvcRequestBuilders.multipart`, `com.villu.pokefantasy.response.AvatarImageResponse`, `com.villu.pokefantasy.response.CurrentUserResponse`, `java.util.Optional`):

```java
    // ── Avatar ───────────────────────────────────────────────────────────────

    @Test
    void uploadAvatar_passesBytesAndReturnsVersion() throws Exception {
        byte[] image = {1, 2, 3};
        when(userFacade.uploadAvatar(ME, image)).thenReturn(42L);

        mvc.perform(multipart(HttpMethod.PUT, "/v1/user/avatar")
                        .file(new MockMultipartFile("file", "avatar.jpg", "image/jpeg", image)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarVersion").value(42));
    }

    @Test
    void uploadAvatar_invalidImage_400() throws Exception {
        when(userFacade.uploadAvatar(org.mockito.ArgumentMatchers.eq(ME), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("La imagen debe ser JPEG"));

        mvc.perform(multipart(HttpMethod.PUT, "/v1/user/avatar")
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void deleteAvatar_204() throws Exception {
        mvc.perform(delete("/v1/user/avatar")).andExpect(status().isNoContent());
        verify(userFacade).deleteAvatar(ME);
    }

    @Test
    void getAvatar_servesJpegWithImmutableCache() throws Exception {
        byte[] data = {1, 2, 3};
        when(userFacade.getAvatar("misty")).thenReturn(Optional.of(new AvatarImageResponse(data, "image/jpeg")));

        MvcResult result = mvc.perform(get("/v1/users/misty/avatar").param("v", "42"))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(data);
        assertThat(result.getResponse().getHeader("Cache-Control"))
                .contains("max-age=31536000").contains("private").contains("immutable");
    }

    @Test
    void getAvatar_missing_404() throws Exception {
        when(userFacade.getAvatar("misty")).thenReturn(Optional.empty());

        mvc.perform(get("/v1/users/misty/avatar")).andExpect(status().isNotFound());
    }

    @Test
    void me_returnsCurrentUser() throws Exception {
        when(userFacade.me(ME)).thenReturn(CurrentUserResponse.builder().username(ME).avatarVersion(42L).build());

        mvc.perform(get("/v1/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ME))
                .andExpect(jsonPath("$.avatarVersion").value(42));
    }
```

Run: `./mvnw -B -ntp -f src/pom.xml test -pl api-rest -am -Dtest=DraftScheduleUserControllersTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (404 en las rutas nuevas / compilación).

- [ ] **Step 4: Crear `AvatarVersionResponse`**

```java
package com.villu.pokefantasy;

public record AvatarVersionResponse(long avatarVersion) {}
```

- [ ] **Step 5: Añadir los endpoints a `UserController`** (imports: `com.villu.pokefantasy.response.AvatarImageResponse`, `com.villu.pokefantasy.response.CurrentUserResponse`, `org.springframework.http.CacheControl`, `org.springframework.http.MediaType`, `org.springframework.web.bind.annotation.DeleteMapping`, `org.springframework.web.bind.annotation.PathVariable`, `org.springframework.web.bind.annotation.RequestPart`, `org.springframework.web.multipart.MultipartFile`, `java.time.Duration`)

```java
    @GetMapping("/user/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(userFacade.me(userDetails.getUsername()));
    }

    /** Sube la foto de perfil ya recortada por el cliente (JPEG pequeño). Sustituye a la anterior. */
    @PutMapping(value = "/user/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AvatarVersionResponse> uploadAvatar(@AuthenticationPrincipal UserDetails userDetails,
                                                              @RequestPart("file") MultipartFile file) throws Exception {
        long version = userFacade.uploadAvatar(userDetails.getUsername(), file.getBytes());
        return ResponseEntity.ok(new AvatarVersionResponse(version));
    }

    @DeleteMapping("/user/avatar")
    public ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal UserDetails userDetails) throws Exception {
        userFacade.deleteAvatar(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Foto de perfil. El front la pide con {@code ?v=<avatarVersion>}: cada versión es una URL distinta,
     * así que se cachea un año como inmutable.
     */
    @GetMapping("/users/{username}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String username) throws Exception {
        return userFacade.getAvatar(username)
                .map(UserController::avatarResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> avatarResponse(AvatarImageResponse avatar) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .body(avatar.data());
    }
```

- [ ] **Step 6: Límites multipart en `application.yml`** (bajo `spring:`, junto a `task:`)

```yaml
  servlet:
    multipart:
      # Foto de perfil: el cliente sube un JPEG de 256x256 (~30 KB). El handler rechaza más de 300 KB
      # con 400; esto corta antes (413) lo que no tiene sentido leer en memoria.
      max-file-size: 1MB
      max-request-size: 1MB
```

- [ ] **Step 7: Ejecutar los tests del controller**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl api-rest -am -Dtest=DraftScheduleUserControllersTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add src\application src\api-rest src\boot\src\main\resources\application.yml
$msg = @'
feat: endpoints de foto de perfil y usuario actual

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git commit -m $msg
```

---

### Task 5: Test de integración y PR

**Files:**
- Modify: `src/boot/src/test/java/com/villu/pokefantasy/it/ApiClient.java`
- Create: `src/boot/src/test/java/com/villu/pokefantasy/it/AvatarIntegrationTest.java`

**Interfaces:**
- Consumes: contrato HTTP de Task 4.
- Produces: `ApiClient.putMultipart(String path, String field, String filename, String contentType, byte[] content): HttpResponse<String>`, `ApiClient.getBytes(String path): HttpResponse<byte[]>`.

- [ ] **Step 1: Ampliar `ApiClient`** (imports: `java.io.ByteArrayOutputStream`, `java.nio.charset.StandardCharsets`)

Sustituir el `send` privado por estas dos versiones y añadir los dos métodos públicos:

```java
    /** Multipart con una sola parte de fichero, como el FormData del navegador. */
    public HttpResponse<String> putMultipart(String path, String field, String filename,
                                             String contentType, byte[] content) throws Exception {
        String boundary = "----pokefantasy" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
    }

    public HttpResponse<byte[]> getBytes(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return send(builder, HttpResponse.BodyHandlers.ofString());
    }

    private <T> HttpResponse<T> send(HttpRequest.Builder builder, HttpResponse.BodyHandler<T> bodyHandler) throws Exception {
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        HttpResponse<T> response = http.send(builder.build(), bodyHandler);
        for (String setCookie : response.headers().allValues("Set-Cookie")) {
            String pair = setCookie.split(";", 2)[0];
            String name = pair.substring(0, pair.indexOf('='));
            String value = pair.substring(pair.indexOf('=') + 1);
            boolean expired = setCookie.contains("Max-Age=0");
            if (expired || value.isEmpty()) cookies.remove(name); else cookies.put(name, value);
        }
        return response;
    }
```

- [ ] **Step 2: Escribir `AvatarIntegrationTest`**

```java
package com.villu.pokefantasy.it;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Foto de perfil de punta a punta: multipart real, transacción, caché y seguridad. */
class AvatarIntegrationTest extends IntegrationTest {

    private static byte[] image(int width, int height, Color color, String format) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void upload_isServedWithImmutableCache_andShowsUpInMeAndLeagueDetail() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        byte[] jpeg = image(256, 256, Color.RED, "jpg");

        HttpResponse<String> upload = ash.putMultipart("/v1/user/avatar", "file", "avatar.jpg", "image/jpeg", jpeg);
        assertThat(upload.statusCode()).isEqualTo(200);
        assertThat(upload.body()).contains("\"avatarVersion\":");
        String version = upload.body().replaceAll("\\D", "");

        HttpResponse<byte[]> served = ash.getBytes("/v1/users/ash/avatar?v=" + version);
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).isEqualTo(jpeg);
        assertThat(served.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(served.headers().firstValue("Cache-Control")).get().asString()
                .contains("max-age=31536000").contains("private").contains("immutable");

        assertThat(ash.get("/v1/user/me").body()).contains("\"avatarVersion\":" + version);

        String leagueId = ash.post("/v1/leagues", "{\"name\":\"Kanto\"}").body().replace("\"", "");
        assertThat(ash.get("/v1/leagues/" + leagueId).body()).contains("\"avatarVersion\":" + version);
    }

    @Test
    void replace_keepsSingleDocument_andDelete_returns404() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        ash.putMultipart("/v1/user/avatar", "file", "a.jpg", "image/jpeg", image(256, 256, Color.RED, "jpg"));
        byte[] second = image(256, 256, Color.BLUE, "jpg");

        assertThat(ash.putMultipart("/v1/user/avatar", "file", "b.jpg", "image/jpeg", second).statusCode()).isEqualTo(200);
        assertThat(mongoTemplate.getCollection("avatars").countDocuments()).isEqualTo(1);
        assertThat(ash.getBytes("/v1/users/ash/avatar").body()).isEqualTo(second);

        assertThat(ash.delete("/v1/user/avatar").statusCode()).isEqualTo(204);
        assertThat(ash.getBytes("/v1/users/ash/avatar").statusCode()).isEqualTo(404);
        // Sin foto: el campo sale null (o no sale si Jackson omite nulos); nunca un número.
        assertThat(ash.get("/v1/user/me").body()).doesNotContainPattern("\"avatarVersion\":\\d");
    }

    @Test
    void upload_png_isRejected() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");

        HttpResponse<String> response = ash.putMultipart("/v1/user/avatar", "file", "a.jpg", "image/jpeg",
                image(256, 256, Color.RED, "png"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("BAD_REQUEST");
    }

    @Test
    void upload_tooLarge_isRejected() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");

        assertThat(ash.putMultipart("/v1/user/avatar", "file", "a.jpg", "image/jpeg", new byte[400 * 1024])
                .statusCode()).isEqualTo(400);
        assertThat(ash.putMultipart("/v1/user/avatar", "file", "a.jpg", "image/jpeg", new byte[1200 * 1024])
                .statusCode()).isEqualTo(413);
    }

    @Test
    void avatar_requiresSession() throws Exception {
        assertThat(client().getBytes("/v1/users/ash/avatar").statusCode()).isEqualTo(401);
    }
}
```

- [ ] **Step 3: Ejecutar el IT (Docker abierto)**

Run: `./mvnw -B -ntp -f src/pom.xml test -pl boot -am -Dtest=AvatarIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (5 tests; los IT los ejecuta surefire, no hay failsafe). Si salen como "skipped", Docker no está arrancado.
- Si `upload_tooLarge_isRejected` da 500 en vez de 413 para 1,2 MB: en `ApiExceptionHandler` añadir `@ExceptionHandler(MaxUploadSizeExceededException.class)` que devuelva `respond(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "La imagen es demasiado grande")` con su test en `ApiExceptionHandlerTest`, y añadir la fila a la tabla de errores de `CLAUDE.md`.
- Si el `PUT` multipart no llega (400 "Required part 'file' is not present"), el contenedor no parsea multipart en PUT: cambiar a `@PostMapping` aquí, en el test del controller y en el plan del front (`apiClient.post`), y anotarlo en ADR-014.

- [ ] **Step 4: Verificación completa**

Run: `./mvnw -B -ntp -f src/pom.xml clean verify`
Expected: `BUILD SUCCESS`, gate JaCoCo incluido.

- [ ] **Step 5: Commit y push**

```powershell
git add src\boot\src\test
$msg = @'
test: integracion de la foto de perfil

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git commit -m $msg
git push -u origin feature/foto-de-perfil
```

- [ ] **Step 6: Abrir el PR contra `develop`** con la API de GitHub (snippet de `CLAUDE.md`), título `feat: foto de perfil (backend)`, cuerpo con: endpoints nuevos, `avatarVersion` en `LeagueMemberResponse`, colección `avatars`, límites, cómo probar, y al final `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

---

### Task 6: Documentación en el vault

**Files (en `C:\PokeFantasy\vault`, commits directos a `main`, `git pull` antes):**
- Create: `70 Decisiones/ADR-014 Fotos de perfil en MongoDB.md`
- Modify: `20 Arquitectura/API REST.md`, `20 Arquitectura/Modelo de datos.md`, `50 Features/Avatar.md`

- [ ] **Step 1: ADR-014** con el formato de ADR-013 (frontmatter `tags: [decision]`, `estado: aceptada`, `area: full`, `fecha: "2026-09-28"`, `prs_back`, `prs_front`):
  - Contexto: fotos de perfil; Render free tier sin disco persistente; 512 MB de RAM; ~10 usuarios.
  - Opciones: (1) colección `avatars` en MongoDB con el recorte ya hecho en el cliente; (2) Cloudinary / Cloudflare R2: servicio y secreto nuevos y efecto fuera de la transacción del comando; (3) solo Pokémon como avatar: no cubre la petición (queda como fase 2).
  - Decisión: opción 1. JPEG 256x256 generado en el navegador con `react-easy-crop` + canvas (dependencia nueva del front), validado en el servidor con ImageIO; `avatarVersion` en `users` y URL versionada con caché inmutable.
  - Consecuencias: ~30 KB por usuario; subir = upsert en la misma transacción; reencuadrar requiere volver a elegir la foto; el `GET` requiere sesión (con el proxy de ADR-013 la cookie viaja con el `<img>`).
- [ ] **Step 2: `API REST.md`**: añadir `GET /v1/user/me`, `PUT`/`DELETE /v1/user/avatar`, `GET /v1/users/{username}/avatar` y el campo `avatarVersion` de los miembros en `GET /v1/leagues/{id}`.
- [ ] **Step 3: `Modelo de datos.md`**: colección `avatars` en la lista, entidad `AvatarEntity` en el diagrama, `Long avatarVersion` en `UserEntity`.
- [ ] **Step 4: `50 Features/Avatar.md`**: `estado: en-curso`, `prs_back` con el número del PR; describir el flujo (subir → encuadrar → guardar, quitar, dónde se ve) y "Fase 2: Pokémon como avatar".
- [ ] **Step 5: Commit y push del vault**

```powershell
cd C:\PokeFantasy\vault
git pull
git add -A
$msg = @'
docs: foto de perfil (ADR-014, API, modelo de datos)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
git commit -m $msg
git push
```
