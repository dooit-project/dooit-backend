# API Error Codes

Last updated: 2026-07-25

이 문서는 모바일과 운영자가 함께 보는 ToDoLab v1 API 오류 코드 카탈로그다. 모든 API 오류 응답은 공통 envelope를 사용한다.

```json
{
  "status": "fail",
  "data": null,
  "error": {
    "code": 11002,
    "message": "인증이 필요합니다."
  },
  "timestamp": "2026-07-25T00:00:00"
}
```

## 코드 목록

| ErrorCode | HTTP | Message | 발생 조건 | 모바일 처리 | Retry |
| --- | --- | --- | --- | --- | --- |
| `10001` | 400 | `값이 올바르지 않습니다.` | validation 실패, enum/date/type mismatch, JSON 파싱 실패 | 입력값 확인 후 재요청 | 아니오 |
| `10002` | 400 | `필수값이 없습니다.` | 필수 요청값 누락 | 입력값 확인 후 재요청 | 아니오 |
| `10003` | 404 | `요청한 리소스를 찾을 수 없습니다.` | 존재하지 않는 API/static path | 화면 갱신 또는 이전 화면 이동 | 아니오 |
| `11001` | 401 | `이메일 또는 비밀번호가 올바르지 않습니다.` | 로그인 인증 정보 불일치 | 안전한 로그인 실패 문구 노출 | 아니오 |
| `11002` | 401 | `인증이 필요합니다.` | access token 없음, 만료, 위변조 | 저장 토큰 삭제 후 로그인 유도 | 아니오 |
| `11003` | 403 | `접근 권한이 없습니다.` | 인증은 됐지만 권한 부족 | 권한 오류 표시 | 아니오 |
| `11004` | 429 | `게스트 계정 생성 요청이 너무 많습니다.` | 게스트 계정 생성 rate limit 초과 | 대기 후 재시도 안내 | 예 |
| `20001` | 404 | `일정을 찾을 수 없습니다.` | Task 없음 또는 owner scope 밖 | 목록 재조회 | 아니오 |
| `20002` | 409 | `Today 목록이 변경되었습니다. 새로고침 후 다시 시도해주세요.` | Today 일괄 재정렬 stale 목록 | Today 재조회 후 재시도 | 조건부 |
| `30001` | 404 | `D-Day 목표를 찾을 수 없습니다.` | D-Day 목표 없음 또는 owner scope 밖 | 목록 재조회 | 아니오 |
| `40001` | 409 | `이미 가입된 이메일입니다.` | 회원가입 email 중복 | 로그인 안내 또는 다른 email 입력 | 아니오 |
| `99999` | 500 | `서버 오류가 발생했습니다.` | 처리하지 못한 서버 내부 오류 | 잠시 후 재시도 안내 | 예 |

## 로깅 기준

| 범위 | Level | 기준 |
| --- | --- | --- |
| 400 validation/type/body 오류 | `warn` | 클라이언트 입력 문제로 보고 stack trace 없이 요약 기록 |
| 401/403 인증/인가 오류 | `warn` | 인증 실패 원인은 일반화해서 기록 |
| 404 owner scope 밖 또는 리소스 없음 | `warn` | id와 resource 종류 중심으로 기록 |
| 409 동시성/상태 충돌 | `warn` | 충돌 기준과 대상 id 중심으로 기록 |
| 500 미처리 예외 | `error` | stack trace 포함 |
| batch/mail 외부 연동 실패 | `error` | 외부 시스템 오류 원인 포함, secret 제외 |

## 개인정보 마스킹

로그에는 아래 값을 원문으로 남기지 않는다.

- access token, JWT secret, password, password hash
- email local-part 전체
- Task title/description 원문
- D-Day title 원문
- mail recipient 원문

허용되는 진단 정보:

- resource id, user id, enum 값, 날짜, task count
- email은 필요할 때 `t***@example.com` 형태로 마스킹
- 요청 body 전체 대신 실패한 field 이름과 validation message

## 장애 대응

모바일 연동 장애는 [`../mobile/MOBILE_INCIDENT_RUNBOOK.md`](../mobile/MOBILE_INCIDENT_RUNBOOK.md) 절차에 따라 기록한다.
