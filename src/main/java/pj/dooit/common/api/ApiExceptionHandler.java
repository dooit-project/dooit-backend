package pj.dooit.common.api;

import pj.dooit.auth.exception.InvalidCredentialsException;
import pj.dooit.auth.exception.GuestCreationRateLimitExceededException;
import pj.dooit.auth.exception.GuestSessionExpiredException;
import pj.dooit.auth.exception.PasswordResetRateLimitExceededException;
import pj.dooit.auth.exception.PasswordResetTokenInvalidException;
import pj.dooit.auth.exception.RefreshTokenExpiredException;
import pj.dooit.auth.exception.RefreshTokenInvalidException;
import pj.dooit.auth.exception.RefreshTokenReusedException;
import pj.dooit.common.idempotency.IdempotencyKeyReusedException;
import pj.dooit.dday.exception.DdayGoalNotFoundException;
import pj.dooit.task.exception.TaskOrderConflictException;
import pj.dooit.task.exception.TaskTemplateNotFoundException;
import pj.dooit.task.exception.TaskValidationException;
import pj.dooit.task.exception.TaskNotFoundException;
import pj.dooit.user.exception.UserEmailAlreadyExistsException;
import pj.dooit.workspace.exception.WorkspaceMemberNotFoundException;
import pj.dooit.workspace.exception.WorkspaceNotFoundException;
import pj.dooit.workspace.exception.WorkspaceValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Bean Validation 에러 처리 (MVC)
     * - @RequestBody @Valid  -> MethodArgumentNotValidException
     * - @ModelAttribute / 바인딩 -> BindException
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<?>> handleValidationException(Exception e) {
        FieldError error = extractFirstFieldError(e);
        if (error != null) {
            String detail = error.getField() + ": " + error.getDefaultMessage();
            log.warn("Validation Failed : {}", detail);
        } else {
            log.warn("Validation Failed : {}", e.getMessage());
        }
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.INVALID_INPUT));
    }

    /**
     * PathVariable / RequestParam 타입 미스매치
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn(
                "Type Mismatch : parameter={}, requiredType={}",
                e.getName(),
                requiredTypeName(e)
        );
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.INVALID_INPUT));
    }

    /**
     * 필수 요청값 누락 (@RequestParam, @PathVariable)
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
    public ResponseEntity<ApiResponse<?>> handleMissingRequestValueException(Exception e) {
        log.warn("Missing Request Value : {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.INVALID_INPUT));
    }

    /**
     * JSON 파싱 오류 등
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("Unreadable Request Body : {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.INVALID_INPUT));
    }

    /**
     * 도메인 커스텀 검증
     */
    @ExceptionHandler(TaskValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleTaskValidationException(TaskValidationException e) {
        log.warn("Task Validation Failed : {}", e.getDetail());
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.INVALID_INPUT));
    }

    /**
     * Task 리소스 없음
     */
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleTaskNotFoundException(TaskNotFoundException e) {
        log.warn("Task Not Found : {}", e.getDetail());
        return ResponseEntity.status(ErrorCode.TASK_NOT_FOUND.getStatus())
                .body(ApiResponse.failure(ErrorCode.TASK_NOT_FOUND));
    }

    @ExceptionHandler(TaskOrderConflictException.class)
    public ResponseEntity<ApiResponse<?>> handleTaskOrderConflictException(TaskOrderConflictException e) {
        log.warn("Task Order Conflict : {}", e.getDetail());
        return ResponseEntity.status(ErrorCode.TASK_ORDER_CONFLICT.getStatus())
                .body(ApiResponse.failure(ErrorCode.TASK_ORDER_CONFLICT));
    }

    @ExceptionHandler(TaskTemplateNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleTaskTemplateNotFoundException(TaskTemplateNotFoundException e) {
        log.warn("Task Template Not Found : {}", e.getDetail());
        return ResponseEntity.status(ErrorCode.TASK_TEMPLATE_NOT_FOUND.getStatus())
                .body(ApiResponse.failure(ErrorCode.TASK_TEMPLATE_NOT_FOUND));
    }

    @ExceptionHandler(DdayGoalNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleDdayGoalNotFoundException(DdayGoalNotFoundException e) {
        log.warn("D-Day Goal Not Found : {}", e.getDetail());
        return ResponseEntity.status(ErrorCode.DDAY_GOAL_NOT_FOUND.getStatus())
                .body(ApiResponse.failure(ErrorCode.DDAY_GOAL_NOT_FOUND));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("Resource Not Found : {}", e.getResourcePath());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ApiResponse.failure(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ApiResponse<?>> handleIdempotencyKeyReusedException(IdempotencyKeyReusedException e) {
        log.warn("Idempotency Key Reused With Different Payload");
        return ResponseEntity.status(ErrorCode.IDEMPOTENCY_KEY_REUSED.getStatus())
                .body(ApiResponse.failure(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @ExceptionHandler(UserEmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<?>> handleUserEmailAlreadyExistsException(UserEmailAlreadyExistsException e) {
        log.warn("User Email Already Exists : {}", e.getDetail());
        return ResponseEntity.status(ErrorCode.USER_EMAIL_ALREADY_EXISTS.getStatus())
                .body(ApiResponse.failure(ErrorCode.USER_EMAIL_ALREADY_EXISTS));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidCredentialsException(InvalidCredentialsException e) {
        log.warn("Invalid Credentials");
        return ResponseEntity.status(ErrorCode.INVALID_CREDENTIALS.getStatus())
                .body(ApiResponse.failure(ErrorCode.INVALID_CREDENTIALS));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthenticationException(AuthenticationException e) {
        log.warn("Authentication Failed");
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getStatus())
                .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(GuestSessionExpiredException.class)
    public ResponseEntity<ApiResponse<?>> handleGuestSessionExpiredException(GuestSessionExpiredException e) {
        log.warn("Guest Session Expired");
        return ResponseEntity.status(ErrorCode.GUEST_SESSION_EXPIRED.getStatus())
                .body(ApiResponse.failure(ErrorCode.GUEST_SESSION_EXPIRED));
    }

    @ExceptionHandler(GuestCreationRateLimitExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleGuestCreationRateLimitExceededException(
            GuestCreationRateLimitExceededException e
    ) {
        log.warn("Guest Creation Rate Limit Exceeded");
        return ResponseEntity.status(ErrorCode.GUEST_CREATION_RATE_LIMIT_EXCEEDED.getStatus())
                .body(ApiResponse.failure(ErrorCode.GUEST_CREATION_RATE_LIMIT_EXCEEDED));
    }

    @ExceptionHandler(PasswordResetTokenInvalidException.class)
    public ResponseEntity<ApiResponse<?>> handlePasswordResetTokenInvalidException(
            PasswordResetTokenInvalidException e
    ) {
        log.warn("Password Reset Token Invalid");
        return ResponseEntity.status(ErrorCode.PASSWORD_RESET_TOKEN_INVALID.getStatus())
                .body(ApiResponse.failure(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
    }

    @ExceptionHandler(PasswordResetRateLimitExceededException.class)
    public ResponseEntity<ApiResponse<?>> handlePasswordResetRateLimitExceededException(
            PasswordResetRateLimitExceededException e
    ) {
        log.warn("Password Reset Rate Limit Exceeded");
        return ResponseEntity.status(ErrorCode.PASSWORD_RESET_RATE_LIMIT_EXCEEDED.getStatus())
                .body(ApiResponse.failure(ErrorCode.PASSWORD_RESET_RATE_LIMIT_EXCEEDED));
    }

    @ExceptionHandler(RefreshTokenInvalidException.class)
    public ResponseEntity<ApiResponse<?>> handleRefreshTokenInvalidException(RefreshTokenInvalidException e) {
        log.warn("Refresh Token Invalid");
        return ResponseEntity.status(ErrorCode.REFRESH_TOKEN_INVALID.getStatus())
                .body(ApiResponse.failure(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ApiResponse<?>> handleRefreshTokenExpiredException(RefreshTokenExpiredException e) {
        log.warn("Refresh Token Expired");
        return ResponseEntity.status(ErrorCode.REFRESH_TOKEN_EXPIRED.getStatus())
                .body(ApiResponse.failure(ErrorCode.REFRESH_TOKEN_EXPIRED));
    }

    @ExceptionHandler(RefreshTokenReusedException.class)
    public ResponseEntity<ApiResponse<?>> handleRefreshTokenReusedException(RefreshTokenReusedException e) {
        log.warn("Refresh Token Reused");
        return ResponseEntity.status(ErrorCode.REFRESH_TOKEN_REUSED.getStatus())
                .body(ApiResponse.failure(ErrorCode.REFRESH_TOKEN_REUSED));
    }

    @ExceptionHandler(WorkspaceValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleWorkspaceValidationException(WorkspaceValidationException e) {
        log.warn("Workspace Validation Failed : {}", e.getDetail());
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleWorkspaceNotFoundException(WorkspaceNotFoundException e) {
        log.warn("Workspace Not Found : {}", e.getDetail());
        return ResponseEntity.status(ErrorCode.WORKSPACE_NOT_FOUND.getStatus())
                .body(ApiResponse.failure(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleWorkspaceMemberNotFoundException(WorkspaceMemberNotFoundException e) {
        log.warn("Workspace Member Not Found : {}", e.getDetail());
        return ResponseEntity.status(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getStatus())
                .body(ApiResponse.failure(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("Access Denied");
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
                .body(ApiResponse.failure(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("Unhandled Exception", e);
        return ResponseEntity.internalServerError().body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR));
    }

    private FieldError extractFirstFieldError(Exception e) {
        if (e instanceof MethodArgumentNotValidException manve) {
            return manve.getBindingResult().getFieldErrors().isEmpty()
                    ? null
                    : manve.getBindingResult().getFieldErrors().getFirst();
        }
        if (e instanceof BindException be) {
            return be.getBindingResult().getFieldErrors().isEmpty()
                    ? null
                    : be.getBindingResult().getFieldErrors().getFirst();
        }
        return null;
    }

    private String requiredTypeName(MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        return requiredType == null ? "unknown" : requiredType.getSimpleName();
    }
}
