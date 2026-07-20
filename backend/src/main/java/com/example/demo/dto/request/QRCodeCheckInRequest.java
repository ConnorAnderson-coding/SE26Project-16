package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QRCodeCheckInRequest {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    @NotBlank(message = "签到token不能为空")
    private String token;
}
