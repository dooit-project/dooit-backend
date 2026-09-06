package pj.dooit.config;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

class MonitoringAuthenticationManager implements AuthenticationManager {

    private static final List<SimpleGrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_MONITORING"));

    private final MonitoringProperties properties;

    MonitoringAuthenticationManager(MonitoringProperties properties) {
        this.properties = properties;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());

        if (matches(username, password)) {
            return UsernamePasswordAuthenticationToken.authenticated(username, null, AUTHORITIES);
        }

        throw new BadCredentialsException("Invalid monitoring credentials");
    }

    private boolean matches(String username, String password) {
        return StringUtils.hasText(properties.username())
                && StringUtils.hasText(password)
                && properties.username().equals(username)
                && constantTimeEquals(resolvePassword(), password);
    }

    private String resolvePassword() {
        if (StringUtils.hasText(properties.passwordFile())) {
            try {
                return Files.readString(Path.of(properties.passwordFile()), StandardCharsets.UTF_8).trim();
            } catch (IOException exception) {
                throw new BadCredentialsException("Monitoring password file is not readable", exception);
            }
        }

        if (StringUtils.hasText(properties.password())) {
            return properties.password();
        }

        throw new BadCredentialsException("Monitoring password is not configured");
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
