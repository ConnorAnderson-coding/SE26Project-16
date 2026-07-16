package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PasswordCheckInRequest {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    @NotBlank(message = "动态口令不能为空")
    @Pattern(regexp = "\\d{6}", message = "动态口令必须为6位数字")
    private String code;
}
