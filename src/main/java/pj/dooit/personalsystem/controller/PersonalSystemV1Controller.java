package pj.dooit.personalsystem.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.personalsystem.dto.PersonalSystemCreateRequest;
import pj.dooit.personalsystem.dto.PersonalSystemEvidenceResponse;
import pj.dooit.personalsystem.dto.PersonalSystemOccurrenceResponse;
import pj.dooit.personalsystem.dto.PersonalSystemPracticeResultRequest;
import pj.dooit.personalsystem.dto.PersonalSystemPracticeResultResponse;
import pj.dooit.personalsystem.dto.PersonalSystemResponse;
import pj.dooit.personalsystem.dto.PersonalSystemStatusRequest;
import pj.dooit.personalsystem.dto.PersonalSystemUpdateRequest;
import pj.dooit.personalsystem.service.PersonalSystemService;
import pj.dooit.personalsystem.service.PersonalSystemMigrationService;
import pj.dooit.personalsystem.dto.PersonalSystemMigrationResponse;
import pj.dooit.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "v1 Personal System", description = "개인 시스템·실천 증거 API")
@SecurityRequirement(name = "bearerAuth")
public class PersonalSystemV1Controller {

    private final PersonalSystemService personalSystemService;
    private final PersonalSystemMigrationService migrationService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "개인 시스템 목표 이관", description = "기존 이력과 Task를 보존해 새 Goal/Plan으로 한 번 이관합니다. 원본은 보관된 읽기 전용 이력으로 남고 이후 수정은 새 API를 사용합니다.")
    @PostMapping("/personal-systems/{systemId}/goal-migration")
    public ResponseEntity<ApiResponse<PersonalSystemMigrationResponse>> migrate(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long systemId
    ) {
        return ResponseEntity.ok(ApiResponse.success(migrationService.migrate(systemId, currentUserService.requireUser(jwt))));
    }

    @Operation(summary = "개인 시스템 생성", description = "로그인 사용자의 ACTIVE 개인 시스템을 생성합니다. 첫 버전은 ACTIVE 시스템 1개를 권장합니다.")
    @PostMapping("/personal-systems")
    public ResponseEntity<ApiResponse<PersonalSystemResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PersonalSystemCreateRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(personalSystemService.create(request, owner)));
    }

    @Operation(summary = "개인 시스템 목록 조회", description = "로그인 사용자의 개인 시스템 목록을 조회합니다.")
    @GetMapping("/personal-systems")
    public ResponseEntity<ApiResponse<List<PersonalSystemResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(personalSystemService.list(owner)));
    }

    @Operation(summary = "개인 시스템 상세 조회", description = "로그인 사용자의 개인 시스템 상세를 조회합니다.")
    @GetMapping("/personal-systems/{systemId}")
    public ResponseEntity<ApiResponse<PersonalSystemResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long systemId
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(personalSystemService.get(systemId, owner)));
    }

    @Operation(summary = "개인 시스템 수정", description = "적용일 이후 회차에 새 version을 적용합니다. 과거 회차 snapshot은 보존됩니다.")
    @RequestMapping(path = "/personal-systems/{systemId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<ApiResponse<PersonalSystemResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long systemId,
            @Valid @RequestBody PersonalSystemUpdateRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(personalSystemService.update(systemId, request, owner)));
    }

    @Operation(summary = "개인 시스템 상태 변경", description = "ACTIVE, PAUSED, ARCHIVED 상태로 변경합니다.")
    @PatchMapping("/personal-systems/{systemId}/status")
    public ResponseEntity<ApiResponse<PersonalSystemResponse>> changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long systemId,
            @Valid @RequestBody PersonalSystemStatusRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(personalSystemService.changeStatus(systemId, request, owner)));
    }

    @Operation(summary = "기간별 개인 시스템 회차 조회", description = "ACTIVE 시스템의 지정 기간 회차를 중복 없이 생성하고 조회합니다. 기간은 최대 31일입니다.")
    @GetMapping("/personal-system-occurrences")
    public ResponseEntity<ApiResponse<List<PersonalSystemOccurrenceResponse>>> getOccurrences(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(personalSystemService.getOccurrences(from, to, owner)));
    }

    @Operation(summary = "회차 실천 결과 저장", description = "회차를 PRACTICED로 기록합니다. 최소 행동도 정상 실천으로 인정합니다.")
    @PutMapping("/personal-system-occurrences/{occurrenceId}/result")
    public ResponseEntity<ApiResponse<PersonalSystemPracticeResultResponse>> putResult(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long occurrenceId,
            @Valid @RequestBody PersonalSystemPracticeResultRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(personalSystemService.putResult(occurrenceId, request, owner)));
    }

    @Operation(summary = "회차 실천 결과 취소", description = "회차 결과를 삭제하고 상태를 PENDING으로 되돌립니다.")
    @DeleteMapping("/personal-system-occurrences/{occurrenceId}/result")
    public ResponseEntity<ApiResponse<Void>> deleteResult(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long occurrenceId,
            @RequestParam(required = false) Long occurrenceVersion
    ) {
        User owner = currentUserService.requireUser(jwt);
        personalSystemService.deleteResult(occurrenceId, occurrenceVersion, owner);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "회차 휴식 기록", description = "회차를 RESTED로 기록합니다. 시스템 전체 일시정지와 별개입니다.")
    @PutMapping("/personal-system-occurrences/{occurrenceId}/rest")
    public ResponseEntity<ApiResponse<PersonalSystemOccurrenceResponse>> rest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long occurrenceId,
            @RequestParam(required = false) Long occurrenceVersion
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(personalSystemService.rest(occurrenceId, occurrenceVersion, owner)));
    }

    @Operation(summary = "회차 휴식 취소", description = "RESTED 회차를 PENDING으로 되돌립니다.")
    @DeleteMapping("/personal-system-occurrences/{occurrenceId}/rest")
    public ResponseEntity<ApiResponse<Void>> cancelRest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long occurrenceId,
            @RequestParam(required = false) Long occurrenceVersion
    ) {
        User owner = currentUserService.requireUser(jwt);
        personalSystemService.cancelRest(occurrenceId, occurrenceVersion, owner);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "개인 시스템 증거 조회", description = "AI 호출 없이 원본 회차와 기간 집계를 반환합니다.")
    @GetMapping("/personal-systems/{systemId}/evidence")
    public ResponseEntity<ApiResponse<PersonalSystemEvidenceResponse>> getEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long systemId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String timeZone
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(personalSystemService.getEvidence(systemId, from, to, timeZone, owner)));
    }
}
