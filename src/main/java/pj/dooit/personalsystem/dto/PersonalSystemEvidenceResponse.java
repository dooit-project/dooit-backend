package pj.dooit.personalsystem.dto;

import pj.dooit.personalsystem.domain.ActionVariant;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PersonalSystemEvidenceResponse(
        Long systemId,
        LocalDate from,
        LocalDate to,
        String timeZone,
        int practicedOccurrenceCount,
        int practicedDateCount,
        Map<ActionVariant, Integer> practicedCountsByVariant,
        int restedCount,
        int noRecordCount,
        List<PersonalSystemOccurrenceResponse> occurrences
) {
}
