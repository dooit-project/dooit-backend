package pj.dooit.personalsystem.dto;

public record PersonalSystemMigrationResponse(
        Long systemId, Long goalId, Long planId, int versionCount, int occurrenceCount,
        int resultCount, boolean alreadyMigrated
) {}
