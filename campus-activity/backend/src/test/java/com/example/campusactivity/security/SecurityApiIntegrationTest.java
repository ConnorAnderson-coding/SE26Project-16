package com.example.campusactivity.security;

import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "campus.demo-password=TestPassword123!",
        "community-clustering.python.enabled=false"
})
@AutoConfigureMockMvc
class SecurityApiIntegrationTest {
    private static final String PASSWORD = "StrongPassword123!";
    private static final String STUDENT_ID = "security-student";
    private static final String TEACHER_ID = "security-teacher";
    private static final String ADMIN_ID = "security-admin";
    private static final String PLAINTEXT_ID = "security-plaintext";
    private static final String INVALID_ROLE_ID = "security-invalid-role";
    private static final String CREATED_ID = "security-created";
    private static final String INVALID_CREATED_ID = "security-invalid-created";
    private static final String REGISTERED_ID = "security-registered";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createSecurityFixtures() {
        deleteFixtures();
        userRepository.save(user(STUDENT_ID, PASSWORD, "student"));
        userRepository.save(user(TEACHER_ID, PASSWORD, "teacher"));
        userRepository.save(user(ADMIN_ID, PASSWORD, "  ADMIN  "));

        UserAccount plaintext = user(PLAINTEXT_ID, PASSWORD, "student");
        plaintext.setPassword(PASSWORD);
        userRepository.save(plaintext);

        userRepository.save(user(INVALID_ROLE_ID, PASSWORD, "superadmin"));
    }

    @AfterEach
    void deleteFixtures() {
        userRepository.deleteAllById(List.of(
                STUDENT_ID,
                TEACHER_ID,
                ADMIN_ID,
                PLAINTEXT_ID,
                INVALID_ROLE_ID,
                CREATED_ID,
                INVALID_CREATED_ID,
                REGISTERED_ID
        ));
    }

    @Test
    void anonymousCanFetchCsrfButCannotAccessMeUsersOrLogout() throws Exception {
        CsrfSession state = csrfSession();

        assertThat(state.token()).isNotBlank();
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));
        mockMvc.perform(get("/api/users")
                        .header("X-Role", "admin")
                        .header("X-User-Id", ADMIN_ID))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));
        mockMvc.perform(post("/api/auth/logout")
                        .session(state.session())
                        .header(state.headerName(), state.token()))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));
    }

    @Test
    void csrfProtectsLoginRegisterLogoutAndOtherBusinessPosts() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(STUDENT_ID, PASSWORD)))
                .andExpect(fixedError(
                        403,
                        "CSRF_TOKEN_INVALID",
                        "请求安全校验失败"
                ));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(REGISTERED_ID)))
                .andExpect(fixedError(
                        403,
                        "CSRF_TOKEN_INVALID",
                        "请求安全校验失败"
                ));
        mockMvc.perform(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(fixedError(
                        403,
                        "CSRF_TOKEN_INVALID",
                        "请求安全校验失败"
                ));

        CsrfSession state = csrfSession();
        mockMvc.perform(post("/api/auth/login")
                        .session(state.session())
                        .header(state.headerName(), state.token() + "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(STUDENT_ID, PASSWORD)))
                .andExpect(fixedError(
                        403,
                        "CSRF_TOKEN_INVALID",
                        "请求安全校验失败"
                ));

        mockMvc.perform(options("/api/users")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    void successfulLoginRotatesSessionRejectsOldCsrfAndAcceptsNewCsrf()
            throws Exception {
        CsrfSession state = csrfSession();
        String oldSessionId = state.session().getId();

        login(state, STUDENT_ID, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(STUDENT_ID))
                .andExpect(jsonPath("$.data.role").value("student"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("hash"))))
                .andExpect(content().string(not(containsString("authorities"))))
                .andExpect(content().string(not(containsString("JSESSIONID"))));

        assertThat(state.session().getId()).isNotEqualTo(oldSessionId);
        Object stored = state.session().getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        );
        assertThat(stored).isInstanceOf(SecurityContext.class);
        SecurityContext context = (SecurityContext) stored;
        assertThat(context.getAuthentication().getName()).isEqualTo(STUDENT_ID);
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(context.getAuthentication().getPrincipal())
                .isInstanceOf(CampusUserPrincipal.class);
        assertThat(((CampusUserPrincipal) context.getAuthentication()
                .getPrincipal()).getPassword()).isNull();

        mockMvc.perform(post("/api/auth/logout")
                        .session(state.session())
                        .header(state.headerName(), state.token()))
                .andExpect(fixedError(
                        403,
                        "CSRF_TOKEN_INVALID",
                        "请求安全校验失败"
                ));

        mockMvc.perform(get("/api/auth/me").session(state.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(STUDENT_ID))
                .andExpect(jsonPath("$.data.role").value("student"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("friends"))));

        CsrfSession refreshed = refreshCsrf(state.session());
        assertThat(refreshed.token()).isNotEqualTo(state.token());

        mockMvc.perform(post("/api/auth/logout")
                        .session(refreshed.session())
                        .header(refreshed.headerName(), refreshed.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(state.session().isInvalid()).isTrue();
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));
    }

    @Test
    void failedLoginsAreAnonymousUniformAndDoNotRotateSession()
            throws Exception {
        assertFailedLogin(STUDENT_ID, "wrong-password");
        assertFailedLogin("missing-account", PASSWORD);
        assertFailedLogin(PLAINTEXT_ID, PASSWORD);
        assertFailedLogin(INVALID_ROLE_ID, PASSWORD);
    }

    @Test
    void loginParserRejectsUnknownDuplicateTrailingAndNonJsonRequests()
            throws Exception {
        assertInvalidLogin(
                "{\"id\":\"" + STUDENT_ID + "\",\"password\":\""
                        + PASSWORD + "\",\"role\":\"admin\"}",
                MediaType.APPLICATION_JSON
        );
        assertInvalidLogin(
                "{\"id\":\"" + STUDENT_ID + "\",\"id\":\""
                        + ADMIN_ID + "\",\"password\":\"" + PASSWORD + "\"}",
                MediaType.APPLICATION_JSON
        );
        assertInvalidLogin(
                loginJson(STUDENT_ID, PASSWORD) + " {}",
                MediaType.APPLICATION_JSON
        );
        assertInvalidLogin(
                loginJson(STUDENT_ID, PASSWORD),
                MediaType.TEXT_PLAIN
        );
    }

    @Test
    void authenticatedAccountMustLogoutBeforeAnotherLogin() throws Exception {
        CsrfSession state = csrfSession();
        login(state, STUDENT_ID, PASSWORD).andExpect(status().isOk());
        String sessionId = state.session().getId();
        CsrfSession refreshed = refreshCsrf(state.session());

        login(refreshed, ADMIN_ID, PASSWORD)
                .andExpect(fixedError(
                        400,
                        "INVALID_AUTH_REQUEST",
                        "认证请求无效"
                ));

        assertThat(state.session().getId()).isEqualTo(sessionId);
        mockMvc.perform(get("/api/auth/me").session(state.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(STUDENT_ID));
    }

    @Test
    void logoutRequiresFreshCsrfInvalidatesSessionAndDeletesCookie()
            throws Exception {
        CsrfSession state = loggedIn(STUDENT_ID);

        mockMvc.perform(post("/api/auth/logout").session(state.session()))
                .andExpect(fixedError(
                        403,
                        "CSRF_TOKEN_INVALID",
                        "请求安全校验失败"
                ));

        mockMvc.perform(post("/api/auth/logout")
                        .session(state.session())
                        .header(state.headerName(), state.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(content().string(not(containsString("JSESSIONID"))))
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(state.session().isInvalid()).isTrue();
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));
    }

    @Test
    void deletedAccountMakesMeReturn401AndInvalidatesItsSession()
            throws Exception {
        CsrfSession state = loggedIn(STUDENT_ID);
        userRepository.deleteById(STUDENT_ID);

        mockMvc.perform(get("/api/auth/me").session(state.session()))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));

        assertThat(state.session().isInvalid()).isTrue();
    }

    @Test
    void registrationCreatesStudentHashRejectsInjectedFieldsAndDoesNotLogin()
            throws Exception {
        CsrfSession state = csrfSession();
        mockMvc.perform(post("/api/auth/register")
                        .session(state.session())
                        .header(state.headerName(), state.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(REGISTERED_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(REGISTERED_ID))
                .andExpect(jsonPath("$.data.role").value("student"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("friends"))));

        UserAccount registered = userRepository.findById(REGISTERED_ID)
                .orElseThrow();
        assertThat(registered.getRole()).isEqualTo("student");
        assertThat(registered.getPassword()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(PASSWORD, registered.getPassword()))
                .isTrue();

        mockMvc.perform(get("/api/auth/me").session(state.session()))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));

        CsrfSession roleState = csrfSession();
        String withRole = registrationJson("another-id")
                .replace("}", ",\"role\":\"admin\"}");
        mockMvc.perform(post("/api/auth/register")
                        .session(roleState.session())
                        .header(roleState.headerName(), roleState.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withRole))
                .andExpect(fixedError(
                        400,
                        "INVALID_AUTH_REQUEST",
                        "认证请求无效"
                ));

        CsrfSession friendsState = csrfSession();
        String withFriends = registrationJson("another-id")
                .replace("}", ",\"friends\":[\"" + ADMIN_ID + "\"]}");
        mockMvc.perform(post("/api/auth/register")
                        .session(friendsState.session())
                        .header(friendsState.headerName(), friendsState.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withFriends))
                .andExpect(fixedError(
                        400,
                        "INVALID_AUTH_REQUEST",
                        "认证请求无效"
                ));
    }

    @Test
    void duplicateRegistrationReturnsFixed409() throws Exception {
        CsrfSession state = csrfSession();

        mockMvc.perform(post("/api/auth/register")
                        .session(state.session())
                        .header(state.headerName(), state.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(STUDENT_ID)))
                .andExpect(fixedError(
                        409,
                        "ACCOUNT_ALREADY_EXISTS",
                        "该账号已存在"
                ));
    }

    @Test
    void onlyDatabaseAdminCanUseUserEndpointsAndResponsesAreSafe()
            throws Exception {
        userRepository.deleteById(INVALID_ROLE_ID);
        CsrfSession student = loggedIn(STUDENT_ID);
        mockMvc.perform(get("/api/users")
                        .session(student.session())
                        .header("X-Role", "admin"))
                .andExpect(fixedError(
                        403,
                        "ACCESS_DENIED",
                        "无权访问该资源"
                ));

        CsrfSession teacher = loggedIn(TEACHER_ID);
        mockMvc.perform(get("/api/users").session(teacher.session()))
                .andExpect(fixedError(
                        403,
                        "ACCESS_DENIED",
                        "无权访问该资源"
                ));

        CsrfSession admin = loggedIn(ADMIN_ID);
        mockMvc.perform(get("/api/users").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("hash"))))
                .andExpect(content().string(not(containsString("friends"))));
    }

    @Test
    void adminCreateEncodesPasswordNormalizesRoleAndUpdateCannotChangeSecrets()
            throws Exception {
        CsrfSession admin = loggedIn(ADMIN_ID);
        String createJson = """
                {
                  "id": "%s",
                  "password": "%s",
                  "name": "Created User",
                  "role": "  TEACHER  ",
                  "college": "Software",
                  "grade": "2026",
                  "interests": ["AI"],
                  "availableTime": ["weekend"]
                }
                """.formatted(CREATED_ID, PASSWORD);

        mockMvc.perform(post("/api/users")
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CREATED_ID))
                .andExpect(jsonPath("$.role").value("teacher"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("friends"))));

        UserAccount created = userRepository.findById(CREATED_ID).orElseThrow();
        String originalHash = created.getPassword();
        assertThat(originalHash).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(PASSWORD, originalHash)).isTrue();
        assertThat(created.getRole()).isEqualTo("teacher");

        String invalidRoleJson = createJson
                .replace(CREATED_ID, INVALID_CREATED_ID)
                .replace("  TEACHER  ", "superadmin");
        mockMvc.perform(post("/api/users")
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRoleJson))
                .andExpect(fixedError(
                        400,
                        "INVALID_AUTH_REQUEST",
                        "认证请求无效"
                ));
        assertThat(userRepository.existsById(INVALID_CREATED_ID))
                .isFalse();

        mockMvc.perform(put("/api/users/{id}", CREATED_ID)
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Changed",
                                  "password": "replacement",
                                  "role": "admin"
                                }
                                """))
                .andExpect(fixedError(
                        400,
                        "INVALID_AUTH_REQUEST",
                        "认证请求无效"
                ));

        UserAccount unchanged = userRepository.findById(CREATED_ID)
                .orElseThrow();
        assertThat(unchanged.getPassword()).isEqualTo(originalHash);
        assertThat(unchanged.getRole()).isEqualTo("teacher");

        mockMvc.perform(put("/api/users/{id}", CREATED_ID)
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Changed",
                                  "college": "Updated",
                                  "grade": "2027",
                                  "interests": [],
                                  "availableTime": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Changed"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("friends"))));

        UserAccount updated = userRepository.findById(CREATED_ID).orElseThrow();
        assertThat(updated.getPassword()).isEqualTo(originalHash);
        assertThat(updated.getRole()).isEqualTo("teacher");
    }

    private void assertFailedLogin(String id, String password) throws Exception {
        CsrfSession state = csrfSession();
        String sessionId = state.session().getId();

        login(state, id, password)
                .andExpect(fixedError(
                        401,
                        "INVALID_CREDENTIALS",
                        "账号或密码错误"
                ));

        assertThat(state.session().getId()).isEqualTo(sessionId);
        assertThat(state.session().getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        )).isNull();
    }

    private void assertInvalidLogin(String json, MediaType mediaType)
            throws Exception {
        CsrfSession state = csrfSession();
        mockMvc.perform(post("/api/auth/login")
                        .session(state.session())
                        .header(state.headerName(), state.token())
                        .contentType(mediaType)
                        .content(json))
                .andExpect(fixedError(
                        400,
                        "INVALID_AUTH_REQUEST",
                        "认证请求无效"
                ));
    }

    private CsrfSession loggedIn(String id) throws Exception {
        CsrfSession state = csrfSession();
        login(state, id, PASSWORD).andExpect(status().isOk());
        return refreshCsrf(state.session());
    }

    private org.springframework.test.web.servlet.ResultActions login(
            CsrfSession state,
            String id,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .session(state.session())
                .header(state.headerName(), state.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(id, password)));
    }

    private CsrfSession csrfSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.headerName").isString())
                .andExpect(jsonPath("$.parameterName").isString())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);
        JsonNode json = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
        return new CsrfSession(
                session,
                json.get("token").asText(),
                json.get("headerName").asText()
        );
    }

    private CsrfSession refreshCsrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
        return new CsrfSession(
                session,
                json.get("token").asText(),
                json.get("headerName").asText()
        );
    }

    private UserAccount user(String id, String rawPassword, String role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setName("Security Test User");
        user.setRole(role);
        user.setCollege("Software");
        user.setGrade("2026");
        user.setInterests(new ArrayList<>(List.of("AI")));
        user.setAvailableTime(new ArrayList<>(List.of("weekend")));
        user.setFriends(new ArrayList<>());
        return user;
    }

    private String loginJson(String id, String password) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("id", id, "password", password)
        );
    }

    private String registrationJson(String id) {
        return """
                {
                  "id": "%s",
                  "password": "%s",
                  "name": "Registered User",
                  "college": "Software",
                  "grade": "2026",
                  "interests": ["AI"],
                  "availableTime": ["weekend"]
                }
                """.formatted(id, PASSWORD);
    }

    private static org.springframework.test.web.servlet.ResultMatcher fixedError(
            int status,
            String code,
            String message
    ) {
        return result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(status);
            assertThat(result.getResponse().getContentType())
                    .startsWith(MediaType.APPLICATION_JSON_VALUE);
            String body = result.getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            JsonNode json = new ObjectMapper().readTree(body);
            assertThat(json.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder("code", "message", "details");
            assertThat(json.get("code").asText()).isEqualTo(code);
            assertThat(json.get("message").asText()).isEqualTo(message);
            assertThat(json.get("details").isObject()).isTrue();
            assertThat(json.get("details").isEmpty()).isTrue();
            assertThat(body)
                    .doesNotContain(
                            "Exception",
                            "password",
                            "bcrypt",
                            "SELECT",
                            "security-",
                            "ROLE_",
                            "/api/"
                    );
        };
    }

    private record CsrfSession(
            MockHttpSession session,
            String token,
            String headerName
    ) {
    }
}
