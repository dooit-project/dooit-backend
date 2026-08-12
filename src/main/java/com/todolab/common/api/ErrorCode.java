package com.todolab.common.api;

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

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, 11001, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 11002, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, 11003, "접근 권한이 없습니다."),
    GUEST_CREATION_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 11004, "게스트 계정 생성 요청이 너무 많습니다."),

    // Task
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, 20001, "일정을 찾을 수 없습니다."),
    TASK_ORDER_CONFLICT(HttpStatus.CONFLICT, 20002, "Today 목록이 변경되었습니다. 새로고침 후 다시 시도해주세요."),
    TASK_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, 20003, "Task 템플릿을 찾을 수 없습니다."),

    // D-Day
    DDAY_GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, 30001, "D-Day 목표를 찾을 수 없습니다."),

    // User
    USER_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, 40001, "이미 가입된 이메일입니다."),

    // 서버 내부 오류
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 99999, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final int code;
    private final String message;
}
