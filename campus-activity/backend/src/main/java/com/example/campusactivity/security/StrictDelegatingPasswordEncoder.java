package com.example.campusactivity.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.regex.Pattern;

public final class StrictDelegatingPasswordEncoder implements PasswordEncoder {
    private static final String BCRYPT_ID = "bcrypt";
    private static final String BCRYPT_PREFIX = "{bcrypt}";
    private static final Pattern BCRYPT_ENCODING = Pattern.compile(
            "^\\{bcrypt}\\$2[ayb]\\$(?:0[4-9]|[12]\\d|3[01])"
                    + "\\$[./0-9A-Za-z]{53}$"
    );

    private final PasswordEncoder delegate =
            new DelegatingPasswordEncoder(
                    BCRYPT_ID,
                    Map.of(BCRYPT_ID, new BCryptPasswordEncoder())
            );

    @Override
    public String encode(CharSequence rawPassword) {
        String encoded = delegate.encode(rawPassword);
        if (!encoded.startsWith(BCRYPT_PREFIX)) {
            throw new IllegalStateException("密码编码配置无效");
        }
        return encoded;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || !isValidBcryptEncoding(encodedPassword)) {
            return false;
        }
        try {
            return delegate.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException _exception) {
            return false;
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (!isValidBcryptEncoding(encodedPassword)) {
            return false;
        }
        try {
            return delegate.upgradeEncoding(encodedPassword);
        } catch (IllegalArgumentException _exception) {
            return false;
        }
    }

    private static boolean isValidBcryptEncoding(String encodedPassword) {
        return encodedPassword != null
                && encodedPassword.startsWith(BCRYPT_PREFIX)
                && BCRYPT_ENCODING.matcher(encodedPassword).matches();
    }
}
