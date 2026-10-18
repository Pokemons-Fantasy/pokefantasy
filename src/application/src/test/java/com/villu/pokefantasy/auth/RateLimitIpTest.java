package com.villu.pokefantasy.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitIpTest {

    @Test
    void ipv4_isKeptAsIs() {
        assertThat(RateLimitIp.of("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void ipv6_isGroupedByItsSlash64() {
        // Un proveedor da a cada cliente un /64 entero: cambiar los últimos 64 bits no puede saltarse el límite.
        assertThat(RateLimitIp.of("2001:db8:85a3:1:aaaa:bbbb:cccc:dddd")).isEqualTo("2001:db8:85a3:1::/64");
        assertThat(RateLimitIp.of("2001:db8:85a3:1::1")).isEqualTo("2001:db8:85a3:1::/64");
    }

    @Test
    void ipv6_isNormalizedWhateverItsSpelling() {
        assertThat(RateLimitIp.of("2001:0DB8:0000:0001:0:0:0:1")).isEqualTo("2001:db8:0:1::/64");
    }

    @Test
    void ipv4MappedIpv6_isTheIpv4() {
        assertThat(RateLimitIp.of("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void unparseable_isKeptAsIs() {
        assertThat(RateLimitIp.of("no-es-una-ip")).isEqualTo("no-es-una-ip");
        assertThat(RateLimitIp.of("2001:db8::zz")).isEqualTo("2001:db8::zz");
    }
}
