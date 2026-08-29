package pj.dooit.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByScopeKeyAndHttpMethodAndRequestPathAndIdempotencyKey(
            String scopeKey,
            String httpMethod,
            String requestPath,
            String idempotencyKey
    );
}
