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
# Jar extraído (app.jar + lib/): carga las clases más rápido que el fat jar anidado.
RUN cp src/boot/target/boot-*.jar app.jar && java -Djarmode=tools -jar app.jar extract --destination extracted

# ── Runtime ───────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /app/extracted/ ./
USER app

# La JVM ajusta el heap a la memoria del contenedor (512 MB en Render free): 75 % para heap y el
# resto para metaspace, threads y buffers. Si aun así se queda sin memoria, sale para que Render
# la reinicie en vez de quedarse medio viva.
# TieredStopAtLevel=1: solo el compilador C1. Con 0,15 CPU el arranque lo limita la CPU y el JIT C2
# compite con la carga de clases; sin C2 el pico de rendimiento es menor, irrelevante con este tráfico.
# Medido en local con --cpus=0.15 --memory=512m: 105 s → 39 s junto con el jar extraído.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
