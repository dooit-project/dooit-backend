package pj.dooit.personalsystem.exception;

import lombok.Getter;

@Getter
public class PersonalSystemConflictException extends RuntimeException {

    private final String detail;

    public PersonalSystemConflictException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
