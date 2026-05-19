package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.Mediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClosedListFacadeTest {

    @Mock private Mediator mediator;

    private ClosedListFacade facade;

    @BeforeEach
    void setUp() {
        facade = new ClosedListFacade(mediator);
    }

    @Test
    void nominate_sendsNominatePokemonCommand() throws Exception {
        facade.nominate("ash", "pikachu", "l1");

        ArgumentCaptor<NominatePokemonCommand> captor = ArgumentCaptor.forClass(NominatePokemonCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
        assertThat(captor.getValue().pokemonName()).isEqualTo("pikachu");
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
    }

    @Test
    void denominate_sendsDenominatePokemonCommand() throws Exception {
        facade.denominate("ash", "pikachu", "l1");

        ArgumentCaptor<DenominatePokemonCommand> captor = ArgumentCaptor.forClass(DenominatePokemonCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
        assertThat(captor.getValue().pokemonName()).isEqualTo("pikachu");
    }

    @Test
    void assignTier_sendsAssignTierCommand() throws Exception {
        facade.assignTier("e1", Tier.S, "l1", "ash");

        ArgumentCaptor<AssignTierCommand> captor = ArgumentCaptor.forClass(AssignTierCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().entryId()).isEqualTo("e1");
        assertThat(captor.getValue().tier()).isEqualTo(Tier.S);
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
        assertThat(captor.getValue().requestingUsername()).isEqualTo("ash");
    }

    @Test
    void getClosedList_sendsGetClosedListCommand() throws Exception {
        when(mediator.send(any(GetClosedListCommand.class))).thenReturn(List.of());

        facade.getClosedList("l1");

        ArgumentCaptor<GetClosedListCommand> captor = ArgumentCaptor.forClass(GetClosedListCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
    }
}
