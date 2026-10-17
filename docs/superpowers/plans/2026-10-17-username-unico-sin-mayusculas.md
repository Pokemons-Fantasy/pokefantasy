# Username único sin distinguir mayúsculas (S7) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que Mongo impida que coexistan `Lobon` y `lobon` con un índice único en `users.nameLower`, también en la base de producción que ya tiene ese índice como no único.

**Architecture:** `UserEntity.nameLower` pasa a `@Indexed(unique = true)`. `MongoIndexInitializer` aprende a cambiar las opciones de un índice existente: si `createIndex` falla por conflicto de opciones (códigos Mongo 85 `IndexOptionsConflict` / 86 `IndexKeySpecsConflict`), borra el índice existente con ese nombre o esas claves y lo recrea; si la recreación falla (p. ej. duplicados), recrea la versión sin `unique` y registra ERROR. Se ejecuta después de `UserNameLowerMigration` (`@DependsOn`).

**Tech Stack:** Spring Boot 4.1, Spring Data MongoDB (`IndexOperations`, `IndexInfo`), JUnit 5 + Mockito, Testcontainers (mongo:7).

**Spec:** diseño aprobado en chat (sesión S7, 2026-10-17). Auditoría: vault `60 Operaciones/Auditoría seguridad y legal 2026-09.md` (S7).

## Global Constraints

- Producción comprobada el 2026-10-17: 0 duplicados por `toLower(name)` y 0 usuarios sin `nameLower`. No hace falta migración de datos.
- El arranque nunca falla por un índice: los errores se registran como ERROR (comportamiento actual de `MongoIndexInitializer`).
- Rama `fix/username-unico-sin-mayusculas` desde `origin/develop`, PR a `develop`.

## Review Focus

- Base con el índice `nameLower` antiguo no único → tras arrancar queda único (IT `existingNonUniqueIndex_isUpgradedToUnique`).
- Recreación que falla por duplicados → se vuelve a crear sin `unique` y no se lanza (unit `conflictThenDuplicates_fallsBackToNonUnique`).
- Error que no es conflicto de opciones (p. ej. `DuplicateKeyException` directo) → no se borra nada, solo ERROR (test existente + verify `dropIndex` nunca).
- Registro concurrente `Lobon`/`lobon` → el segundo `saveUser` lanza `DuplicateKeyException` (IT `caseInsensitiveDuplicate_isRejectedByTheIndex`).

---

### Task 1: Índice único en nameLower con actualización de opciones

**Files:**
- Modify: `src/domain/src/main/java/com/villu/pokefantasy/repository/entity/UserEntity.java:25` (`@Indexed(unique = true)`, actualizar javadoc)
- Modify: `src/infrastructure/src/main/java/com/villu/pokefantasy/migration/MongoIndexInitializer.java`
- Test: `src/infrastructure/src/test/java/com/villu/pokefantasy/migration/MongoIndexInitializerTest.java`
- Test: `src/boot/src/test/java/com/villu/pokefantasy/it/QueriesIntegrationTest.java`

**Interfaces:**
- Produces: `MongoIndexInitializer` anotado `@DependsOn("userNameLowerMigration")`; método privado `isOptionsConflict(RuntimeException)` que recorre las causas buscando `com.mongodb.MongoException` con `getCode()` 85 u 86.

- [ ] **Step 1: Tests unitarios que fallan** (en `MongoIndexInitializerTest`, con `UncategorizedMongoDbException` envolviendo un `MongoCommandException` con `{code: 86, errmsg: ...}`):
  - `optionsConflict_dropsTheOldIndexAndRecreatesIt`: `users.createIndex` lanza conflicto la primera vez y luego devuelve el nombre; `users.getIndexInfo()` devuelve un `IndexInfo` llamado `nameLower` sobre `nameLower`. Verificar `dropIndex("nameLower")` y que el índice `nameLower` se crea dos veces.
  - `conflictThenDuplicates_fallsBackToNonUnique`: tras el drop, la creación única lanza `DuplicateKeyException`; verificar una tercera `createIndex` cuyo `getIndexOptions()` no contiene `unique` y que no se lanza nada.
  - En el test existente, añadir `verify(users, never()).dropIndex(anyString())`.
- [ ] **Step 2:** `./mvnw -B -ntp -f src/pom.xml test -pl infrastructure -am -Dtest=MongoIndexInitializerTest -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3: Implementar** en `MongoIndexInitializer`: en el `catch` de `createIndex`, si `isOptionsConflict(e)`, `recreate(entity, index)`: buscar en `indexOps.getIndexInfo()` el índice con el mismo nombre (`index.getIndexOptions().getString("name")`) o `isIndexForFields(keys)`, `dropIndex(name)`, `createIndex(index)`; si falla, ERROR y `createIndex` de una `IndexDefinition` con las mismas claves y las opciones sin `unique`. `UserEntity.nameLower` → `@Indexed(unique = true)`. Anotar la clase con `@DependsOn("userNameLowerMigration")`.
- [ ] **Step 4:** mismo comando → PASS.
- [ ] **Step 5: ITs** en `QueriesIntegrationTest`:
  - `caseInsensitiveDuplicate_isRejectedByTheIndex`: `user("Lobon")`, luego `saveUser` de `lobon` → `assertThatThrownBy(...).isInstanceOf(DuplicateKeyException.class)`.
  - `existingNonUniqueIndex_isUpgradedToUnique`: `dropIndex("nameLower")`, crear `new Index("nameLower", ASC).named("nameLower")` no único, llamar a `MongoIndexInitializer.createIndexes()`, y comprobar en `getIndexInfo()` que `nameLower` es `isUnique()`.
- [ ] **Step 6:** `./mvnw -B -ntp -f src/pom.xml clean verify` (con Docker arrancado) → BUILD SUCCESS.
- [ ] **Step 7: Commit** `fix(users): username único sin distinguir mayúsculas (S7)`.

### Task 2: PR y vault

- [ ] PR a `develop` vía API de GitHub.
- [ ] Vault: S7 marcado con el PR en la auditoría; en Gotchas cerrar el pendiente de duplicados (comprobado 2026-10-17) y anotar que cambiar opciones de un `@Indexed` lo resuelve `MongoIndexInitializer` borrando y recreando.
