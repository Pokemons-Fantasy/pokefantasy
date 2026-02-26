package com.villu.pokefantasy.commands.users.saveUser;

import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.request.user.UserRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Service
public class SaveUser {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    public void saveUser(UserRequest userRequest) {
        if (userRequest == null || userRequest.getUsername().isEmpty() || userRequest.getPassword().isEmpty()) {
            throw new IllegalArgumentException("UserRequest cannot be null or have values empty");
        }
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .name(userRequest.getUsername())
                .password(Base64.getEncoder().encodeToString(userRequest.getPassword().getBytes(StandardCharsets.UTF_8)))
                .build();

        userRepository.saveUser(userMapper.dtoToEntity(user));
    }

    public boolean login(UserRequest userRequest) {
        if (userRequest == null || userRequest.getUsername().isEmpty() || userRequest.getPassword().isEmpty()) {
            throw new IllegalArgumentException("UserRequest cannot be null or have values empty");
        }
        User user = userMapper.entityToDto(userRepository.findByUsername(userRequest.getUsername()));
        if (user == null) {
            return false;
        }
        String encodedPassword = Base64.getEncoder().encodeToString(userRequest.getPassword().getBytes(StandardCharsets.UTF_8));
        return user.getPassword().equals(encodedPassword);
    }
}
