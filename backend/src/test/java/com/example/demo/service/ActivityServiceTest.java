package com.example.demo.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.demo.common.BusinessException;
import com.example.demo.common.PageResult;
import com.example.demo.dto.request.ActivityRequest;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.Registration;
import com.example.demo.entity.User;
import com.example.demo.recommend.RecommendationService;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.search.ActivityIndexService;
import com.example.demo.search.service.ActivitySearchService;

class ActivityServiceTest extends ServiceTestSupport {

    private ActivityRepository activityRepository;
    private UserService userService;
    private RegistrationRepository registrationRepository;
    private ActivityViewService activityViewService;
    private ObjectProvider<ActivityIndexService> indexProvider;
    private ObjectProvider<ActivitySearchService> searchProvider;
    private ObjectProvider<RecommendationService> recommendationProvider;
    private ActivityHotnessService hotnessService;
    private ActivityService service;

    @BeforeEach
    public void setUp() {
        activityRepository = mock(ActivityRepository.class);
        userService = mock(UserService.class);
        registrationRepository = mock(RegistrationRepository.class);
        activityViewService = mock(ActivityViewService.class);
        indexProvider = mock(ObjectProvider.class);
        searchProvider = mock(ObjectProvider.class);
        recommendationProvider = mock(ObjectProvider.class);
        hotnessService = mock(ActivityHotnessService.class);
        service = new ActivityService(activityRepository, userService, registrationRepository,
                activityViewService, indexProvider, searchProvider, recommendationProvider, hotnessService);
    }

    @AfterEach
    public void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listUsesMysqlAndMapsAllSortModes() {
        User organizer = user("org", "organizer");
        Activity activity = activity(1, organizer);
        when(activityRepository.search(any(), any(), any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(activity), invocation.getArgument(4), 1));

        for (String sort : new String[]{null, "", "hot", "relevance", "composite", "time", "signup", "title,asc", "title,desc"}) {
            PageResult<ActivityResponse> result = service.list("", "", "", "", 0, 10, sort, .5);
            assertEquals(1, result.getContent().size());
            assertEquals("Activity 1", result.getContent().getFirst().getTitle());
        }

        verify(activityRepository, times(9)).search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void listUsesSearchWhenIndexContainsDocumentsAndFallsBackWhenEmpty() {
        ActivitySearchService search = mock(ActivitySearchService.class);
        PageResult<ActivityResponse> expected = new PageResult<>(List.of(), 0, 10, 0, 0);
        when(searchProvider.getIfAvailable()).thenReturn(search);
        when(search.isIndexEmpty()).thenReturn(false);
        when(search.search(any())).thenReturn(expected);

        assertSame(expected, service.list("academic", "published", "Room", "robot", 1, 5, "time", .7));
        verify(search).search(any());
        verifyNoInteractions(activityRepository);

        reset(search, activityRepository);
        when(searchProvider.getIfAvailable()).thenReturn(search);
        when(search.isIndexEmpty()).thenReturn(true);
        when(activityRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.list(null, null, null, "robot", 0, 10, null, .5);
        verify(activityRepository).search(isNull(), isNull(), isNull(), eq("robot"), any());
    }

    @Test
    void getByIdRecordsAuthenticatedViewAndHandlesMissingOrAnonymousUser() {
        Activity value = activity(2, user("org", "organizer"));
        when(activityRepository.findWithDetailsById(2L)).thenReturn(Optional.of(value));
        login("student", "student");

        assertEquals(2L, service.getById(2L).getId());
        verify(activityViewService).recordUniqueViewAsync(2L, "student");

        SecurityContextHolder.clearContext();
        assertEquals(2L, service.getById(2L).getId());
        assertThrows(BusinessException.class, () -> service.getById(99L));
    }

    @Test
    void getMineMapsOrganizerActivities() {
        login("org", "organizer");
        when(activityRepository.findByOrganizerIdOrderByStartTimeDesc("org"))
                .thenReturn(List.of(activity(1, user("org", "organizer"))));

        assertEquals(1, service.getMine().size());
    }

    @Test
    void smartRecommendationIsPreferredAndFailureFallsBackToLegacy() {
        login("student", "student");
        RecommendationService smart = mock(RecommendationService.class);
        ActivityResponse smartResult = ActivityResponse.builder().id(10L).build();
        when(recommendationProvider.getIfAvailable()).thenReturn(smart);
        when(smart.recommend(3)).thenReturn(List.of(smartResult));
        assertEquals(10L, service.getRecommended(3).getFirst().getId());

        when(smart.recommend(3)).thenThrow(new IllegalStateException("search offline"));
        User current = user("student", "student");
        current.setInterests(List.of("robot", "academic"));
        when(userService.getUserEntity("student")).thenReturn(current);
        when(registrationRepository.findByUserIdOrderByCreatedAtDesc("student")).thenReturn(List.of());
        Activity fallback = activity(11, user("org", "organizer"));
        fallback.setTags(List.of("robot"));
        fallback.setHotnessScore(2.0);
        when(activityRepository.findPublishedByHot(any())).thenReturn(List.of(fallback));

        ActivityResponse response = service.getRecommended(3).getFirst();
        assertEquals(11L, response.getId());
        assertTrue(response.getRecommendScore() > 0);
        assertEquals(2, response.getRecommendReasons().size());
    }

    @Test
    void legacyRecommendationExcludesSignedUpAndCoversColdAndNullValues() {
        login("student", "student");
        User current = user("student", "student");
        current.setInterests(null);
        when(userService.getUserEntity("student")).thenReturn(current);

        Activity signed = activity(1, user("org", "organizer"));
        Registration registration = new Registration();
        registration.setActivity(signed);
        when(registrationRepository.findByUserIdOrderByCreatedAtDesc("student")).thenReturn(List.of(registration));

        Activity cold = activity(2, user("org", "organizer"));
        cold.setTags(null);
        cold.setHotnessScore(0.0);
        Activity capped = activity(3, user("org", "organizer"));
        capped.setHotnessScore(99.0);
        when(activityRepository.findPublishedByHot(any())).thenReturn(List.of(signed, cold, capped));

        List<ActivityResponse> result = service.getRecommendedLegacy(10);
        assertEquals(List.of(3L, 2L), result.stream().map(ActivityResponse::getId).toList());
        assertFalse(result.getFirst().getRecommendReasons().isEmpty());
        assertEquals(0, result.getLast().getRecommendScore());
        assertEquals(1, result.getLast().getRecommendReasons().size());
    }

    @Test
    void createPopulatesDefaultsRecalculatesHotnessAndIndexes() {
        User organizer = login("org", "organizer");
        when(userService.getUserEntity("org")).thenReturn(organizer);
        ActivityIndexService index = mock(ActivityIndexService.class);
        invokeProviderConsumer(indexProvider, index);
        ActivityRequest request = request();
        request.setTags(null);
        request.setCheckInRadiusMeters(null);
        when(activityRepository.save(any())).thenAnswer(invocation -> {
            Activity saved = invocation.getArgument(0);
            saved.setId(21L);
            return saved;
        });

        ActivityResponse response = service.create(request);

        assertEquals(21L, response.getId());
        assertEquals("published", response.getStatus());
        assertEquals(200, response.getCheckInRadiusMeters());
        assertNotNull(response.getCheckInCode());
        verify(hotnessService).recalculate(any(Activity.class));
        verify(index).indexActivity(any(Activity.class));
    }

    @Test
    void createRejectsReversedTimeRange() {
        login("org", "organizer");
        when(userService.getUserEntity("org")).thenReturn(user("org", "organizer"));
        ActivityRequest request = request();
        request.setEndTime(request.getStartTime().minusMinutes(1));

        assertThrows(BusinessException.class, () -> service.create(request));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void updateAllowsOwnerAndAdminButRejectsOtherUser() {
        User owner = user("org", "organizer");
        Activity existing = activity(31, owner);
        ActivityIndexService index = mock(ActivityIndexService.class);
        invokeProviderConsumer(indexProvider, index);
        when(activityRepository.findById(31L)).thenReturn(Optional.of(existing));
        login("org", "organizer");

        ActivityRequest request = request();
        request.setTitle("Updated");
        request.setTags(List.of("new"));
        request.setCheckInRadiusMeters(80);
        assertEquals("Updated", service.update(31L, request).getTitle());
        verify(activityRepository).save(existing);
        verify(index).indexActivity(existing);

        login("admin", "admin");
        assertDoesNotThrow(() -> service.update(31L, request));

        login("other", "organizer");
        assertThrows(BusinessException.class, () -> service.update(31L, request));
    }

    @Test
    void deleteChecksOwnershipAndRemovesSearchDocument() {
        User owner = login("org", "organizer");
        Activity existing = activity(41, owner);
        when(activityRepository.findById(41L)).thenReturn(Optional.of(existing));
        ActivityIndexService index = mock(ActivityIndexService.class);
        invokeProviderConsumer(indexProvider, index);

        service.delete(41L);

        verify(activityRepository).delete(existing);
        verify(index).deleteActivity(41L);
        assertThrows(BusinessException.class, () -> service.delete(404L));
        assertThrows(BusinessException.class, () -> service.getActivityEntity(404L));
    }

    @Test
    void getActivityEntityReturnsEntity() {
        Activity existing = activity(51, user("org", "organizer"));
        when(activityRepository.findById(51L)).thenReturn(Optional.of(existing));
        assertSame(existing, service.getActivityEntity(51L));
    }

    private ActivityRequest request() {
        ActivityRequest request = new ActivityRequest();
        request.setTitle("New activity");
        request.setCategory("academic");
        request.setDescription("Details");
        request.setStartTime(LocalDateTime.now().plusHours(1));
        request.setEndTime(LocalDateTime.now().plusHours(2));
        request.setLocation("Hall");
        request.setMaxParticipants(50);
        request.setPoster("poster.png");
        request.setLatitude(30.1);
        request.setLongitude(120.2);
        request.setCheckInRadiusMeters(100);
        request.setTags(List.of("robot"));
        return request;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void invokeProviderConsumer(ObjectProvider<T> provider, T value) {
        doAnswer(invocation -> {
            ((Consumer) invocation.getArgument(0)).accept(value);
            return null;
        }).when(provider).ifAvailable(any());
    }
}
