package pj.dooit.task.dto;

import pj.dooit.task.domain.TaskType;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 빠른 기록의 새 선택 필드만 엄격하게 읽어 기존 Task API의 enum 처리는 유지한다. */
public class QuickCaptureTaskTypeDeserializer extends ValueDeserializer<TaskType> {

    @Override
    public TaskType deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            String value = parser.getString();
            for (TaskType type : TaskType.values()) {
                if (type.name().equals(value)) {
                    return type;
                }
            }
        }
        return context.reportInputMismatch(TaskType.class,
                "requestedType은 TODO, SCHEDULE, IDEA 문자열 또는 null이어야 합니다.");
    }
}
