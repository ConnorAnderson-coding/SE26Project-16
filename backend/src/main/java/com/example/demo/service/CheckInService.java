package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CacheNames;
import com.example.demo.dto.DtoMapper;
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
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckInService {

    private static final int EXPIRES_SECONDS = 30;
    private static final Duration TOKEN_TTL = Duration.ofSeconds(EXPIRES_SECONDS);
    private static final Duration TOTP_SECRET_TTL = Duration.ofSeconds(EXPIRES_SECONDS * 3L);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final String METHOD_QRCODE = "qrcode";
    private static final String METHOD_LOCATION = "location";
    private static final String METHOD_PASSWORD = "password";

    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;
    private final CheckInRepository checkInRepository;
    private final UserService userService;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, TimedValue> localStore = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public CheckInSessionResponse createQrSession(Long activityId) {
        Activity activity = getActivity(activityId);
        assertOrganizer(activity);

        String oldToken = getValue(qrActivityKey(activityId));
        if (oldToken != null) {
            deleteValue(qrTokenKey(oldToken));
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime issuedAt = LocalDateTime.now();
        String qrContent = "CHECKIN:" + activityId + ":" + token + ":" + Instant.now().toEpochMilli();
        putValue(qrActivityKey(activityId), token, TOKEN_TTL);
        putValue(qrTokenKey(token), String.valueOf(activityId), TOKEN_TTL);

        return CheckInSessionResponse.builder()
                .activityId(activityId)
                .token(token)
                .qrContent(qrContent)
                .expiresInSeconds(EXPIRES_SECONDS)
                .issuedAt(issuedAt)
                .build();
    }

    @Transactional
    @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#request.activityId")
    public CheckInResponse checkInByQr(QRCodeCheckInRequest request) {
        String activityId = getValue(qrTokenKey(request.getToken()));
        if (!String.valueOf(request.getActivityId()).equals(activityId)) {
            throw new BusinessException("二维码已过期或无效");
        }

        CheckIn checkIn = createCheckIn(request.getActivityId(), METHOD_QRCODE, null, null, null);
        deleteValue(qrTokenKey(request.getToken()));
        deleteValue(qrActivityKey(request.getActivityId()));
        return DtoMapper.toCheckInResponse(checkIn);
    }

    @Transactional(readOnly = true)
    public CheckInSessionResponse createPasswordSession(Long activityId) {
        Activity activity = getActivity(activityId);
        assertOrganizer(activity);

        byte[] secret = new byte[20];
        secureRandom.nextBytes(secret);
        String encodedSecret = Base64.getEncoder().encodeToString(secret);
        putValue(passwordKey(activityId), encodedSecret, TOTP_SECRET_TTL);

        return CheckInSessionResponse.builder()
                .activityId(activityId)
                .code(generateTotp(secret, currentTimeStep()))
                .expiresInSeconds(EXPIRES_SECONDS)
                .issuedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#request.activityId")
    public CheckInResponse checkInByPassword(PasswordCheckInRequest request) {
        String encodedSecret = getValue(passwordKey(request.getActivityId()));
        if (encodedSecret == null) {
            throw new BusinessException("动态口令已过期");
        }
        byte[] secret = Base64.getDecoder().decode(encodedSecret);
        long step = currentTimeStep();
        if (!request.getCode().equals(generateTotp(secret, step - 1))
                && !request.getCode().equals(generateTotp(secret, step))
                && !request.getCode().equals(generateTotp(secret, step + 1))) {
            throw new BusinessException("动态口令错误");
        }
        return DtoMapper.toCheckInResponse(createCheckIn(request.getActivityId(), METHOD_PASSWORD, null, null, null));
    }

    @Transactional
    @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#request.activityId")
    public CheckInResponse checkInByLocation(LocationCheckInRequest request) {
        Activity activity = getActivity(request.getActivityId());
        if (activity.getLatitude() == null || activity.getLongitude() == null) {
            throw new BusinessException("该活动未配置签到地点");
        }
        double distance = haversineMeters(
                activity.getLatitude(),
                activity.getLongitude(),
                request.getLatitude(),
                request.getLongitude());
        int radius = activity.getCheckInRadiusMeters() != null ? activity.getCheckInRadiusMeters() : 200;
        if (distance > radius) {
            throw new BusinessException("当前位置不在签到范围内");
        }
        return DtoMapper.toCheckInResponse(createCheckIn(
                activity,
                METHOD_LOCATION,
                request.getLatitude(),
                request.getLongitude(),
                distance));
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> listMine() {
        String userId = SecurityUtils.getCurrentUserId();
        return checkInRepository.findByUserIdOrderByCheckedAtDesc(userId).stream()
                .map(DtoMapper::toCheckInResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> listByActivity(Long activityId) {
        Activity activity = getActivity(activityId);
        assertOrganizer(activity);
        return checkInRepository.findByActivityIdOrderByCheckedAtDesc(activityId).stream()
                .map(DtoMapper::toCheckInResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CheckInStatsResponse stats(Long activityId) {
        Activity activity = getActivity(activityId);
        assertOrganizer(activity);
        List<CheckInResponse> records = checkInRepository.findByActivityIdOrderByCheckedAtDesc(activityId).stream()
                .map(DtoMapper::toCheckInResponse)
                .collect(Collectors.toList());
        long registeredCount = registrationRepository.countByActivityIdAndStatus(activityId, "approved");
        long checkedInCount = checkInRepository.countByActivityId(activityId);
        long uncheckedCount = Math.max(0, registeredCount - checkedInCount);
        double rate = registeredCount == 0 ? 0.0 : checkedInCount * 100.0 / registeredCount;

        return CheckInStatsResponse.builder()
                .activityId(activityId)
                .registeredCount(registeredCount)
                .checkedInCount(checkedInCount)
                .uncheckedCount(uncheckedCount)
                .checkInRate(rate)
                .records(records)
                .build();
    }

    private CheckIn createCheckIn(Long activityId, String method, Double latitude, Double longitude, Double distanceMeters) {
        return createCheckIn(getActivity(activityId), method, latitude, longitude, distanceMeters);
    }

    private CheckIn createCheckIn(Activity activity, String method, Double latitude, Double longitude, Double distanceMeters) {
        String userId = SecurityUtils.getCurrentUserId();
        Registration registration = registrationRepository.findByActivityIdAndUserId(activity.getId(), userId)
                .orElseThrow(() -> new BusinessException("未报名该活动，不能签到"));
        if (!"approved".equals(registration.getStatus())) {
            throw new BusinessException("报名尚未通过审核，不能签到");
        }
        if (checkInRepository.existsByActivityIdAndUserId(activity.getId(), userId)) {
            throw new BusinessException("您已完成签到");
        }
        assertCheckInWindow(activity);

        User user = userService.getUserEntity(userId);
        CheckIn checkIn = new CheckIn();
        checkIn.setActivity(activity);
        checkIn.setUser(user);
        checkIn.setMethod(method);
        checkIn.setCheckedAt(LocalDateTime.now());
        checkIn.setLatitude(latitude);
        checkIn.setLongitude(longitude);
        checkIn.setDistanceMeters(distanceMeters);
        checkIn = checkInRepository.save(checkIn);

        int current = activity.getCheckInCount() != null ? activity.getCheckInCount() : 0;
        activity.setCheckInCount(current + 1);
        activity.setUpdatedAt(LocalDateTime.now());
        activityRepository.save(activity);

        return checkIn;
    }

    private Activity getActivity(Long activityId) {
        return activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在"));
    }

    private void assertOrganizer(Activity activity) {
        String userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUser().getUser().getRole();
        if (!"admin".equals(role) && !activity.getOrganizer().getId().equals(userId)) {
            throw new BusinessException(403, "无权管理该活动签到");
        }
    }

    private void assertCheckInWindow(Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime opensAt = activity.getStartTime().minusMinutes(30);
        LocalDateTime closesAt = activity.getEndTime().plusMinutes(30);
        if (now.isBefore(opensAt) || now.isAfter(closesAt)) {
            throw new BusinessException("当前不在签到时间窗口内");
        }
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private long currentTimeStep() {
        return Instant.now().getEpochSecond() / EXPIRES_SECONDS;
    }

    private String generateTotp(byte[] secret, long timeStep) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception ex) {
            throw new IllegalStateException("生成动态口令失败", ex);
        }
    }

    private String qrActivityKey(Long activityId) {
        return "checkin:qr:activity:" + activityId;
    }

    private String qrTokenKey(String token) {
        return "checkin:qr:token:" + token;
    }

    private String passwordKey(Long activityId) {
        return "checkin:password:" + activityId;
    }

    private void putValue(String key, String value, Duration ttl) {
        localStore.put(key, new TimedValue(value, Instant.now().plus(ttl)));
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, value, ttl);
            } catch (RuntimeException ignored) {
                // Local/test environments may run without Redis; the in-memory store above keeps the feature usable.
            }
        }
    }

    private String getValue(String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    return value;
                }
            } catch (RuntimeException ignored) {
                // Fall back to local store when Redis is unavailable.
            }
        }

        TimedValue local = localStore.get(key);
        if (local == null) {
            return null;
        }
        if (local.expiresAt().isBefore(Instant.now())) {
            localStore.remove(key);
            return null;
        }
        return local.value();
    }

    private void deleteValue(String key) {
        localStore.remove(key);
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (RuntimeException ignored) {
                // Fall back to local store when Redis is unavailable.
            }
        }
    }

    private record TimedValue(String value, Instant expiresAt) {
    }
}
