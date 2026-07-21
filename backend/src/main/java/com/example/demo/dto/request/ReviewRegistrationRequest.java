package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewRegistrationRequest {

    @NotNull(message = "审核结果不能为空")
    private Boolean approved;
}
