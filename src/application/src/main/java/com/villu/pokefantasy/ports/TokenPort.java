package com.villu.pokefantasy.ports;

import java.time.Duration;

public interface TokenPort {
    String generateToken(String username);
    String extractUsername(String token);
    boolean isTokenValid(String token, String username);
    /** Vida del JWT de acceso (también el {@code Max-Age} de su cookie). */
    Duration accessTokenTtl();
}
