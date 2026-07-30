package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.dto.response.FavoriteStatusResponse;
import com.example.demo.dto.response.FavoriteToggleResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.Favorite;
import com.example.demo.entity.FavoriteId;
import com.example.demo.entity.User;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.FavoriteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FavoriteServiceTest extends ServiceTestSupport {

    private FavoriteRepository favoriteRepository;
    private ActivityRepository activityRepository;
    private UserService userService;
    private ActivityHotnessService activityHotnessService;
    private FavoriteService service;

    @BeforeEach
    void setUp() {
        favoriteRepository = mock(FavoriteRepository.class);
        activityRepository = mock(ActivityRepository.class);
        userService = mock(UserService.class);
        activityHotnessService = mock(ActivityHotnessService.class);
        service = new FavoriteService(favoriteRepository, activityRepository,
                userService, activityHotnessService);
    }

    @AfterEach
    void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ───── getMine() ─────

    @Test
    void getMineReturnsFavoritesForCurrentUser() {
        User user = login("user1", "student");
        Activity activity = activity(1L, user);
        Favorite fav = new Favorite();
        fav.setId(new FavoriteId("user1", 1L));
        fav.setActivity(activity);
        fav.setUser(user);

        when(favoriteRepository.findByIdUserIdOrderByCreatedAtDesc("user1"))
                .thenReturn(List.of(fav));

        List<ActivityResponse> result = service.getMine();

        assertEquals(1, result.size());
        assertEquals("Activity 1", result.get(0).getTitle());
    }

    @Test
    void getMineReturnsEmptyWhenNoFavorites() {
        login("user1", "student");
        when(favoriteRepository.findByIdUserIdOrderByCreatedAtDesc("user1"))
                .thenReturn(List.of());

        List<ActivityResponse> result = service.getMine();

        assertTrue(result.isEmpty());
    }

    // ───── toggle() — favorite ─────

    @Test
    void toggleFavoriteCreatesFavorite() {
        User user = login("user1", "student");
        Activity activity = activity(1L, user);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(favoriteRepository.existsById(new FavoriteId("user1", 1L))).thenReturn(false);
        when(userService.getUserEntity("user1")).thenReturn(user);

        FavoriteToggleResponse response = service.toggle(1L);

        assertTrue(response.isFavorited());
        verify(favoriteRepository).save(any(Favorite.class));
        verify(activityRepository).incrementFavoriteCount(eq(1L), any(LocalDateTime.class));
        verify(activityHotnessService).recalculate(activity);
    }

    // ───── toggle() — unfavorite ─────

    @Test
    void toggleFavoriteRemovesFavorite() {
        User user = login("user1", "student");
        Activity activity = activity(1L, user);
        activity.setFavoriteCount(5);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(favoriteRepository.existsById(new FavoriteId("user1", 1L))).thenReturn(true);

        FavoriteToggleResponse response = service.toggle(1L);

        assertFalse(response.isFavorited());
        verify(favoriteRepository).deleteById(new FavoriteId("user1", 1L));
        verify(activityRepository).decrementFavoriteCount(eq(1L), any(LocalDateTime.class));
        verify(activityHotnessService).recalculate(activity);
    }

    @Test
    void toggleFavoriteClampsFavoriteCountAtZero() {
        User user = login("user1", "student");
        Activity activity = activity(1L, user);
        activity.setFavoriteCount(0); // 已经是0
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(favoriteRepository.existsById(new FavoriteId("user1", 1L))).thenReturn(true);

        service.toggle(1L);

        verify(activityRepository).decrementFavoriteCount(eq(1L), any(LocalDateTime.class));
    }

    // ───── toggle() — error paths ─────

    @Test
    void toggleFavoriteThrowsWhenActivityNotFound() {
        login("user1", "student");
        when(activityRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.toggle(99L));
        assertEquals("活动不存在", ex.getMessage());
    }

    @Test
    void toggleFavoriteThrowsWhenActivityEnded() {
        login("user1", "student");
        Activity activity = activity(1L, user("org", "organizer"));
        activity.setStatus("ended");
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.toggle(1L));
        assertEquals("活动已结束，无法修改收藏状态", ex.getMessage());
        verify(favoriteRepository, never()).existsById(any());
    }

    // ───── getStatus() ─────

    @Test
    void getStatusReturnsFavoritedWhenExists() {
        login("user1", "student");
        when(favoriteRepository.existsByIdUserIdAndIdActivityId("user1", 1L)).thenReturn(true);

        FavoriteStatusResponse response = service.getStatus(1L);

        assertTrue(response.isFavorited());
    }

    @Test
    void getStatusReturnsNotFavoritedWhenNotExists() {
        login("user1", "student");
        when(favoriteRepository.existsByIdUserIdAndIdActivityId("user1", 1L)).thenReturn(false);

        FavoriteStatusResponse response = service.getStatus(1L);

        assertFalse(response.isFavorited());
    }
}
