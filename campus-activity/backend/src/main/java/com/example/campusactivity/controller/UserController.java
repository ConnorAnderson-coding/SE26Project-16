package com.example.campusactivity.controller;

import com.example.campusactivity.dto.user.AdminCreateUserRequest;
import com.example.campusactivity.dto.user.AdminUpdateUserRequest;
import com.example.campusactivity.dto.user.AdminUserResponse;
import com.example.campusactivity.service.auth.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AdminUserService adminUserService;

    public UserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminUserResponse> list() {
        return adminUserService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> get(@PathVariable String id) {
        return adminUserService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public AdminUserResponse create(
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        return adminUserService.create(request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUserResponse> update(
            @PathVariable String id,
            @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        return adminUserService.update(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!adminUserService.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
