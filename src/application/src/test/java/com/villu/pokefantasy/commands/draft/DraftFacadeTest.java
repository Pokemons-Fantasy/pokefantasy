package com.villu.pokefantasy.commands.draft;

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
class DraftFacadeTest {

    @Mock private Mediator mediator;

    private DraftFacade facade;

    @BeforeEach
    void setUp() {
        facade = new DraftFacade(mediator);
    }

    @Test
    void startDraft_sendsStartDraftCommand() throws Exception {
        facade.startDraft(List.of("ash", "brock"), "l1", "ash");

        ArgumentCaptor<StartDraftCommand> captor = ArgumentCaptor.forClass(StartDraftCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
        assertThat(captor.getValue().requestingUsername()).isEqualTo("ash");
        assertThat(captor.getValue().turnOrder()).containsExactly("ash", "brock");
    }

    @Test
    void pick_sendsDraftPickCommand() throws Exception {
        facade.pick("ash", "pikachu", "l1");

        ArgumentCaptor<DraftPickCommand> captor = ArgumentCaptor.forClass(DraftPickCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
        assertThat(captor.getValue().pokemonName()).isEqualTo("pikachu");
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
    }

    @Test
    void getStatus_sendsGetDraftStatusCommand() throws Exception {
        when(mediator.send(any(GetDraftStatusCommand.class))).thenReturn(null);

        facade.getStatus("l1");

        ArgumentCaptor<GetDraftStatusCommand> captor = ArgumentCaptor.forClass(GetDraftStatusCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
    }

    @Test
    void cancelDraft_sendsCancelDraftCommand() throws Exception {
        facade.cancelDraft("l1", "ash");

        ArgumentCaptor<CancelDraftCommand> captor = ArgumentCaptor.forClass(CancelDraftCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
        assertThat(captor.getValue().requestingUsername()).isEqualTo("ash");
    }
}
