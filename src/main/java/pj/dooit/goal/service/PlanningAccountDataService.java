package pj.dooit.goal.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanningAccountDataService {
    private final JdbcTemplate jdbcTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void transferOwnership(Long sourceOwnerId, Long targetOwnerId) {
        entityManager.flush();
        for (String table : List.of("GOAL", "GOAL_PLAN", "GOAL_PLAN_OCCURRENCE", "GOAL_DIRECT_LOG",
                "PERSONAL_SYSTEM", "PERSONAL_SYSTEM_OCCURRENCE")) {
            jdbcTemplate.update("UPDATE `" + table + "` SET `OWNER_USER_ID` = ? WHERE `OWNER_USER_ID` = ?",
                    targetOwnerId, sourceOwnerId);
        }
    }

    @Transactional
    public void deleteOwnedData(Long ownerId) {
        entityManager.flush();
        // Release source mapping FKs before deleting the canonical graph, then release Task -> Goal.
        jdbcTemplate.update("DELETE FROM `PERSONAL_SYSTEM_PRACTICE_RESULT` WHERE `OCCURRENCE_ID` IN "
                + "(SELECT `ID` FROM `PERSONAL_SYSTEM_OCCURRENCE` WHERE `OWNER_USER_ID` = ?)", ownerId);
        jdbcTemplate.update("DELETE FROM `PERSONAL_SYSTEM_OCCURRENCE` WHERE `OWNER_USER_ID` = ?", ownerId);
        jdbcTemplate.update("DELETE FROM `PERSONAL_SYSTEM_VERSION` WHERE `SYSTEM_ID` IN "
                + "(SELECT `ID` FROM `PERSONAL_SYSTEM` WHERE `OWNER_USER_ID` = ?)", ownerId);
        jdbcTemplate.update("DELETE FROM `PERSONAL_SYSTEM` WHERE `OWNER_USER_ID` = ?", ownerId);
        jdbcTemplate.update("DELETE FROM `GOAL_PRACTICE_RESULT` WHERE `OCCURRENCE_ID` IN "
                + "(SELECT `ID` FROM `GOAL_PLAN_OCCURRENCE` WHERE `OWNER_USER_ID` = ?)", ownerId);
        jdbcTemplate.update("DELETE FROM `GOAL_PLAN_OCCURRENCE` WHERE `OWNER_USER_ID` = ?", ownerId);
        jdbcTemplate.update("DELETE FROM `GOAL_DIRECT_LOG` WHERE `OWNER_USER_ID` = ?", ownerId);
        jdbcTemplate.update("DELETE FROM `GOAL_PLAN_VERSION` WHERE `PLAN_ID` IN "
                + "(SELECT `ID` FROM `GOAL_PLAN` WHERE `OWNER_USER_ID` = ?)", ownerId);
        jdbcTemplate.update("DELETE FROM `GOAL_PLAN` WHERE `OWNER_USER_ID` = ?", ownerId);
        jdbcTemplate.update("UPDATE `TASK` SET `LINKED_GOAL_ID` = NULL WHERE `OWNER_USER_ID` = ?", ownerId);
        jdbcTemplate.update("DELETE FROM `GOAL_VERSION` WHERE `GOAL_ID` IN "
                + "(SELECT `ID` FROM `GOAL` WHERE `OWNER_USER_ID` = ?)", ownerId);
        jdbcTemplate.update("DELETE FROM `GOAL` WHERE `OWNER_USER_ID` = ?", ownerId);
    }
}
