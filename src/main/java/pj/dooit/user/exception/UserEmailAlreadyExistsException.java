package pj.dooit.user.exception;

public class UserEmailAlreadyExistsException extends RuntimeException {

    private final String email;

    public UserEmailAlreadyExistsException(String email) {
        super("이미 가입된 이메일입니다. email=" + email);
        this.email = email;
    }

    public String getDetail() {
        return "email=" + email;
    }
}
