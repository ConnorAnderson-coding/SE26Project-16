package com.example.demo.support;

import com.example.demo.dto.request.ActivityRequest;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.entity.Activity;
import com.example.demo.entity.User;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import com.example.demo.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "123456";
    private static final AtomicInteger SCENARIO_SEQ = new AtomicInteger(0);

    @Autowired
    protected MockMvc mockMvc;

    protected final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ActivityRepository activityRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    protected void loginAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    protected void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected record TestScenario(
            User student,
            User organizer,
            User admin,
            Activity activity,
            String studentToken,
            String organizerToken,
            String adminToken) {
    }

    protected TestScenario createScenario() {
        int seq = SCENARIO_SEQ.incrementAndGet();
        return transactionTemplate.execute(status -> {
            User student = saveUser("test-student-" + seq, "student", "测试学生");
            User organizer = saveUser("test-organizer-" + seq, "teacher", "测试组织者");
            User admin = saveUser("test-admin-" + seq, "admin", "测试管理员");
            Activity activity = saveActivity(organizer, "测试活动-" + seq, "published");
            return new TestScenario(
                    student,
                    organizer,
                    admin,
                    activity,
                    jwtTokenProvider.generateToken(student.getId()),
                    jwtTokenProvider.generateToken(organizer.getId()),
                    jwtTokenProvider.generateToken(admin.getId()));
        });
    }

    protected User saveUser(String id, String role, String name) {
        User user = new User();
        user.setId(id);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setName(name);
        user.setRole(role);
        user.setCollege("计算机学院");
        user.setGrade("2024");
        user.setInterests(List.of("编程", "篮球"));
        user.setAvailableTime(List.of("周末"));
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    protected Activity saveActivity(User organizer, String title, String status) {
        LocalDateTime now = LocalDateTime.now();
        Activity activity = new Activity();
        activity.setTitle(title);
        activity.setCategory("讲座");
        activity.setDescription("活动描述");
        activity.setStartTime(now.plusDays(1));
        activity.setEndTime(now.plusDays(1).plusHours(2));
        activity.setLocation("教学楼 A101");
        activity.setOrganizer(organizer);
        activity.setCollege(organizer.getCollege());
        activity.setMaxParticipants(50);
        activity.setSignupCount(0);
        activity.setFavoriteCount(0);
        activity.setStatus(status);
        activity.setTags(List.of("编程"));
        activity.setCheckInCode("CKTEST01");
        activity.setCreatedAt(now);
        activity.setUpdatedAt(now);
        return activityRepository.save(activity);
    }

    protected ActivityRequest buildActivityRequest(String title) {
        ActivityRequest request = new ActivityRequest();
        request.setTitle(title);
        request.setCategory("讲座");
        request.setDescription("新建活动描述");
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(2));
        request.setLocation("图书馆");
        request.setMaxParticipants(30);
        request.setTags(List.of("编程"));
        return request;
    }

    protected String loginToken(String userId, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUserId(userId);
        request.setPassword(password);
        JsonNode response = performPost("/api/v1/auth/login", request);
        return response.path("data").path("token").asText();
    }

    protected JsonNode performPost(String url, Object body) throws Exception {
        var result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected ResultActions authGet(String token, String url) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header("Authorization", "Bearer " + token));
    }

    protected ResultActions authPost(String token, String url, Object body) throws Exception {
        var builder = post(url).header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions authPut(String token, String url, Object body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions authDelete(String token, String url) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header("Authorization", "Bearer " + token));
    }

    protected JsonNode parseResponse(ResultActions actions) throws Exception {
        var result = actions.andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
