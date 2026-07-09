package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RegisterRequest {

    @NotBlank(message = "学号/工号不能为空")
    @Size(max = 32, message = "学号/工号长度不能超过32")
    private String id;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6-64之间")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过64")
    private String name;

    @Size(max = 16)
    private String role = "student";

    @NotBlank(message = "学院不能为空")
    @Size(max = 64)
    private String college;

    @NotBlank(message = "年级不能为空")
    @Size(max = 32)
    private String grade;

    private List<String> interests;

    private List<String> availableTime;
}
