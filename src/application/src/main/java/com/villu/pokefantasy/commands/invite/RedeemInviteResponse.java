package com.villu.pokefantasy.commands.invite;

/** {@code alreadyMember}: el usuario ya estaba en la liga y no se ha añadido de nuevo. */
public record RedeemInviteResponse(String leagueId, boolean alreadyMember) {}
