package com.villu.pokefantasy.commands.users.login;

import java.time.Duration;

/** {@code refreshToken} es {@code null} si no se pudo guardar: la sesión dura lo que el token de acceso. */
public record LoginResult(String accessToken, Duration accessTokenTtl,
                          String refreshToken, Duration refreshTokenTtl) {}
