package pj.dooit.personalsystem.exception;

import lombok.Getter;

@Getter
public class PersonalSystemMigratedException extends RuntimeException {

    private final String detail;

    public PersonalSystemMigratedException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
