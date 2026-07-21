package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FavoriteToggleResponse {
    private boolean favorited;
}
