package com.example.campusactivity.controller;

import com.example.campusactivity.dto.ApiResponse;
import com.example.campusactivity.dto.LoginRequest;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserAccount>> login(@RequestBody LoginRequest request) {
        return userRepository.findByIdAndPassword(request.id(), request.password())
                .map(user -> ResponseEntity.ok(ApiResponse.ok("登录成功", user)))
                .orElseGet(() -> ResponseEntity.badRequest().body(ApiResponse.fail("学号/工号或密码错误")));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserAccount>> register(@Valid @RequestBody UserAccount user) {
        if (userRepository.existsById(user.getId())) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("该学号/工号已注册"));
        }
        return ResponseEntity.ok(ApiResponse.ok("注册成功", userRepository.save(user)));
    }
}
