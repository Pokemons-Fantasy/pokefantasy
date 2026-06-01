package com.villu.pokefantasy.repository;

public interface InviteRepository {
    void save(String token, String leagueId, long ttlSeconds);
    String findLeagueId(String token);  // null si no existe/expiró
    void delete(String token);
}
