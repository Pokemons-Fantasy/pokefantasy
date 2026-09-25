package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.Role;
import lombok.Data;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "users")
public class UserEntity {
    private String id;
    @Version
    private Long version;
    @Indexed(unique = true)
    private String name;
    /**
     * {@code name} en minúsculas, indexado: la búsqueda por prefijo sin distinguir mayúsculas usa el
     * índice (un regex con la opción "i" no puede). Lo rellena {@code UserRepository.saveUser} y, para
     * usuarios antiguos, {@code UserNameLowerMigration}.
     */
    @Indexed
    private String nameLower;
    private String password;
    private Role role;
    @Indexed(sparse = true)
    private List<String> fcmTokens = new ArrayList<>();
}
