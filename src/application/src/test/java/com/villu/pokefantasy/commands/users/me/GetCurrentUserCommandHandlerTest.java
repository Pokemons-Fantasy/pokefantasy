package com.villu.pokefantasy.commands.users.me;

import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import com.villu.pokefantasy.response.CurrentUserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserCommandHandlerTest {

    @Mock private UserRepository userRepository;

    @Test
    void handle_returnsUsernameAndAvatarVersion() {
        UserEntity user = new UserEntity();
        user.setName("ash");
        user.setAvatarVersion(42L);
        when(userRepository.findByUsername("ash")).thenReturn(user);

        CurrentUserResponse result = new GetCurrentUserCommandHandler(userRepository)
                .handle(new GetCurrentUserCommand("ash"));

        assertThat(result.getUsername()).isEqualTo("ash");
        assertThat(result.getAvatarVersion()).isEqualTo(42L);
    }

    @Test
    void handle_unknownUser_throwsIllegalArgument() {
        assertThatThrownBy(() -> new GetCurrentUserCommandHandler(userRepository)
                .handle(new GetCurrentUserCommand("ghost")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commandType() {
        assertThat(new GetCurrentUserCommandHandler(userRepository).commandType())
                .isEqualTo(GetCurrentUserCommand.class);
    }
}
