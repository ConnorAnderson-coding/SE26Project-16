package com.example.campusactivity.service.auth;

import com.example.campusactivity.dto.auth.RegistrationRequest;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.security.RoleAuthorityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    @Test
    void concurrentUniqueConstraintCollisionBecomesAccountAlreadyExists() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userRepository.existsById("new-account")).thenReturn(false);
        when(passwordEncoder.encode("StrongPassword123!"))
                .thenReturn("{bcrypt}encoded");
        when(userRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("constraint"));
        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                new RoleAuthorityMapper()
        );
        RegistrationRequest request = new RegistrationRequest(
                "new-account",
                "StrongPassword123!",
                "New User",
                "Software",
                "2026",
                List.of("AI"),
                List.of("weekend")
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AccountAlreadyExistsException.class);
    }
}
