package com.villu.pokefantasy.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/v1/user", "/v1/user/login", "/v1/user/logout",
            "/actuator/health", "/actuator/health/liveness");

    /** Deploy previews de la web en Netlify ({@code deploy-preview-<nº de PR>--pokefantasy}). */
    private static final Pattern DEPLOY_PREVIEW =
            Pattern.compile("https://deploy-preview-\\d+--pokefantasy\\.netlify\\.app");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                           SecurityContextRepository securityContextRepository,
                                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        RequestMatcher publicPaths = request -> PUBLIC_PATHS.contains(request.getServletPath())
                || isApiDocs(request.getServletPath());

        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(publicPaths).permitAll()
                        // Métricas: solo administradores de la app (rol global ADMIN, no de liga).
                        .requestMatchers(request -> request.getServletPath().startsWith("/actuator/metrics"))
                        .hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(problemDetailEntryPoint(resolver)))
                .build();
    }

    /**
     * Sin sesión: 401 con el mismo ProblemDetail que el resto de errores ({@code ApiExceptionHandler},
     * código {@code UNAUTHENTICATED}). Sin esto Spring Security responde 403 y el front no distingue
     * "no has iniciado sesión" de "no tienes permiso".
     */
    static AuthenticationEntryPoint problemDetailEntryPoint(HandlerExceptionResolver resolver) {
        return (request, response, exception) -> {
            if (resolver.resolveException(request, response, null, exception) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
        };
    }

    /**
     * Dónde vive la autenticación de cada petición: en un atributo de la propia petición, sin sesión (la
     * API es stateless). {@link JwtAuthFilter} la guarda aquí y la cadena la carga en cada dispatch, así
     * que los ASYNC (cierre de una conexión SSE) y ERROR de la misma petición siguen autenticados.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new RequestAttributeSecurityContextRepository();
    }

    /** Especificación OpenAPI y Swagger UI: públicas (la API la ve igualmente cualquiera con el frontend). */
    static boolean isApiDocs(String path) {
        return path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui");
    }

    /**
     * Orígenes con acceso a la API con credenciales. Nunca {@code *.netlify.app}: cualquiera publica gratis
     * ahí y, con las cookies {@code SameSite=None}, leería y escribiría la API con la sesión de la víctima.
     * <ul>
     *   <li>La web: aunque llama por el proxy {@code /api} de Netlify (mismo sitio), el proxy reenvía su
     *       {@code Origin} y Spring la trata como petición CORS.</li>
     *   <li>La app Android (Capacitor) y el desarrollo local.</li>
     *   <li>Los deploy previews de la web, con {@link #DEPLOY_PREVIEW}: el {@code *} de
     *       {@code allowedOriginPatterns} equivale a {@code .*} y admitiría otros dominios.</li>
     * </ul>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration() {
            @Override
            public String checkOrigin(String origin) {
                String allowed = super.checkOrigin(origin);
                if (allowed == null && origin != null && DEPLOY_PREVIEW.matcher(origin).matches()) {
                    return origin;
                }
                return allowed;
            }
        };
        config.setAllowedOriginPatterns(List.of(
                "https://pokefantasy.netlify.app",
                "http://localhost:[*]",
                "http://127.0.0.1:[*]",
                "capacitor://localhost",
                "https://localhost"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
