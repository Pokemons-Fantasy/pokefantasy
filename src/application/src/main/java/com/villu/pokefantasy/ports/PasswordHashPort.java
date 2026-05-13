package com.villu.pokefantasy.ports;

public interface PasswordHashPort {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
