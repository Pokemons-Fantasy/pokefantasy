package com.villu.pokefantasy.security;

import com.villu.pokefantasy.ports.ProxySignaturePort;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Comprueba la firma del proxy firmado de Netlify ({@code signed} en el {@code netlify.toml} de la web): un JWS
 * HS256 con {@code iss=netlify} y caducidad, firmado con el valor de {@code API_SIGNATURE_TOKEN} de Netlify, que
 * aquí es {@code NETLIFY_PROXY_SECRET}.
 */
@Component
@Slf4j
public class NetlifyProxySignatureAdapter implements ProxySignaturePort {

    /** HS256 exige una clave de al menos 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    /** {@code null} sin secreto: modo transición (ver {@link ProxySignaturePort#trusts}). */
    private final JwtParser parser;

    public NetlifyProxySignatureAdapter(@Value("${pokefantasy.netlify-proxy-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            parser = null;
            log.warn("NETLIFY_PROXY_SECRET sin configurar: se confía en la IP que manda el proxy de Netlify sin "
                    + "comprobar su firma");
            return;
        }
        byte[] key = secret.strip().getBytes(StandardCharsets.UTF_8);
        if (key.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("NETLIFY_PROXY_SECRET debe tener al menos " + MIN_SECRET_BYTES
                    + " caracteres");
        }
        parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(key))
                .requireIssuer("netlify")
                .clockSkewSeconds(30)
                .build();
    }

    @Override
    public boolean trusts(String signature) {
        if (parser == null) {
            return true;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            parser.parseSignedClaims(signature);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Firma del proxy de Netlify no válida: {}", exception.getMessage());
            return false;
        }
    }
}
