package com.villu.pokefantasy.security;

import com.villu.pokefantasy.dto.Role;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock private UserRepository userRepository;

    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsServiceImpl(userRepository);
    }

    @Test
    void loadUserByUsername_adminUser_returnsUserDetailsWithAdminRole() {
        UserEntity entity = new UserEntity();
        entity.setName("ash");
        entity.setPassword("hashed");
        entity.setRole(Role.ADMIN);
        when(userRepository.findByUsername("ash")).thenReturn(entity);

        UserDetails result = service.loadUserByUsername("ash");

        assertThat(result.getUsername()).isEqualTo("ash");
        assertThat(result.getPassword()).isEqualTo("hashed");
        assertThat(result.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_regularUser_returnsUserDetailsWithUserRole() {
        UserEntity entity = new UserEntity();
        entity.setName("brock");
        entity.setPassword("hashed2");
        entity.setRole(Role.USER);
        when(userRepository.findByUsername("brock")).thenReturn(entity);

        UserDetails result = service.loadUserByUsername("brock");

        assertThat(result.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void loadUserByUsername_userNotFound_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing");
    }
}
