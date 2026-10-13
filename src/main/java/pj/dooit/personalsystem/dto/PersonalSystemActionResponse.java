package pj.dooit.personalsystem.dto;

public record PersonalSystemActionResponse(
        String text,
        String targetAmount,
        Integer estimatedMinutes
) {
}
