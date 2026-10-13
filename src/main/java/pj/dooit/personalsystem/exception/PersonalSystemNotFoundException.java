package pj.dooit.personalsystem.exception;

import lombok.Getter;

@Getter
public class PersonalSystemNotFoundException extends RuntimeException {

    private final String detail;

    public PersonalSystemNotFoundException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
