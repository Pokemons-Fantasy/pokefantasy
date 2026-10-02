package com.villu.pokefantasy.commands.users.avatar;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarImagePolicyTest {

    @Test
    void jpeg256_isAccepted() {
        assertThatCode(() -> AvatarImagePolicy.validate(TestImages.jpeg(256, 256))).doesNotThrowAnyException();
    }

    @Test
    void maxSide_isAccepted() {
        assertThatCode(() -> AvatarImagePolicy.validate(TestImages.jpeg(512, 512))).doesNotThrowAnyException();
    }

    @Test
    void aboveMaxSide_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(TestImages.jpeg(513, 512)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("512");
        assertThatThrownBy(() -> AvatarImagePolicy.validate(TestImages.jpeg(512, 513)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("512");
    }

    @Test
    void png_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(TestImages.png(256, 256)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPEG");
    }

    @Test
    void empty_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vacía");
        assertThatThrownBy(() -> AvatarImagePolicy.validate(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vacía");
    }

    @Test
    void garbage_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(new byte[]{1, 2, 3, 4, 5}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no es una imagen");
    }

    @Test
    void truncatedJpeg_isRejected() {
        byte[] headerOnly = Arrays.copyOf(TestImages.jpeg(256, 256), 4); // SOI + inicio de segmento, sin SOF
        assertThatThrownBy(() -> AvatarImagePolicy.validate(headerOnly))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no se puede leer");
    }

    @Test
    void tooManyBytes_isRejected() {
        assertThatThrownBy(() -> AvatarImagePolicy.validate(new byte[AvatarImagePolicy.MAX_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("300 KB");
    }
}
