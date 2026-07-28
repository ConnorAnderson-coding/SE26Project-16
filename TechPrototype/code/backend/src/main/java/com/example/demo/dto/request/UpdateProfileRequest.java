package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64)
    private String name;

    @NotBlank(message = "学院不能为空")
    @Size(max = 64)
    private String college;

    @NotBlank(message = "年级不能为空")
    @Size(max = 32)
    private String grade;

    private List<String> interests;

    private List<String> availableTime;
}
