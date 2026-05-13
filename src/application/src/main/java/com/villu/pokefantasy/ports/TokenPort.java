package com.villu.pokefantasy.ports;

public interface TokenPort {
    String generateToken(String username);
    String extractUsername(String token);
    boolean isTokenValid(String token, String username);
}
