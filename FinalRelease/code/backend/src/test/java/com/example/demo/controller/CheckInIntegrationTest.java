package com.example.demo.controller;

import com.example.demo.dto.request.LocationCheckInRequest;
import com.example.demo.dto.request.PasswordCheckInRequest;
import com.example.demo.dto.request.QRCodeCheckInRequest;
import com.example.demo.dto.request.RegistrationRequest;
import com.example.demo.dto.request.ReviewRegistrationRequest;
import com.example.demo.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CheckInIntegrationTest extends IntegrationTestSupport {

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
        makeActivityOpen(scenario.activity().getId());
    }

    @Test
    void qrCodeCheckInShouldCreateRecordAndRejectReuse() throws Exception {
        approveStudentRegistration();

        JsonNode session = parseResponse(authPost(
                scenario.organizerToken(),
                "/api/v1/checkins/qrcode/session",
                Map.of("activityId", scenario.activity().getId()))
                .andExpect(status().isOk()));

        QRCodeCheckInRequest request = new QRCodeCheckInRequest();
        request.setActivityId(scenario.activity().getId());
        request.setToken(session.path("data").path("token").asText());

        authPost(scenario.studentToken(), "/api/v1/checkins/qrcode", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("qrcode"))
                .andExpect(jsonPath("$.data.userId").value(scenario.student().getId()));

        authPost(scenario.studentToken(), "/api/v1/checkins/qrcode", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    void passwordCheckInShouldAcceptGeneratedTotpCode() throws Exception {
        approveStudentRegistration();

        JsonNode session = parseResponse(authPost(
                scenario.organizerToken(),
                "/api/v1/checkins/password/session",
                Map.of("activityId", scenario.activity().getId()))
                .andExpect(status().isOk()));

        PasswordCheckInRequest request = new PasswordCheckInRequest();
        request.setActivityId(scenario.activity().getId());
        request.setCode(session.path("data").path("code").asText());

        authPost(scenario.studentToken(), "/api/v1/checkins/password", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("password"));
    }

    @Test
    void locationCheckInShouldValidateDistance() throws Exception {
        approveStudentRegistration();

        LocationCheckInRequest farRequest = new LocationCheckInRequest();
        farRequest.setActivityId(scenario.activity().getId());
        farRequest.setLatitude(31.2304);
        farRequest.setLongitude(121.4737);

        authPost(scenario.studentToken(), "/api/v1/checkins/location", farRequest)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前位置不在签到范围内"));

        LocationCheckInRequest nearRequest = new LocationCheckInRequest();
        nearRequest.setActivityId(scenario.activity().getId());
        nearRequest.setLatitude(31.02521);
        nearRequest.setLongitude(121.43371);

        authPost(scenario.studentToken(), "/api/v1/checkins/location", nearRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("location"))
                .andExpect(jsonPath("$.data.distanceMeters").isNumber());
    }

    @Test
    void organizerStatsShouldReturnCountsAndRecords() throws Exception {
        approveStudentRegistration();
        JsonNode session = parseResponse(authPost(
                scenario.organizerToken(),
                "/api/v1/checkins/password/session",
                Map.of("activityId", scenario.activity().getId()))
                .andExpect(status().isOk()));

        PasswordCheckInRequest request = new PasswordCheckInRequest();
        request.setActivityId(scenario.activity().getId());
        request.setCode(session.path("data").path("code").asText());
        authPost(scenario.studentToken(), "/api/v1/checkins/password", request)
                .andExpect(status().isOk());

        authGet(scenario.organizerToken(), "/api/v1/checkins/stats?activityId=" + scenario.activity().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registeredCount").value(1))
                .andExpect(jsonPath("$.data.checkedInCount").value(1))
                .andExpect(jsonPath("$.data.uncheckedCount").value(0))
                .andExpect(jsonPath("$.data.records[0].userId").value(scenario.student().getId()));
    }

    private void approveStudentRegistration() throws Exception {
        RegistrationRequest registrationRequest = new RegistrationRequest();
        registrationRequest.setActivityId(scenario.activity().getId());
        JsonNode signupResponse = parseResponse(authPost(
                scenario.studentToken(),
                "/api/v1/registrations",
                registrationRequest)
                .andExpect(status().isOk()));

        ReviewRegistrationRequest reviewRequest = new ReviewRegistrationRequest();
        reviewRequest.setApproved(true);
        authPut(
                scenario.organizerToken(),
                "/api/v1/registrations/" + signupResponse.path("data").path("id").asLong() + "/review",
                reviewRequest)
                .andExpect(status().isOk());
    }

    private void makeActivityOpen(Long activityId) {
        transactionTemplate.executeWithoutResult(status -> {
            var activity = activityRepository.findById(activityId).orElseThrow();
            LocalDateTime now = LocalDateTime.now();
            activity.setStartTime(now.minusMinutes(10));
            activity.setEndTime(now.plusHours(2));
            activity.setLatitude(31.0252);
            activity.setLongitude(121.4337);
            activity.setCheckInRadiusMeters(200);
            activityRepository.save(activity);
        });
    }
}
