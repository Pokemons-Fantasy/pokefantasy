package com.villu.pokefantasy.ports;

/**
 * Si una petición llegó por el proxy {@code /api} de la web (Netlify), para fiarse de la IP del cliente que
 * pone ese proxy. Netlify firma cada petición con un secreto compartido (cabecera {@code x-nf-sign}).
 */
public interface ProxySignaturePort {

    /**
     * {@code true} si {@code signature} (la cabecera {@code x-nf-sign}) es una firma válida de Netlify. Sin
     * secreto configurado también devuelve {@code true}: modo transición en el que se confía en la cabecera del
     * proxy, con el mismo riesgo que antes con {@code X-Forwarded-For}.
     */
    boolean trusts(String signature);
}
