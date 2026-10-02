package com.villu.pokefantasy.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Foto de perfil de un usuario, ya recortada por el cliente (JPEG pequeño). Un documento por usuario
 * ({@code _id} = username): subir una foto nueva sobrescribe la anterior. Va aparte de {@code users}
 * para no cargar la imagen cada vez que se lee un usuario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "avatars")
public class AvatarEntity {
    @Id
    private String username;
    private byte[] data;
    private String contentType;
    private Instant updatedAt;
}
