package pj.dooit.personalsystem.service;

import pj.dooit.personalsystem.domain.ActionVariant;
import pj.dooit.personalsystem.domain.NextActionPatchKind;
import pj.dooit.personalsystem.domain.PersonalSystemOccurrence;
import pj.dooit.personalsystem.domain.PersonalSystemPracticeResult;
import pj.dooit.personalsystem.repository.PersonalSystemOccurrenceRepository;
import pj.dooit.personalsystem.repository.PersonalSystemPracticeResultRepository;
import pj.dooit.task.domain.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonalSystemTaskLinkService {

    private final PersonalSystemOccurrenceRepository occurrenceRepository;
    private final PersonalSystemPracticeResultRepository resultRepository;

    @Transactional
    public void lockLinkedOccurrence(Long taskId) {
        occurrenceRepository.findByLinkedTaskId(taskId);
    }

    @Transactional
    public void recordLinkedTaskCompleted(Task task, LocalDateTime completedAt) {
        if (task == null || task.getId() == null || task.getOwner() == null || task.getOwner().getId() == null) {
            return;
        }
        occurrenceRepository.findByLinkedTaskIdAndOwnerId(task.getId(), task.getOwner().getId())
                .filter(occurrence -> occurrence.getMigratedOccurrenceId() == null)
                .ifPresent(occurrence -> recordCompleted(occurrence, completedAt));
    }

    @Transactional
    public void recordLinkedTaskReopened(Task task) {
        if (task == null || task.getId() == null || task.getOwner() == null || task.getOwner().getId() == null) {
            return;
        }
        occurrenceRepository.findByLinkedTaskIdAndOwnerId(task.getId(), task.getOwner().getId())
                .filter(occurrence -> occurrence.getMigratedOccurrenceId() == null)
                .ifPresent(occurrence -> {
                    resultRepository.deleteById(occurrence.getId());
                    occurrence.markPending();
                });
    }

    private void recordCompleted(PersonalSystemOccurrence occurrence, LocalDateTime completedAt) {
        if (resultRepository.existsById(occurrence.getId())) {
            occurrence.markPracticed();
            return;
        }
        PersonalSystemPracticeResult result = new PersonalSystemPracticeResult(
                occurrence,
                ActionVariant.UNKNOWN,
                completedAt,
                occurrence.getTimeZone(),
                null,
                List.of(),
                NextActionPatchKind.KEEP,
                null
        );
        occurrence.markPracticed();
        resultRepository.save(result);
    }
}
