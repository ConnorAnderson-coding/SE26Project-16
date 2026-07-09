package com.example.demo.cache;

import com.example.demo.common.CacheNames;
import com.example.demo.dto.request.ActivityRequest;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegistrationRequest;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ActivityService;
import com.example.demo.service.AuthService;
import com.example.demo.service.RegistrationService;
import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@AutoConfigureMockMvc
class ActivityCacheIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ActivityService activityService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private CacheManager cacheManager;

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
    }

    @AfterEach
    void tearDown() {
        clearSecurityContext();
    }

    @Test
    void findWithDetailsByIdShouldPopulateCache() {
        activityRepository.findWithDetailsById(scenario.activity().getId());

        Cache cache = cacheManager.getCache(CacheNames.ACTIVITY_DETAIL);
        assertNotNull(cache);
        assertNotNull(cache.get(scenario.activity().getId()));
    }

    @Test
    void updateActivityShouldEvictDetailCache() {
        Long activityId = scenario.activity().getId();
        activityRepository.findWithDetailsById(activityId);

        Cache cache = cacheManager.getCache(CacheNames.ACTIVITY_DETAIL);
        assertNotNull(cache);
        assertNotNull(cache.get(activityId));

        loginAs(scenario.organizer());
        ActivityRequest request = buildActivityRequest("更新后的标题");
        activityService.update(activityId, request);

        assertNull(cache.get(activityId));
        assertEquals("更新后的标题", activityService.getById(activityId).getTitle());
    }

    @Test
    void cachedUserReadShouldNotBreakLogin() {
        userRepository.findCachedById(scenario.student().getId());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUserId(scenario.student().getId());
        loginRequest.setPassword(PASSWORD);

        var response = authService.login(loginRequest);
        assertNotNull(response.getToken());
        assertEquals(scenario.student().getId(), response.getUser().getId());
    }

    @Test
    void getByIdShouldReturnOrganizerAfterCacheHit() {
        activityRepository.findWithDetailsById(scenario.activity().getId());
        var response = activityService.getById(scenario.activity().getId());

        assertEquals(scenario.organizer().getId(), response.getOrganizerId());
        assertEquals(scenario.organizer().getName(), response.getOrganizerName());
    }

    @Test
    void signupShouldEvictActivityDetailCache() {
        Long activityId = scenario.activity().getId();
        activityRepository.findWithDetailsById(activityId);
        Cache cache = cacheManager.getCache(CacheNames.ACTIVITY_DETAIL);
        assertNotNull(cache.get(activityId));

        loginAs(scenario.student());
        RegistrationRequest request = new RegistrationRequest();
        request.setActivityId(activityId);
        registrationService.signup(request);

        assertNull(cache.get(activityId));
    }
}
