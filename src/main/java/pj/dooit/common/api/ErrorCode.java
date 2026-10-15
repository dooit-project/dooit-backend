package pj.dooit.common.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통 검증 에러
    INVALID_INPUT(HttpStatus.BAD_REQUEST, 10001, "값이 올바르지 않습니다."),
    REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, 10002, "필수값이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, 10003, "요청한 리소스를 찾을 수 없습니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, 10004, "Idempotency-Key가 다른 요청 본문으로 재사용되었습니다."),

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, 11001, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 11002, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, 11003, "접근 권한이 없습니다."),
    GUEST_CREATION_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 11004, "게스트 계정 생성 요청이 너무 많습니다."),
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, 11005, "비밀번호 재설정 링크가 만료되었거나 올바르지 않습니다."),
    PASSWORD_RESET_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 11006, "비밀번호 재설정 요청이 너무 많습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, 11007, "refresh token이 올바르지 않습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, 11008, "refresh token이 만료되었습니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, 11009, "refresh token 재사용이 감지되었습니다."),
    GUEST_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, 11010, "게스트 세션이 만료되었습니다. 로그인하거나 새 게스트로 시작해주세요."),

    // Task
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, 20001, "일정을 찾을 수 없습니다."),
    TASK_ORDER_CONFLICT(HttpStatus.CONFLICT, 20002, "Today 목록이 변경되었습니다. 새로고침 후 다시 시도해주세요."),
    TASK_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, 20003, "Task 템플릿을 찾을 수 없습니다."),
    TASK_CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, 20004, "Task checklist item을 찾을 수 없습니다."),

    // D-Day
    DDAY_GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, 30001, "D-Day 목표를 찾을 수 없습니다."),

    // User
    USER_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, 40001, "이미 가입된 이메일입니다."),

    // Workspace
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, 50001, "공유 workspace를 찾을 수 없습니다."),
    WORKSPACE_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, 50002, "공유 workspace 멤버를 찾을 수 없습니다."),

    // Personal System
    PERSONAL_SYSTEM_NOT_FOUND(HttpStatus.NOT_FOUND, 60001, "개인 시스템을 찾을 수 없습니다."),
    PERSONAL_SYSTEM_OCCURRENCE_NOT_FOUND(HttpStatus.NOT_FOUND, 60002, "개인 시스템 회차를 찾을 수 없습니다."),
    PERSONAL_SYSTEM_CONFLICT(HttpStatus.CONFLICT, 60003, "개인 시스템 회차가 변경되었습니다. 새로고침 후 다시 시도해주세요."),

    // Goal Journey
    GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, 70001, "목표를 찾을 수 없습니다."),
    GOAL_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, 70002, "목표 계획을 찾을 수 없습니다."),

    GOAL_OCCURRENCE_NOT_FOUND(HttpStatus.NOT_FOUND, 70003, "목표 계획 회차를 찾을 수 없습니다."),
    GOAL_OCCURRENCE_CONFLICT(HttpStatus.CONFLICT, 70004, "목표 계획 회차의 상태가 변경되었거나 요청 작업과 충돌합니다. 새로고침 후 다시 시도해주세요."),

    GOAL_TASK_CONFLICT(HttpStatus.CONFLICT, 70005, "반복 회차 Task의 목표 연결은 계획에서 변경해주세요."),
    GOAL_DIRECT_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, 70006, "목표 직접 실천 기록을 찾을 수 없습니다."),
    GOAL_DIRECT_LOG_CONFLICT(HttpStatus.CONFLICT, 70007, "취소한 직접 실천 기록은 수정할 수 없습니다."),

    GOAL_JOURNEY_CONFLICT(HttpStatus.CONFLICT, 70008, "목표 여정이 변경되었습니다. 처음부터 다시 조회해주세요."),

    // 서버 내부 오류
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 99999, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final int code;
    private final String message;
}
