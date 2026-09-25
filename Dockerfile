# ── Build ─────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Wrapper + POMs primero: la capa de dependencias solo se rehace cuando cambia un pom.xml,
# no con cada cambio de código.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY src/pom.xml src/
COPY src/domain/pom.xml src/domain/
COPY src/application/pom.xml src/application/
COPY src/infrastructure/pom.xml src/infrastructure/
COPY src/api-rest/pom.xml src/api-rest/
COPY src/boot/pom.xml src/boot/
# com.villu son los propios módulos: aún no existen en ningún repositorio.
RUN chmod +x mvnw && ./mvnw -B -ntp -f src/pom.xml dependency:go-offline -DexcludeGroupIds=com.villu

COPY src/ src/
RUN ./mvnw -B -ntp -f src/pom.xml clean package -DskipTests

# ── Runtime ───────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /app/src/boot/target/boot-*.jar app.jar
USER app

# La JVM ajusta el heap a la memoria del contenedor (512 MB en Render free): 75 % para heap y el
# resto para metaspace, threads y buffers. Si aun así se queda sin memoria, sale para que Render
# la reinicie en vez de quedarse medio viva.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
