package com.example.campusactivity.service.auth;

import com.example.campusactivity.dto.auth.AuthenticatedUserResponse;
import com.example.campusactivity.dto.auth.RegistrationRequest;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.security.RoleAuthorityMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleAuthorityMapper roleAuthorityMapper;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RoleAuthorityMapper roleAuthorityMapper
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleAuthorityMapper = roleAuthorityMapper;
    }

    @Transactional
    public AuthenticatedUserResponse register(RegistrationRequest request) {
        if (userRepository.existsById(request.id())) {
            throw new AccountAlreadyExistsException();
        }

        UserAccount user = new UserAccount();
        user.setId(request.id());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setRole("student");
        user.setCollege(request.college());
        user.setGrade(request.grade());
        user.setInterests(request.interests());
        user.setAvailableTime(request.availableTime());
        user.setFriends(new ArrayList<>());

        try {
            return toAuthenticatedUser(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException _exception) {
            throw new AccountAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedUserResponse> findAuthenticatedUser(
            String accountId
    ) {
        return userRepository.findById(accountId)
                .flatMap(this::toAuthenticatedUserIfValid);
    }

    private Optional<AuthenticatedUserResponse> toAuthenticatedUserIfValid(
            UserAccount user
    ) {
        try {
            return Optional.of(toAuthenticatedUser(user));
        } catch (IllegalArgumentException _exception) {
            return Optional.empty();
        }
    }

    private AuthenticatedUserResponse toAuthenticatedUser(UserAccount user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getName(),
                roleAuthorityMapper.normalize(user.getRole()),
                user.getCollege(),
                user.getGrade(),
                user.getInterests(),
                user.getAvailableTime()
        );
    }
}
