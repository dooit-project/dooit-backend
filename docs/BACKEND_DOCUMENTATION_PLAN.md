# Backend Documentation Plan

Last updated: 2026-07-15

이 문서는 백엔드 개발자가 프론트엔드, 모바일, 운영자와 안정적으로 협업하기 위해 유지해야 할 문서 목록과 우선순위를 정리한다.

## 1. 현재 문서 기준

| 문서 | 역할 | 상태 |
| --- | --- | --- |
| `README.md` | 로컬 개발, 빌드, API 문서 endpoint 안내 | 유지 |
| `docs/API_V1_FRONTEND.md` | 모바일/프론트 연동용 v1 요약 계약 | 유지, OpenAPI와 동기화 필요 |
| `docs/MOBILE_API_BACKEND_STATUS.md` | 모바일 요구사항 대비 백엔드 구현 상태 | 유지, 테스트 결과 반영 필요 |
| `docs/ENVIRONMENT_INTEGRATION.md` | 환경별 API URL, CORS origin, 문서 UI 공개 기준 | 신규, staging/prod URL 확정 후 갱신 필요 |
| `docs/MOBILE_INTEGRATION_RUNBOOK.md` | 모바일 real-mode smoke test 절차와 결과 기록 방식 | 신규, 실제 테스트 결과 누적 필요 |
| `docs/API_COMPATIBILITY_POLICY.md` | v1 호환성, breaking change, deprecation, OpenAPI 변경 검토 정책 | 신규, 외부 클라이언트 확장 시 갱신 필요 |
| `docs/RECURRENCE_MODEL.md` | 반복 series와 occurrence Task 내부 모델 | 신규, 반복 API 확정 시 갱신 필요 |
| `docs/NOTIFICATION_CONTRACT.md` | 반복 occurrence와 모바일 로컬 알림 책임 계약 | 신규, 알림 API 도입 시 갱신 필요 |
| `docs/AUTH_CONTRACT.md` | JWT 인증, 토큰 TTL, refresh token, 로그아웃, 401/403 계약 | 신규, refresh token 도입 시 갱신 필요 |
| `docs/API_ERROR_CODES.md` | 오류 코드, HTTP status, 모바일 처리, retry, logging/masking 기준 | 신규, ErrorCode 변경 시 갱신 필요 |
| `docs/MOBILE_INCIDENT_RUNBOOK.md` | 모바일 real-mode 장애 접수와 백엔드 확인 절차 | 신규, 장애 대응 결과 누적 필요 |
| `docs/DATA_MODEL_GLOSSARY.md` | Task, D-Day, User 주요 필드와 상태 전이 | 신규, 도메인 필드 추가 시 갱신 필요 |
| `docs/TIMEZONE_CONTRACT.md` | 현재 Asia/Seoul 기준과 향후 사용자 timezone 도입 계약 | 신규, 사용자 timezone 도입 시 갱신 필요 |
| `docs/ROADMAP.md` | 앞으로의 백엔드 작업 우선순위 | 유지 |
| `/v3/api-docs` | 기계 판독 가능한 OpenAPI 원본 계약 | 신규 기준 |
| `/swagger-ui` | 개발자 테스트용 OpenAPI UI | 신규 기준 |
| `/scalar.html` | 읽기 좋은 API reference UI | 신규 기준 |

## 2. 우선 작성할 문서

### 2.1 API 연동 규격서

목적: 프론트엔드와 모바일이 API를 구현할 때 보는 사람이 읽는 계약서.

파일 제안:

- 현재: `docs/API_V1_FRONTEND.md`
- 장기: `docs/API_INTEGRATION_SPEC.md`로 이름을 명확히 하거나 현재 파일을 유지

포함해야 할 내용:

- base URL과 base path
- 인증 방식
- 공통 request header
- 공통 response envelope
- 공통 오류 envelope
- 날짜/시간 형식과 시간대
- endpoint별 request/response
- nullable 필드 기준
- pagination/cursor 기준
- idempotency, 동시성, 409 기준

상태: 현재 `docs/API_V1_FRONTEND.md` 유지, 공통 계약 문서와 동기화 완료

### 2.2 오류 코드 카탈로그

목적: 모바일에 노출 가능한 안전한 오류 문구와 서버 내부 오류를 분리한다.

파일 제안:

- `docs/API_ERROR_CODES.md`

포함해야 할 내용:

- error code
- HTTP status
- 사용자 노출 message
- 발생 조건
- 모바일 권장 처리
- retry 가능 여부
- 로그에 남길 내부 진단 정보

상태: 초안 작성 완료, `ErrorCode` 변경 시 갱신 필요

### 2.3 인증/인가 계약서

목적: 웹 세션 인증과 모바일 JWT 인증의 책임을 명확히 한다.

파일 제안:

- `docs/AUTH_CONTRACT.md`

포함해야 할 내용:

- 회원가입, 로그인, 내 정보 API
- JWT claim: `sub`, `email`, `role`, `iss`, `exp`
- access token TTL
- refresh token 도입 여부
- 401과 403 기준
- 로그아웃의 서버/클라이언트 책임
- 사용자 소유권 격리 원칙

상태: 초안 작성 완료, refresh token 도입 시 갱신 필요

### 2.4 환경별 연동 가이드

목적: 로컬, 스테이징, 운영에서 어떤 URL과 CORS 설정을 쓰는지 명확히 한다.

파일:

- `docs/ENVIRONMENT_INTEGRATION.md`

포함해야 할 내용:

- local/staging/prod API URL
- 모바일 `.env.local` 예시
- Expo Web, iOS Simulator, Android Emulator, 실제 기기별 URL
- `TODOLAB_ALLOWED_ORIGINS` 설정 예시
- Swagger UI/Scalar 공개 범위
- 운영 secrets 관리 원칙

상태: 초안 작성 완료, staging/prod URL 확정 후 갱신 필요

### 2.5 모바일 연동 테스트 Runbook

목적: “붙여봤다”를 재현 가능한 절차와 결과로 남긴다.

파일:

- `docs/MOBILE_INTEGRATION_RUNBOOK.md`

포함해야 할 내용:

- 사전 조건
- 백엔드 실행 명령
- 모바일 mock/real 전환 방법
- 회원가입/로그인 smoke test
- Today/Calendar/D-Day smoke test
- CORS preflight 확인 방법
- 실패 시 확인할 로그와 설정
- 결과 기록 템플릿

상태: 초안 작성 완료, real-mode 결과 누적 필요

### 2.6 데이터 모델 사전

목적: Task, D-Day, User의 상태와 날짜 필드 의미를 팀이 동일하게 이해한다.

파일 제안:

- `docs/DATA_MODEL_GLOSSARY.md`

포함해야 할 내용:

- `TaskStatus`: `INBOX`, `TODAY`, `DONE`
- `TaskType`: `TODO`, `SCHEDULE`, `IDEA`
- `plannedDate`, `targetDate`, `startAt`, `endAt`, `completedAt`
- `DdayGoal`과 연결 Task
- owner scope
- 여러 날 일정 overlap 규칙
- end-exclusive 규칙

상태: 초안 작성 완료, 도메인 필드 추가 시 갱신 필요

### 2.7 릴리스와 호환성 정책

목적: API 변경이 모바일 앱 배포와 충돌하지 않게 한다.

파일:

- `docs/API_COMPATIBILITY_POLICY.md`

포함해야 할 내용:

- `/api/v1/**` 유지 정책
- breaking change 정의
- deprecation 기간
- legacy `/api/**` 제거 기준
- OpenAPI 변경 검토 방식
- 모바일 앱 버전별 지원 정책

상태: 초안 작성 완료, OpenAPI JSON snapshot diff 도입 조건 확정

## 3. 문서 작성 순서

1. `API_ERROR_CODES.md`
2. `AUTH_CONTRACT.md`
3. `ENVIRONMENT_INTEGRATION.md` 초안 작성 완료, staging/prod 확정값 반영
4. `MOBILE_INTEGRATION_RUNBOOK.md` 초안 작성 완료, smoke test 결과 누적
5. `API_V1_FRONTEND.md`와 OpenAPI JSON 대조 정리
6. `DATA_MODEL_GLOSSARY.md`
7. `API_COMPATIBILITY_POLICY.md` 초안 작성 완료

## 4. 문서 유지 원칙

- OpenAPI JSON은 기계 판독 가능한 원본 계약이다.
- 사람이 읽는 문서는 OpenAPI를 보완하되, 서로 다른 계약을 만들지 않는다.
- API 변경 PR에는 관련 문서와 테스트 변경이 함께 있어야 한다.
- 모바일 연동 실패가 발생하면 원인, 수정, 검증 결과를 runbook 또는 status 문서에 남긴다.
- 운영 설정 값 자체는 문서에 쓰지 않고, 필요한 환경변수 이름과 예시만 기록한다.
