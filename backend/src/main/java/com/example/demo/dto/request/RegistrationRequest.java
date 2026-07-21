package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrationRequest {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;
}
