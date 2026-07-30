package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.request.LocationCheckInRequest;
import com.example.demo.dto.request.PasswordCheckInRequest;
import com.example.demo.dto.request.QRCodeCheckInRequest;
import com.example.demo.dto.response.CheckInResponse;
import com.example.demo.dto.response.CheckInSessionResponse;
import com.example.demo.dto.response.CheckInStatsResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.CheckIn;
import com.example.demo.entity.Registration;
import com.example.demo.entity.User;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.RegistrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckInServiceTest extends ServiceTestSupport {

    private ActivityRepository activityRepository;
    private RegistrationRepository registrationRepository;
    private CheckInRepository checkInRepository;
    private ObjectProvider<StringRedisTemplate> redisProvider;
    private CheckInService service;

    @BeforeEach
    public void setUp() {
        activityRepository = mock(ActivityRepository.class);
        registrationRepository = mock(RegistrationRepository.class);
        checkInRepository = mock(CheckInRepository.class);
        redisProvider = mock(ObjectProvider.class);
        service = new CheckInService(activityRepository, registrationRepository, checkInRepository,
                redisProvider);
    }

    @AfterEach
    public void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void qrSessionAndCheckInCompleteHappyPathAndInvalidateToken() {
        User organizer = login("org", "organizer");
        Activity activity = activity(1, organizer);
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

        CheckInSessionResponse first = service.createQrSession(1L);
        CheckInSessionResponse session = service.createQrSession(1L);
        assertNotEquals(first.getToken(), session.getToken());
        assertTrue(session.getQrContent().startsWith("CHECKIN:1:"));

        prepareApprovedCheckIn(activity, "student");
        login("student", "student");
        QRCodeCheckInRequest request = new QRCodeCheckInRequest();
        request.setActivityId(1L);
        request.setToken(session.getToken());

        CheckInResponse response = service.checkInByQr(request);

        assertEquals("qrcode", response.getMethod());
        verify(activityRepository).incrementCheckInCount(eq(1L), any(LocalDateTime.class));
        assertThrows(BusinessException.class, () -> service.checkInByQr(request));
    }

    @Test
    void qrRejectsWrongActivityAndOrganizerAuthorizationIsEnforced() {
        User organizer = user("org", "organizer");
        Activity activity = activity(2, organizer);
        when(activityRepository.findById(2L)).thenReturn(Optional.of(activity));
        login("other", "organizer");
        assertThrows(BusinessException.class, () -> service.createQrSession(2L));

        login("admin", "admin");
        CheckInSessionResponse session = service.createQrSession(2L);
        QRCodeCheckInRequest request = new QRCodeCheckInRequest();
        request.setActivityId(999L);
        request.setToken(session.getToken());
        assertThrows(BusinessException.class, () -> service.checkInByQr(request));
    }

    @Test
    void passwordSessionAcceptsGeneratedCodeAndRejectsExpiredOrInvalidCode() {
        User organizer = login("org", "organizer");
        Activity activity = activity(3, organizer);
        when(activityRepository.findById(3L)).thenReturn(Optional.of(activity));
        CheckInSessionResponse session = service.createPasswordSession(3L);
        assertEquals(6, session.getCode().length());

        prepareApprovedCheckIn(activity, "student");
        login("student", "student");
        PasswordCheckInRequest valid = passwordRequest(3L, session.getCode());
        assertEquals("password", service.checkInByPassword(valid).getMethod());

        PasswordCheckInRequest invalid = passwordRequest(3L, "abcdef");
        assertThrows(BusinessException.class, () -> service.checkInByPassword(invalid));
        assertThrows(BusinessException.class,
                () -> service.checkInByPassword(passwordRequest(404L, "000000")));
    }

    @Test
    void locationCheckInAcceptsNearPointWithConfiguredAndDefaultRadius() {
        User organizer = user("org", "organizer");
        Activity activity = activity(4, organizer);
        activity.setLatitude(30.0);
        activity.setLongitude(120.0);
        activity.setCheckInRadiusMeters(500);
        when(activityRepository.findById(4L)).thenReturn(Optional.of(activity));
        prepareApprovedCheckIn(activity, "student");
        login("student", "student");

        LocationCheckInRequest request = locationRequest(4L, 30.0001, 120.0001);
        CheckInResponse response = service.checkInByLocation(request);
        assertEquals("location", response.getMethod());
        assertNotNull(response.getDistanceMeters());

        activity.setCheckInRadiusMeters(null);
        assertDoesNotThrow(() -> service.checkInByLocation(request));
    }

    @Test
    void locationRejectsMissingCoordinatesAndDistantPoint() {
        User organizer = user("org", "organizer");
        Activity activity = activity(5, organizer);
        when(activityRepository.findById(5L)).thenReturn(Optional.of(activity));
        login("student", "student");

        assertThrows(BusinessException.class,
                () -> service.checkInByLocation(locationRequest(5L, 30.0, 120.0)));

        activity.setLatitude(30.0);
        activity.setLongitude(120.0);
        activity.setCheckInRadiusMeters(10);
        assertThrows(BusinessException.class,
                () -> service.checkInByLocation(locationRequest(5L, 31.0, 121.0)));
    }

    @Test
    void checkInRejectsUnregisteredPendingDuplicateAndClosedWindow() {
        User organizer = user("org", "organizer");
        Activity activity = activity(6, organizer);
        when(activityRepository.findById(6L)).thenReturn(Optional.of(activity));
        when(activityRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(activity));
        login("student", "student");
        LocationCheckInRequest request = locationRequest(6L, 30.0, 120.0);
        activity.setLatitude(30.0);
        activity.setLongitude(120.0);

        assertThrows(BusinessException.class, () -> service.checkInByLocation(request));

        Registration registration = registration(activity, "student", "pending");
        when(registrationRepository.findByActivityIdAndUserId(6L, "student"))
                .thenReturn(Optional.of(registration));
        assertThrows(BusinessException.class, () -> service.checkInByLocation(request));

        registration.setStatus("approved");
        when(checkInRepository.existsByActivityIdAndUserId(6L, "student")).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.checkInByLocation(request));

        when(checkInRepository.existsByActivityIdAndUserId(6L, "student")).thenReturn(false);
        activity.setStartTime(LocalDateTime.now().plusHours(2));
        activity.setEndTime(LocalDateTime.now().plusHours(3));
        assertThrows(BusinessException.class, () -> service.checkInByLocation(request));
        activity.setStartTime(LocalDateTime.now().minusHours(3));
        activity.setEndTime(LocalDateTime.now().minusHours(2));
        assertThrows(BusinessException.class, () -> service.checkInByLocation(request));
    }

    @Test
    void historyActivityListAndStatsAreMapped() {
        User organizer = user("org", "organizer");
        Activity activity = activity(7, organizer);
        CheckIn checkIn = checkIn(activity, user("student", "student"), "qrcode");
        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));
        when(checkInRepository.findByUserIdOrderByCheckedAtDesc("student")).thenReturn(List.of(checkIn));
        when(checkInRepository.findByActivityIdOrderByCheckedAtDesc(7L)).thenReturn(List.of(checkIn));
        when(registrationRepository.countByActivityIdAndStatus(7L, "approved")).thenReturn(10L);
        when(checkInRepository.countByActivityId(7L)).thenReturn(4L);

        login("student", "student");
        assertEquals(1, service.listMine().size());

        login("org", "organizer");
        assertEquals(1, service.listByActivity(7L).size());
        CheckInStatsResponse stats = service.stats(7L);
        assertEquals(10, stats.getRegisteredCount());
        assertEquals(4, stats.getCheckedInCount());
        assertEquals(6, stats.getUncheckedCount());
        assertEquals(40.0, stats.getCheckInRate());

        when(registrationRepository.countByActivityIdAndStatus(7L, "approved")).thenReturn(0L);
        when(checkInRepository.countByActivityId(7L)).thenReturn(2L);
        CheckInStatsResponse zero = service.stats(7L);
        assertEquals(0, zero.getUncheckedCount());
        assertEquals(0.0, zero.getCheckInRate());
    }

    @Test
    void missingActivityIsRejected() {
        login("admin", "admin");
        assertThrows(BusinessException.class, () -> service.createQrSession(404L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisSuccessAndFailuresFallBackToLocalStore() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(values);
        User organizer = login("org", "organizer");
        Activity activity = activity(8, organizer);
        when(activityRepository.findById(8L)).thenReturn(Optional.of(activity));

        CheckInSessionResponse redisSession = service.createQrSession(8L);
        when(values.get("checkin:qr:token:" + redisSession.getToken())).thenReturn("8");
        prepareApprovedCheckIn(activity, "student");
        login("student", "student");
        QRCodeCheckInRequest request = new QRCodeCheckInRequest();
        request.setActivityId(8L);
        request.setToken(redisSession.getToken());
        assertDoesNotThrow(() -> service.checkInByQr(request));
        verify(redis, atLeastOnce()).delete(anyString());

        doThrow(new IllegalStateException("redis down")).when(values)
                .set(anyString(), anyString(), any(java.time.Duration.class));
        when(values.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        doThrow(new IllegalStateException("redis down")).when(redis).delete(anyString());
        login("org", "organizer");
        CheckInSessionResponse localSession = service.createQrSession(8L);
        assertNotNull(localSession.getToken());
    }

    private void prepareApprovedCheckIn(Activity activity, String userId) {
        when(activityRepository.findByIdForUpdate(activity.getId())).thenReturn(Optional.of(activity));
        Registration registration = registration(activity, userId, "approved");
        when(registrationRepository.findByActivityIdAndUserId(activity.getId(), userId))
                .thenReturn(Optional.of(registration));
        when(checkInRepository.save(any())).thenAnswer(invocation -> {
            CheckIn value = invocation.getArgument(0);
            value.setId(100L);
            return value;
        });
    }

    private Registration registration(Activity activity, String userId, String status) {
        Registration registration = new Registration();
        registration.setActivity(activity);
        registration.setActivityId(activity.getId());
        registration.setUser(user(userId, "student"));
        registration.setUserId(userId);
        registration.setStatus(status);
        registration.setCreatedAt(LocalDateTime.now());
        return registration;
    }

    private CheckIn checkIn(Activity activity, User user, String method) {
        CheckIn checkIn = new CheckIn();
        checkIn.setId(1L);
        checkIn.setActivity(activity);
        checkIn.setActivityId(activity.getId());
        checkIn.setUser(user);
        checkIn.setUserId(user.getId());
        checkIn.setMethod(method);
        checkIn.setCheckedAt(LocalDateTime.now());
        return checkIn;
    }

    private PasswordCheckInRequest passwordRequest(long activityId, String code) {
        PasswordCheckInRequest request = new PasswordCheckInRequest();
        request.setActivityId(activityId);
        request.setCode(code);
        return request;
    }

    private LocationCheckInRequest locationRequest(long activityId, double latitude, double longitude) {
        LocationCheckInRequest request = new LocationCheckInRequest();
        request.setActivityId(activityId);
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        return request;
    }
}
