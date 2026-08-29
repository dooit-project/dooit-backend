package pj.dooit.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;

public class RepositoryTestSupport {

    @PersistenceContext
    protected EntityManager em;

    @BeforeEach
    void clearBeforeEach() {
        // 매 테스트 시작마다 영속성 컨텍스트 초기화
        em.clear();
    }

    protected void flushAndClear() {
        em.flush();
        em.clear();
    }

}
