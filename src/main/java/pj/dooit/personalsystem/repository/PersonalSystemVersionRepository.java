package pj.dooit.personalsystem.repository;

import pj.dooit.personalsystem.domain.PersonalSystemVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PersonalSystemVersionRepository extends JpaRepository<PersonalSystemVersion, Long> {

    List<PersonalSystemVersion> findBySystemIdOrderByVersionAsc(Long systemId);

    Optional<PersonalSystemVersion> findBySystemIdAndVersion(Long systemId, int version);

    Optional<PersonalSystemVersion> findTopBySystemIdAndEffectiveFromDateLessThanEqualOrderByVersionDesc(
            Long systemId,
            LocalDate effectiveFromDate
    );

    List<PersonalSystemVersion> findBySystemIdAndEffectiveFromDateLessThanEqualOrderByVersionAsc(
            Long systemId,
            LocalDate to
    );
}
