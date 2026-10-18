package com.villu.pokefantasy.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetlifyProxySignatureAdapterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-netlify";

    /** Como la firma Netlify: HS256 con el secreto, iss=netlify y caducidad. */
    private static String sign(String secret, String issuer, Instant expiresAt) {
        return Jwts.builder()
                .issuer(issuer)
                .claim("site_url", "https://pokefantasy.netlify.app")
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private final NetlifyProxySignatureAdapter adapter = new NetlifyProxySignatureAdapter(SECRET);

    @Test
    void validNetlifySignature_isTrusted() {
        assertThat(adapter.trusts(sign(SECRET, "netlify", Instant.now().plusSeconds(60)))).isTrue();
    }

    @Test
    void missingWrongExpiredOrForeignSignature_isNotTrusted() {
        assertThat(adapter.trusts(null)).isFalse();
        assertThat(adapter.trusts(" ")).isFalse();
        assertThat(adapter.trusts("no-es-un-jwt")).isFalse();
        assertThat(adapter.trusts(sign("otro-secreto-de-32-caracteres-o-mas!!", "netlify", Instant.now().plusSeconds(60))))
                .isFalse();
        assertThat(adapter.trusts(sign(SECRET, "atacante", Instant.now().plusSeconds(60)))).isFalse();
        assertThat(adapter.trusts(sign(SECRET, "netlify", Instant.now().minusSeconds(120)))).isFalse();
    }

    @Test
    void withoutSecret_trustsTheProxyHeaderAsBefore() {
        NetlifyProxySignatureAdapter transition = new NetlifyProxySignatureAdapter("");

        assertThat(transition.trusts(null)).isTrue();
        assertThat(new NetlifyProxySignatureAdapter(null).trusts("x")).isTrue();
    }

    @Test
    void shortSecret_failsAtStartup() {
        assertThatThrownBy(() -> new NetlifyProxySignatureAdapter("corto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NETLIFY_PROXY_SECRET");
    }
}
