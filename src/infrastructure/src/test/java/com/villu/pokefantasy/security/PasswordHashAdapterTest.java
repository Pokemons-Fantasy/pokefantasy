package com.villu.pokefantasy.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordHashAdapterTest {

    @Mock private PasswordEncoder passwordEncoder;

    private PasswordHashAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PasswordHashAdapter(passwordEncoder);
    }

    @Test
    void encode_delegatesToPasswordEncoder() {
        when(passwordEncoder.encode("raw")).thenReturn("encoded");

        assertThat(adapter.encode("raw")).isEqualTo("encoded");
    }

    @Test
    void matches_correctPassword_returnsTrue() {
        when(passwordEncoder.matches("raw", "encoded")).thenReturn(true);

        assertThat(adapter.matches("raw", "encoded")).isTrue();
    }

    @Test
    void matches_incorrectPassword_returnsFalse() {
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThat(adapter.matches("wrong", "encoded")).isFalse();
    }
}
