package com.villu.pokefantasy;

import com.villu.pokefantasy.ports.ProxySignaturePort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * IP real del cliente (para el límite de intentos de login), sin fiarse de lo que mande el propio cliente. La deja
 * en el atributo {@value #ATTRIBUTE} de la petición.
 * <ul>
 *   <li>Web, por el proxy {@code /api} de Netlify: {@value #NETLIFY_CLIENT_IP}, que Netlify rellena con la IP real
 *       e ignora la que mande el cliente. Solo con la firma del proxy ({@value #NETLIFY_SIGNATURE}): si no,
 *       cualquiera que llame directo a Render pondría la IP que quisiera.</li>
 *   <li>App Android y llamadas directas: {@value #CLOUDFLARE_CLIENT_IP}, que pone Cloudflare delante de Render.</li>
 *   <li>En local, la del socket.</li>
 * </ul>
 * Nunca {@code X-Forwarded-For}: el cliente puede meter entradas delante y Netlify añade la suya detrás.
 */
@Component
public class ClientIpFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = ClientIpFilter.class.getName() + ".ip";
    static final String NETLIFY_CLIENT_IP = "X-Nf-Client-Connection-Ip";
    static final String NETLIFY_SIGNATURE = "X-Nf-Sign";
    static final String CLOUDFLARE_CLIENT_IP = "CF-Connecting-IP";

    private final ProxySignaturePort proxySignature;

    public ClientIpFilter(ProxySignaturePort proxySignature) {
        this.proxySignature = proxySignature;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(ATTRIBUTE, resolve(request));
        chain.doFilter(request, response);
    }

    String resolve(HttpServletRequest request) {
        String netlify = request.getHeader(NETLIFY_CLIENT_IP);
        if (hasText(netlify) && proxySignature.trusts(request.getHeader(NETLIFY_SIGNATURE))) {
            return netlify.strip();
        }
        String cloudflare = request.getHeader(CLOUDFLARE_CLIENT_IP);
        if (hasText(cloudflare)) {
            return cloudflare.strip();
        }
        return request.getRemoteAddr();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
