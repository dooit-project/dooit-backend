package com.todolab.calendar.controller;

import com.todolab.auth.service.CurrentUserService;
import com.todolab.calendar.dto.CalendarFeedTokenResponse;
import com.todolab.calendar.service.CalendarFeedService;
import com.todolab.common.api.ApiResponse;
import com.todolab.user.domain.User;
import com.todolab.workspace.domain.SharedWorkspace;
import com.todolab.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Tag(name = "v1 Calendar Feed", description = "iCalendar 읽기 전용 feed API")
public class CalendarFeedV1Controller {

    private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarFeedService calendarFeedService;
    private final CurrentUserService currentUserService;
    private final WorkspaceService workspaceService;

    @Operation(summary = "개인 calendar feed token 발급", description = "기존 활성 token을 폐기하고 새 iCalendar feed token을 발급합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/v1/calendar-feed/token")
    public ResponseEntity<ApiResponse<CalendarFeedTokenResponse>> issueToken(@AuthenticationPrincipal Jwt jwt) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(calendarFeedService.issueToken(owner)));
    }

    @Operation(summary = "개인 calendar feed token 폐기", description = "로그인 사용자의 활성 iCalendar feed token을 모두 폐기합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/v1/calendar-feed/token")
    public ResponseEntity<ApiResponse<Void>> revokeToken(@AuthenticationPrincipal Jwt jwt) {
        User owner = currentUserService.requireUser(jwt);
        calendarFeedService.revokeTokens(owner);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Workspace calendar feed token 발급", description = "ACTIVE 멤버가 해당 workspace 읽기 전용 iCalendar feed token을 발급합니다. 기존 활성 workspace feed token은 폐기됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/v1/workspaces/{workspaceId}/calendar-feed/token")
    public ResponseEntity<ApiResponse<CalendarFeedTokenResponse>> issueWorkspaceToken(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId
    ) {
        User member = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireReadableWorkspace(workspaceId, member);
        return ResponseEntity.ok(ApiResponse.success(calendarFeedService.issueWorkspaceToken(member, workspace)));
    }

    @Operation(summary = "Workspace calendar feed token 폐기", description = "ACTIVE 멤버가 자신이 발급한 해당 workspace iCalendar feed token을 모두 폐기합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/v1/workspaces/{workspaceId}/calendar-feed/token")
    public ResponseEntity<ApiResponse<Void>> revokeWorkspaceToken(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId
    ) {
        User member = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireReadableWorkspace(workspaceId, member);
        calendarFeedService.revokeWorkspaceTokens(member, workspace);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "개인 calendar feed 조회", description = "발급된 opaque token으로 읽기 전용 iCalendar feed를 반환합니다.")
    @GetMapping("/api/v1/calendar-feeds/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        return calendarFeedService.renderFeed(token)
                .map(body -> ResponseEntity.ok()
                        .contentType(TEXT_CALENDAR)
                        .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"todolab.ics\"")
                        .body(body))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
