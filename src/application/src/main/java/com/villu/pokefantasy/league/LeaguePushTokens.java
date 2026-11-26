package com.villu.pokefantasy.league;

import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Tokens push a los que mandar un aviso de liga: los usuarios que no existen o no tienen tokens se saltan. */
@Service
public class LeaguePushTokens {

    private final UserRepository userRepository;

    public LeaguePushTokens(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Los tokens de todos los miembros de la liga. */
    public List<String> ofMembers(LeagueEntity league) {
        return ofUsers(league.getMembers().stream().map(LeagueMember::getUsername).toList());
    }

    /** Los tokens de esos usuarios. Uno a uno: {@code findByUsernames} no trae los tokens. */
    public List<String> ofUsers(Collection<String> usernames) {
        return usernames.stream()
                .map(userRepository::findByUsername)
                .filter(Objects::nonNull)
                .map(UserEntity::getFcmTokens)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }
}
