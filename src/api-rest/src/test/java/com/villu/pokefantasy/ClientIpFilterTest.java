package com.villu.pokefantasy;

import com.villu.pokefantasy.ports.ProxySignaturePort;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpFilterTest {

    /** Firma válida solo si es "firmada". */
    private final ClientIpFilter filter = new ClientIpFilter("firmada"::equals);

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/user/login");
        request.setRemoteAddr("10.0.0.5");
        // Lo que manda un cliente que intenta saltarse el límite: nunca se usa
        request.addHeader("X-Forwarded-For", "6.6.6.6");
        return request;
    }

    @Test
    void web_signedByNetlify_usesTheClientIpNetlifySaw() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpFilter.NETLIFY_CLIENT_IP, " 87.216.96.142 ");
        request.addHeader(ClientIpFilter.NETLIFY_SIGNATURE, "firmada");
        request.addHeader(ClientIpFilter.CLOUDFLARE_CLIENT_IP, "3.120.0.1");

        assertThat(filter.resolve(request)).isEqualTo("87.216.96.142");
    }

    @Test
    void netlifyHeaderWithoutValidSignature_isIgnored_usesCloudflare() {
        // Alguien que llama directo a Render y se inventa la cabecera de Netlify
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpFilter.NETLIFY_CLIENT_IP, "8.8.8.8");
        request.addHeader(ClientIpFilter.NETLIFY_SIGNATURE, "falsa");
        request.addHeader(ClientIpFilter.CLOUDFLARE_CLIENT_IP, "203.0.113.9");

        assertThat(filter.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void appAndDirectCalls_useCloudflare_localUsesTheSocket() {
        MockHttpServletRequest app = request();
        app.addHeader(ClientIpFilter.CLOUDFLARE_CLIENT_IP, "203.0.113.9");
        assertThat(filter.resolve(app)).isEqualTo("203.0.113.9");

        MockHttpServletRequest local = request();
        local.addHeader(ClientIpFilter.NETLIFY_CLIENT_IP, " ");
        local.addHeader(ClientIpFilter.CLOUDFLARE_CLIENT_IP, "");
        assertThat(filter.resolve(local)).isEqualTo("10.0.0.5");
    }

    @Test
    void filter_leavesTheIpInTheRequestAndContinues() throws Exception {
        ProxySignaturePort transition = signature -> true;
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpFilter.NETLIFY_CLIENT_IP, "87.216.96.142");
        MockFilterChain chain = new MockFilterChain();

        new ClientIpFilter(transition).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(ClientIpFilter.ATTRIBUTE)).isEqualTo("87.216.96.142");
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
