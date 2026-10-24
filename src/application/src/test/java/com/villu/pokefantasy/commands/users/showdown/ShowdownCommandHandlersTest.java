package com.villu.pokefantasy.commands.users.showdown;

import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowdownCommandHandlersTest {

    @Mock private UserRepository userRepository;

    private static UserEntity user(String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        return user;
    }

    @Test
    void link_free_savesAccount() {
        when(userRepository.findByShowdownId("ashketchum")).thenReturn(Optional.empty());

        new LinkShowdownCommandHandler(userRepository)
                .handle(new LinkShowdownCommand("ash", "Ash Ketchum", "ashketchum"));

        verify(userRepository).setShowdownAccount("ash", "Ash Ketchum", "ashketchum");
    }

    @Test
    void link_takenByOther_throwsIllegalState() {
        when(userRepository.findByShowdownId("ashketchum")).thenReturn(Optional.of(user("misty")));

        assertThatThrownBy(() -> new LinkShowdownCommandHandler(userRepository)
                .handle(new LinkShowdownCommand("ash", "Ash Ketchum", "ashketchum")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La cuenta de Showdown 'Ash Ketchum' ya está vinculada a otro jugador.");
        verify(userRepository, never()).setShowdownAccount(any(), any(), any());
    }

    @Test
    void link_sameUserAgain_saves() {
        when(userRepository.findByShowdownId("ashketchum")).thenReturn(Optional.of(user("ash")));

        new LinkShowdownCommandHandler(userRepository)
                .handle(new LinkShowdownCommand("ash", "Ash Ketchum", "ashketchum"));

        verify(userRepository).setShowdownAccount("ash", "Ash Ketchum", "ashketchum");
    }

    @Test
    void unlink_clearsAccount() {
        new UnlinkShowdownCommandHandler(userRepository).handle(new UnlinkShowdownCommand("ash"));

        verify(userRepository).clearShowdownAccount("ash");
    }

    @Test
    void commandTypes() {
        assertThat(new LinkShowdownCommandHandler(userRepository).commandType()).isEqualTo(LinkShowdownCommand.class);
        assertThat(new UnlinkShowdownCommandHandler(userRepository).commandType())
                .isEqualTo(UnlinkShowdownCommand.class);
    }
}
