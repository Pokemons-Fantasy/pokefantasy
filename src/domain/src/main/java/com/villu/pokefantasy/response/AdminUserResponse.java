package com.villu.pokefantasy.response;

/** Usuario en el panel de administración (L5). */
public record AdminUserResponse(String username, Long avatarVersion, boolean admin) {}
