package pj.dooit.auth.exception;

import org.springframework.security.core.AuthenticationException;

public class GuestSessionExpiredException extends AuthenticationException {

    public GuestSessionExpiredException() {
        super("게스트 세션이 만료되었습니다.");
    }
}
