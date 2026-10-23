package com.villu.pokefantasy.auth;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Con qué IP cuentan los límites por IP (login y registro). Una IPv4 cuenta tal cual. Una IPv6 cuenta por su
 * /64: los proveedores dan a cada cliente un /64 entero, y si contase la dirección exacta bastaría con cambiar
 * los últimos 64 bits para saltarse el límite.
 */
public final class RateLimitIp {

    private RateLimitIp() {}

    public static String of(String ip) {
        // Solo los literales IPv6 llevan ':'. Sin esta comprobación, InetAddress resolvería nombres por DNS.
        if (ip.indexOf(':') < 0) {
            return ip;
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(ip);
        } catch (UnknownHostException invalid) {
            return ip;
        }
        if (!(address instanceof Inet6Address)) {
            return address.getHostAddress(); // IPv4 mapeada (::ffff:a.b.c.d)
        }
        byte[] bytes = address.getAddress();
        StringBuilder prefix = new StringBuilder();
        for (int group = 0; group < 4; group++) {
            int value = ((bytes[2 * group] & 0xff) << 8) | (bytes[2 * group + 1] & 0xff);
            prefix.append(Integer.toHexString(value)).append(':');
        }
        return prefix.append(":/64").toString();
    }
}
