package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserResponse {
    private String id;
    private String name;
    private String role;
    private String college;
    private String grade;
    private List<String> interests;
    private List<String> availableTime;
}
