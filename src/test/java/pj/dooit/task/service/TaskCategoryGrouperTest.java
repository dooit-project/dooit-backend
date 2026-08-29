package pj.dooit.task.service;

import pj.dooit.Constant;
import pj.dooit.task.dto.TaskCategoryGroupResponse;
import pj.dooit.task.dto.TaskResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskCategoryGrouperTest {

    TaskCategoryGrouper taskCategoryGrouper = new TaskCategoryGrouper();

    @Test
    @DisplayName("카테고리별로 그룹핑하고 미분류는 마지막에 배치한다")
    void group_groupsByCategoryAndPlacesUncategorizedLast() {
        // given
        List<TaskResponse> tasks = List.of(
                task(1L, "미분류1", null),
                task(2L, "공부1", "공부"),
                task(3L, "일1", "일"),
                task(4L, "공부2", "공부"),
                task(5L, "미분류2", " ")
        );

        // when
        List<TaskCategoryGroupResponse> groups = taskCategoryGrouper.group(tasks);

        // then
        assertThat(groups).extracting(TaskCategoryGroupResponse::category)
                .containsExactly("공부", "일", Constant.UNCATEGORIZED);
        assertThat(groups.get(0).tasks()).extracting(TaskResponse::title)
                .containsExactly("공부1", "공부2");
        assertThat(groups.get(2).tasks()).extracting(TaskResponse::title)
                .containsExactly("미분류1", "미분류2");
    }

    private TaskResponse task(Long id, String title, String category) {
        return TaskResponse.builder()
                .id(id)
                .title(title)
                .category(category)
                .build();
    }
}
