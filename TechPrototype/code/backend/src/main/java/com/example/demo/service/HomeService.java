package com.example.demo.service;

import com.example.demo.dto.response.HomeStatsResponse;
import com.example.demo.repository.FavoriteRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final RegistrationRepository registrationRepository;
    private final FavoriteRepository favoriteRepository;

    @Transactional(readOnly = true)
    public HomeStatsResponse getStats() {
        String userId = SecurityUtils.getCurrentUserId();
        return HomeStatsResponse.builder()
                .mySignupCount(registrationRepository.countByUserId(userId))
                .approvedCount(registrationRepository.countByUserIdAndStatus(userId, "approved"))
                .myFavoriteCount(favoriteRepository.countByIdUserId(userId))
                .build();
    }
}
