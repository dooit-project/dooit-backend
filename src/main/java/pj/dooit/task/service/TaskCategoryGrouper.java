package pj.dooit.task.service;

import pj.dooit.Constant;
import pj.dooit.task.dto.TaskCategoryGroupResponse;
import pj.dooit.task.dto.TaskResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TaskCategoryGrouper {

    public List<TaskCategoryGroupResponse> group(List<TaskResponse> tasks) {
        Map<String, List<TaskResponse>> grouped = tasks.stream()
                .collect(Collectors.groupingBy(task -> toCategoryLabel(task.category())));

        return grouped.entrySet().stream()
                .sorted((a, b) -> compareCategory(a.getKey(), b.getKey()))
                .map(entry -> new TaskCategoryGroupResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private int compareCategory(String a, String b) {
        boolean aUncategorized = a.equals(Constant.UNCATEGORIZED);
        boolean bUncategorized = b.equals(Constant.UNCATEGORIZED);

        if (aUncategorized && !bUncategorized) {
            return 1;
        }
        if (!aUncategorized && bUncategorized) {
            return -1;
        }
        return a.compareTo(b);
    }

    private String toCategoryLabel(String category) {
        if (category == null || category.isBlank()) {
            return Constant.UNCATEGORIZED;
        }
        return category;
    }
}
