package pj.dooit.personalsystem.exception;

import lombok.Getter;

@Getter
public class PersonalSystemOccurrenceNotFoundException extends RuntimeException {

    private final String detail;

    public PersonalSystemOccurrenceNotFoundException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
