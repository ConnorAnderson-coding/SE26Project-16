package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ActivityRecordRequest {

    @NotBlank(message = "活动总结不能为空")
    @Size(max = 5000)
    private String summary;

    private List<String> photos;
}
