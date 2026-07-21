package com.example.campusactivity.security;

import com.example.campusactivity.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityComponentsTest {
    private static final String RAW_PASSWORD = "StrongPassword123!";

    private final RoleAuthorityMapper roleAuthorityMapper =
            new RoleAuthorityMapper();

    @Test
    void strictPasswordEncoderAlwaysEncodesWithBcryptAndMatchesExactly() {
        StrictDelegatingPasswordEncoder encoder =
                new StrictDelegatingPasswordEncoder();

        String encoded = encoder.encode(RAW_PASSWORD);

        assertThat(encoded).startsWith("{bcrypt}");
        assertThat(encoder.matches(RAW_PASSWORD, encoded)).isTrue();
        assertThat(encoder.matches("WrongPassword123!", encoded)).isFalse();
        assertThat(encoder.matches(null, encoded)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("rejectedEncodedPasswords")
    void strictPasswordEncoderRejectsNonBcryptAndDamagedValues(
            String encodedPassword
    ) {
        StrictDelegatingPasswordEncoder encoder =
                new StrictDelegatingPasswordEncoder();

        assertThatCode(() -> encoder.matches(RAW_PASSWORD, encodedPassword))
                .doesNotThrowAnyException();
        assertThat(encoder.matches(RAW_PASSWORD, encodedPassword)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("rejectedEncodedPasswords")
    void upgradeEncodingRejectsNonBcryptAndDamagedValues(
            String encodedPassword
    ) {
        StrictDelegatingPasswordEncoder encoder =
                new StrictDelegatingPasswordEncoder();

        assertThatCode(() -> encoder.upgradeEncoding(encodedPassword))
                .doesNotThrowAnyException();
        assertThat(encoder.upgradeEncoding(encodedPassword)).isFalse();
    }

    @Test
    void upgradeEncodingDelegatesOnlyForValidBcryptValues() {
        StrictDelegatingPasswordEncoder encoder =
                new StrictDelegatingPasswordEncoder();
        String lowerStrengthHash = "{bcrypt}"
                + new BCryptPasswordEncoder(4).encode(RAW_PASSWORD);

        assertThat(encoder.upgradeEncoding(lowerStrengthHash)).isTrue();
        assertThat(encoder.upgradeEncoding(encoder.encode(RAW_PASSWORD)))
                .isFalse();
    }

    @Test
    void roleMappingIsExactAndUsesRootLocaleNormalization() {
        assertThat(roleAuthorityMapper.authoritiesFor("admin"))
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
        assertThat(roleAuthorityMapper.authoritiesFor("teacher"))
                .extracting(Object::toString)
                .containsExactly("ROLE_TEACHER");
        assertThat(roleAuthorityMapper.authoritiesFor("student"))
                .extracting(Object::toString)
                .containsExactly("ROLE_STUDENT");
        assertThat(roleAuthorityMapper.normalize("  ADMIN  ")).isEqualTo("admin");
        assertThatThrownBy(() -> roleAuthorityMapper.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roleAuthorityMapper.normalize(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roleAuthorityMapper.normalize("superadmin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void principalContainsOnlyAuthenticationFieldsAndErasesCredentials() {
        CampusUserPrincipal principal = new CampusUserPrincipal(
                "account-1",
                "{bcrypt}hash",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        List<Field> instanceFields = Arrays.stream(
                        CampusUserPrincipal.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertThat(instanceFields)
                .extracting(Field::getName)
                .containsExactlyInAnyOrder(
                        "accountId",
                        "passwordHash",
                        "authorities"
                );
        assertThat(instanceFields)
                .extracting(Field::getType)
                .doesNotContain(UserAccount.class);
        assertThat(principal.getUsername()).isEqualTo("account-1");
        assertThat(principal.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(principal.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_STUDENT");

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
    }

    private static Stream<String> rejectedEncodedPasswords() {
        return Stream.of(
                null,
                "",
                RAW_PASSWORD,
                "{noop}" + RAW_PASSWORD,
                "{MD4}value",
                "{MD5}value",
                "{SHA-1}value",
                "{SHA-256}value",
                "{sha256}value",
                "{pbkdf2}value",
                "{scrypt}value",
                "{argon2}value",
                "{unknown}value",
                "{bcrypt}",
                "{bcrypt}damaged"
        );
    }
}
