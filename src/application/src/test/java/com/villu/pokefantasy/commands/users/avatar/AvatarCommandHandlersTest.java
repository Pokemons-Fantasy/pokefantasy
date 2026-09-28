package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.AvatarEntity;
import com.villu.pokefantasy.response.AvatarImageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarCommandHandlersTest {

    @Mock private AvatarRepository avatarRepository;
    @Mock private UserRepository userRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    @Test
    void upload_savesAvatarAndSetsVersionToItsTimestamp() {
        byte[] image = TestImages.jpeg(256, 256);

        Long version = new UploadAvatarCommandHandler(avatarRepository, userRepository)
                .handle(new UploadAvatarCommand("ash", image));

        ArgumentCaptor<AvatarEntity> saved = ArgumentCaptor.forClass(AvatarEntity.class);
        verify(avatarRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("ash");
        assertThat(saved.getValue().getData()).isSameAs(image);
        assertThat(saved.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(version).isEqualTo(saved.getValue().getUpdatedAt().toEpochMilli());
        verify(userRepository).setAvatarVersion("ash", version);
    }

    @Test
    void upload_invalidImage_savesNothing() {
        UploadAvatarCommandHandler handler = new UploadAvatarCommandHandler(avatarRepository, userRepository);

        assertThatThrownBy(() -> handler.handle(new UploadAvatarCommand("ash", TestImages.png(256, 256))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(avatarRepository, never()).save(any());
        verify(userRepository, never()).setAvatarVersion(anyString(), anyLong());
    }

    @Test
    void delete_removesAvatarAndClearsVersion() {
        new DeleteAvatarCommandHandler(avatarRepository, userRepository).handle(new DeleteAvatarCommand("ash"));

        verify(avatarRepository).deleteByUsername("ash");
        verify(userRepository).setAvatarVersion("ash", null);
    }

    @Test
    void get_leagueMate_returnsBytesAndContentType() {
        byte[] data = {1, 2, 3};
        when(leagueMembershipGuard.sharesLeague("misty", "ash")).thenReturn(true);
        when(avatarRepository.findByUsername("ash"))
                .thenReturn(Optional.of(new AvatarEntity("ash", data, "image/jpeg", Instant.EPOCH)));

        Optional<AvatarImageResponse> result = new GetAvatarCommandHandler(avatarRepository, leagueMembershipGuard)
                .handle(new GetAvatarCommand("ash", "misty"));

        assertThat(result).get().satisfies(r -> {
            assertThat(r.data()).isSameAs(data);
            assertThat(r.contentType()).isEqualTo("image/jpeg");
        });
    }

    @Test
    void get_missing_isEmpty() {
        when(leagueMembershipGuard.sharesLeague("ash", "misty")).thenReturn(true);
        when(avatarRepository.findByUsername("misty")).thenReturn(Optional.empty());

        assertThat(new GetAvatarCommandHandler(avatarRepository, leagueMembershipGuard)
                .handle(new GetAvatarCommand("misty", "ash"))).isEmpty();
    }

    @Test
    void get_notALeagueMate_isEmptyWithoutReadingTheAvatar() {
        when(leagueMembershipGuard.sharesLeague("gary", "ash")).thenReturn(false);

        assertThat(new GetAvatarCommandHandler(avatarRepository, leagueMembershipGuard)
                .handle(new GetAvatarCommand("ash", "gary"))).isEmpty();
        verify(avatarRepository, never()).findByUsername(anyString());
    }

    @Test
    void commandTypes() {
        assertThat(new UploadAvatarCommandHandler(avatarRepository, userRepository).commandType())
                .isEqualTo(UploadAvatarCommand.class);
        assertThat(new DeleteAvatarCommandHandler(avatarRepository, userRepository).commandType())
                .isEqualTo(DeleteAvatarCommand.class);
        assertThat(new GetAvatarCommandHandler(avatarRepository, leagueMembershipGuard).commandType())
                .isEqualTo(GetAvatarCommand.class);
    }
}
