package com.example.campusactivity.service.auth;

import com.example.campusactivity.dto.user.AdminCreateUserRequest;
import com.example.campusactivity.dto.user.AdminUpdateUserRequest;
import com.example.campusactivity.dto.user.AdminUserResponse;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.security.RoleAuthorityMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AdminUserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleAuthorityMapper roleAuthorityMapper;

    public AdminUserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RoleAuthorityMapper roleAuthorityMapper
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleAuthorityMapper = roleAuthorityMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AdminUserResponse> findById(String id) {
        return userRepository.findById(id).map(this::toResponse);
    }

    @Transactional
    public AdminUserResponse create(AdminCreateUserRequest request) {
        if (userRepository.existsById(request.id())) {
            throw new AccountAlreadyExistsException();
        }

        final String normalizedRole;
        try {
            normalizedRole = roleAuthorityMapper.normalize(request.role());
        } catch (IllegalArgumentException _exception) {
            throw new InvalidUserRequestException();
        }

        UserAccount user = new UserAccount();
        user.setId(request.id());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setRole(normalizedRole);
        user.setCollege(request.college());
        user.setGrade(request.grade());
        user.setInterests(request.interests());
        user.setAvailableTime(request.availableTime());
        user.setFriends(new ArrayList<>());

        try {
            return toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException _exception) {
            throw new AccountAlreadyExistsException();
        }
    }

    @Transactional
    public Optional<AdminUserResponse> update(
            String id,
            AdminUpdateUserRequest request
    ) {
        return userRepository.findById(id).map(user -> {
            user.setName(request.name());
            user.setCollege(request.college());
            user.setGrade(request.grade());
            user.setInterests(request.interests());
            user.setAvailableTime(request.availableTime());
            return toResponse(userRepository.save(user));
        });
    }

    @Transactional
    public boolean delete(String id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        userRepository.deleteById(id);
        return true;
    }

    private AdminUserResponse toResponse(UserAccount user) {
        final String normalizedRole;
        try {
            normalizedRole = roleAuthorityMapper.normalize(user.getRole());
        } catch (IllegalArgumentException _exception) {
            throw new InvalidUserRequestException();
        }
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                normalizedRole,
                user.getCollege(),
                user.getGrade(),
                user.getInterests(),
                user.getAvailableTime()
        );
    }
}
