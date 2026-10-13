package pj.dooit.personalsystem.exception;

import lombok.Getter;

@Getter
public class PersonalSystemValidationException extends RuntimeException {

    private final String detail;

    public PersonalSystemValidationException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
