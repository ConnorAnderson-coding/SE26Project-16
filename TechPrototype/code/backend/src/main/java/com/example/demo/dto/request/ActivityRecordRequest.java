package com.example.demo.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActivityRecordRequest {

    @NotBlank(message = "活动总结不能为空")
    @Size(max = 5000)
    private String summary;

    private List<String> photos;
}
