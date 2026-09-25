package com.villu.pokefantasy;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI (springdoc): especificación en {@code /v3/api-docs} y Swagger UI en
 * {@code /swagger-ui.html}. La sesión va en la cookie httpOnly {@code jwt} (o en {@code Authorization:
 * Bearer}); desde Swagger UI en el mismo origen basta con hacer login primero.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "PokeFantasy API", version = "v1",
                description = "Ligas fantasy de Pokémon: draft, equipos, robos, trades, banca y calendario. "
                        + "Los errores siguen RFC 9457 (application/problem+json) con un `code` estable."),
        security = {@SecurityRequirement(name = "cookie"), @SecurityRequirement(name = "bearer")})
@SecurityScheme(name = "cookie", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.COOKIE, paramName = "jwt")
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
