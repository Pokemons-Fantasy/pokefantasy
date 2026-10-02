package com.villu.pokefantasy.it;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Foto de perfil de punta a punta: multipart real, transacción, caché y seguridad. */
class AvatarIntegrationTest extends IntegrationTest {

    private static byte[] image(int width, int height, Color color, String format) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void upload_isServedWithImmutableCache_andShowsUpInMeAndLeagueDetail() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        byte[] jpeg = image(256, 256, Color.RED, "jpg");

        HttpResponse<String> upload = ash.putMultipart("/v1/user/avatar", "file", "avatar.jpg", "image/jpeg", jpeg);
        assertThat(upload.statusCode()).isEqualTo(200);
        assertThat(upload.body()).contains("\"avatarVersion\":");
        String version = upload.body().replaceAll("\\D", "");

        HttpResponse<byte[]> served = ash.getBytes("/v1/users/ash/avatar?v=" + version);
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).isEqualTo(jpeg);
        assertThat(served.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(served.headers().firstValue("Cache-Control")).get().asString()
                .contains("max-age=31536000").contains("private").contains("immutable");

        assertThat(ash.get("/v1/user/me").body()).contains("\"avatarVersion\":" + version);

        String leagueId = ash.post("/v1/leagues", "{\"name\":\"Kanto\"}").body().replace("\"", "");
        assertThat(ash.get("/v1/leagues/" + leagueId).body()).contains("\"avatarVersion\":" + version);
    }

    @Test
    void replace_keepsSingleDocument_andDelete_returns404() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        ash.putMultipart("/v1/user/avatar", "file", "a.jpg", "image/jpeg", image(256, 256, Color.RED, "jpg"));
        byte[] second = image(256, 256, Color.BLUE, "jpg");

        assertThat(ash.putMultipart("/v1/user/avatar", "file", "b.jpg", "image/jpeg", second).statusCode()).isEqualTo(200);
        assertThat(mongoTemplate.getCollection("avatars").countDocuments()).isEqualTo(1);
        assertThat(ash.getBytes("/v1/users/ash/avatar").body()).isEqualTo(second);

        assertThat(ash.delete("/v1/user/avatar").statusCode()).isEqualTo(204);
        assertThat(ash.getBytes("/v1/users/ash/avatar").statusCode()).isEqualTo(404);
        // Sin foto: el campo sale null (o no sale si Jackson omite nulos); nunca un número.
        assertThat(ash.get("/v1/user/me").body()).doesNotContainPattern("\"avatarVersion\":\\d");
    }

    @Test
    void upload_png_isRejected() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");

        HttpResponse<String> response = ash.putMultipart("/v1/user/avatar", "file", "a.jpg", "image/jpeg",
                image(256, 256, Color.RED, "png"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("BAD_REQUEST");
    }

    @Test
    void upload_tooLarge_isRejected() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");

        assertThat(ash.putMultipart("/v1/user/avatar", "file", "a.jpg", "image/jpeg", new byte[400 * 1024])
                .statusCode()).isEqualTo(400);
        assertThat(ash.putMultipart("/v1/user/avatar", "file", "a.jpg", "image/jpeg", new byte[1200 * 1024])
                .statusCode()).isEqualTo(413);
    }

    @Test
    void avatar_isOnlyVisibleToLeagueMates() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        ApiClient misty = client().loggedInAs("misty", "pikachu123");
        ash.putMultipart("/v1/user/avatar", "file", "a.jpg", "image/jpeg", image(256, 256, Color.RED, "jpg"));

        assertThat(misty.getBytes("/v1/users/ash/avatar").statusCode()).isEqualTo(404);

        String leagueId = ash.post("/v1/leagues", "{\"name\":\"Kanto\"}").body().replace("\"", "");
        ash.post("/v1/leagues/" + leagueId + "/members", "{\"username\":\"misty\"}");

        assertThat(misty.getBytes("/v1/users/ash/avatar").statusCode()).isEqualTo(200);
    }

    @Test
    void avatar_requiresSession() throws Exception {
        assertThat(client().getBytes("/v1/users/ash/avatar").statusCode()).isEqualTo(401);
    }
}
