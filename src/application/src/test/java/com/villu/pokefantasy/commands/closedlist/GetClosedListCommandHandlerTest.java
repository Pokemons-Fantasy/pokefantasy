package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.response.ClosedListEntryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetClosedListCommandHandlerTest {

    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    private GetClosedListCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetClosedListCommandHandler(closedListRepository, leagueMembershipGuard);
    }

    @Test
    void handle_emptyList_returnsEmpty() {
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of());

        assertThat(handler.handle(new GetClosedListCommand("l1", "ash"))).isEmpty();
    }

    @Test
    void handle_withEntries_mapsCorrectly() {
        ClosedListEntity entity = new ClosedListEntity();
        entity.setId("e1");
        entity.setPokemonId(25);
        entity.setPokemonName("pikachu");
        entity.setTier(Tier.S);
        entity.setNominatedBy("ash");
        entity.setSprite("https://sprite.url");
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(entity));

        List<ClosedListEntryResponse> result = handler.handle(new GetClosedListCommand("l1", "ash"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("e1");
        assertThat(result.get(0).getPokemonId()).isEqualTo(25);
        assertThat(result.get(0).getPokemonName()).isEqualTo("pikachu");
        assertThat(result.get(0).getTier()).isEqualTo(Tier.S);
        assertThat(result.get(0).getNominatedBy()).isEqualTo("ash");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetClosedListCommand.class);
    }
}
