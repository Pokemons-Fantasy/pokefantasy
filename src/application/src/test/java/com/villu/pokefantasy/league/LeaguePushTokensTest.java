package com.villu.pokefantasy.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaguePushTokensTest {

    @Mock private UserRepository userRepository;

    private static UserEntity user(String name, List<String> tokens) {
        UserEntity u = new UserEntity();
        u.setName(name);
        u.setFcmTokens(tokens);
        return u;
    }

    @Test
    void ofMembers_joinsTokensOfEveryMember() {
        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0), new LeagueMember("brock", LeagueRole.USER, 0)));
        when(userRepository.findByUsername("ash")).thenReturn(user("ash", List.of("t1")));
        when(userRepository.findByUsername("brock")).thenReturn(user("brock", List.of("t2", "t3")));

        assertThat(new LeaguePushTokens(userRepository).ofMembers(league)).containsExactly("t1", "t2", "t3");
    }

    @Test
    void ofMembersExcept_skipsThatUser() {
        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0), new LeagueMember("brock", LeagueRole.USER, 0)));
        when(userRepository.findByUsername("brock")).thenReturn(user("brock", List.of("t2")));

        assertThat(new LeaguePushTokens(userRepository).ofMembersExcept(league, "ash")).containsExactly("t2");
    }

    @Test
    void ofMembersExcept_onlyThatUser_empty() {
        LeagueEntity league = new LeagueEntity();
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0)));

        assertThat(new LeaguePushTokens(userRepository).ofMembersExcept(league, "ash")).isEmpty();
    }

    @Test
    void ofUsers_skipsMissingUsersAndNullTokens() {
        when(userRepository.findByUsername("gone")).thenReturn(null);
        when(userRepository.findByUsername("misty")).thenReturn(user("misty", null));
        when(userRepository.findByUsername("ash")).thenReturn(user("ash", List.of("t1")));

        assertThat(new LeaguePushTokens(userRepository).ofUsers(List.of("gone", "misty", "ash"))).containsExactly("t1");
    }
}
