# Dooit Backend Roadmap

Last updated: 2026-09-03

이 문서는 완료 이력 보관소가 아니라 **앞으로 닫아야 할 백엔드/운영 작업 목록**이다. 이미 구현된 API 계약과 운영 절차의 세부 내용은 각 계약 문서와 runbook을 원본으로 본다.

## 1. 현재 기준

- 모바일 API 기준 경로는 `/api/v1/**`다.
- 인증은 `/api/v1/**` 모바일/웹 클라이언트 API에서 Bearer JWT를 사용한다.
- API 원본 계약은 실행 중인 백엔드의 `/v3/api-docs` OpenAPI JSON이다.
- Java base package는 `pj.dooit`, Gradle project name은 `Dooit`이다.
- local production은 이 Mac의 Docker Compose와 external MySQL volume을 사용한다.
- production app port는 `127.0.0.1:8080`에만 bind하고, 외부 API는 Cloudflare Tunnel의 `https://dooitapi.hsng.pe.kr`로만 연다.
- staging은 현재 운영하지 않는다. 환경은 local 개발 환경과 이 PC의 production 환경으로만 구분한다.
- 실제 secret, access token, DB dump 내용, private production URL은 문서에 기록하지 않는다.

### 2026-08-30 정리

현재 남은 P0는 production 접근성과 운영 복구성 검증, 그리고 프론트의 `오늘 계획 -> 실행 -> 하루 마감` 흐름을 막는 B0 계약에 몰려 있다.

| 구분 | 현재 상태 | 다음에 닫을 일 |
| --- | --- | --- |
| 빠른 등록 | API와 규칙 기반 파싱 구현 완료. 상대 주 표현, 한국어 날짜, 축약 상대일, 시 반/콜론 시간 표현 포함 | 모바일 실제 입력 로그 기반 예외 표현 보강 |
| 빠른 등록 템플릿 | personal template CRUD와 template 기반 Task 생성 구현 완료 | 모바일 UI 연동 후 누락 필드가 있으면 계약 보강 |
| 일일 계획 | `daily-plans` resource, focus 1~3개, 계획 확정/마감 상태, 결과 summary 구현 완료 | 모바일 연동 후 하루 마감 UX에서 batch mutation 필요 여부 확인 |
| 예상 소요 시간 | Task nullable 필드와 template 기본값 적용 구현 완료 | 모바일 연동 후 입력 preset/표시 정책 검증 |
| Checklist | 개인/Workspace Task 하위 한 단계 checklist item API 구현 완료 | 모바일 연동 후 담당자/알림 같은 추가 필드 필요 여부 결정 |
| 카테고리 탐색 | 개인 Task category 목록/count API 구현 완료 | 모바일 연동 후 수동 category order 필요 여부 결정 |
| 공유 workspace | 설계와 1차 Task/D-Day API 구현 완료 | 모바일 연동 과정에서 권한/초대 UX 검증 |
| 서버 push | token, 후보, 이력, provider 설정, Expo client, idempotency, invalid token 처리, 개인 owner와 shared workspace scheduler 자동 발송 완료 | 운영 credential 적용 후 실수신 smoke |
| production 접근 | Cloudflare Tunnel로 Web·API 도메인 연결, HTTPS 강제, readiness·CORS public smoke 완료 | Android 실제 기기 smoke |
| 운영 복구성 | launchd, Docker health, readiness, Tailscale recovery check 통과 | 전원 정책 적용과 재부팅/Docker 재시작 실검증 |
| backup | local routine backup 검증 통과 | offsite backup 위치 결정과 restore 연습 |

## 2. 제품 기능 로드맵

운영 접근 경로가 막혀 있더라도 backend 설계와 API 구현을 병행할 수 있는 제품 기능이다. 모바일 UX 영향, 데이터 모델 변경 범위, 공유/권한 모델의 blast radius를 기준으로 순서를 둔다.

### P0. 프론트 출시 연동 요청

목표: 프론트 real API 연결과 출시 검증을 막는 backend 계약/배포 gap을 우선 닫는다. 완료 보고에는 backend commit SHA 또는 image tag, 배포 환경/API URL, 적용 migration, 변경 OpenAPI endpoint/schema/error code, 호환성, 실행 테스트, 프론트 확인 사항을 함께 남긴다.

- [x] 내용이 있는 Workspace 삭제가 500을 반환하지 않도록 삭제 정책을 구현하고 `SHARING_CONTRACT.md`와 OpenAPI에 반영한다.
- [x] staging 미운영, production host 내부 URL, Tailscale/public HTTPS URL 환경변수 기준, readiness 공개 접근을 문서화한다.
- [x] backend commit/image metadata endpoint를 구현한다.
- [ ] Android 실제 기기에서 production HTTPS URL을 smoke한다.
- [x] production Web origin 사용 여부와 실제 origin을 확정한다.
- [x] 비밀번호 재설정 request/verify/confirm API, reset link 형식, TTL, rate limit, error code, session 처리 정책을 구현한다.
- [x] 운영 Web CORS allow header와 인증/API 응답 `Cache-Control: no-store` 정책을 확정한다.

### P1. 프론트 계약 후속 요청

- [x] 주요 생성 API에 `Idempotency-Key` 처리와 24시간 replay 저장 정책을 추가한다.
- [x] 등록 계정 refresh, guest refresh, logout, refresh token rotation/reuse detection, guest 90일 보존 정책을 설계/구현한다.
- [x] Task별 알림 preference와 notification candidate `notifyAt`을 추가한다.
- [x] Workspace PENDING 초대 거절 계약과 권한 테스트를 추가한다.

### B0. 일일 계획 영속화

목표: 모바일의 `오늘 계획 -> 실행 -> 하루 마감` 흐름에서 사용자가 오늘의 핵심으로 확정한 1~3개 Task와 계획 상태를 서버에 저장한다.

상태: 구현 완료. 계약은 `docs/api/DAILY_PLANNING_CONTRACT.md`를 원본으로 본다.

- [x] `GET /api/v1/daily-plans/{date}` 계약을 추가한다.
- [x] `PUT /api/v1/daily-plans/{date}` 계약을 추가한다.
- [x] `DailyPlanStatus`를 `DRAFT`, `CONFIRMED`, `CLOSED`로 정의한다.
- [x] `focusTaskIds`는 같은 사용자, 같은 날짜, 미완료 Today Task만 허용한다.
- [x] 최대 3개와 배열 순서 보존을 validation으로 고정한다.
- [x] Task 완료, 삭제, Inbox 이동, 다른 날짜 이동 시 focus 목록에서 자동 제거한다.
- [x] 같은 사용자와 날짜에 하나의 plan만 존재하도록 DB 제약을 둔다.
- [x] `PUT` 전체 교체 방식으로 멱등성을 보장한다.
- [x] 사용자 timezone 기준 local date 처리와 integration test를 추가한다.
- [x] Workspace Task는 1차 범위에서 제외한다.

선행 조건:

- `DAILY_PLANNING_CONTRACT.md` 초안을 프론트와 합의한다.
- API 추가는 non-breaking change로 진행한다.

### B0. 예상 소요 시간

목표: 오늘 계획 수립과 실행량 조절을 위해 Task 단위 예상 소요 시간을 저장하고 응답한다.

상태: 구현 완료. Task와 TaskTemplate 계약에 반영했다.

- [x] `TASK.estimated_duration_minutes` nullable column migration을 추가한다.
- [x] `TaskRequest`, `TaskResponse`, `TaskSearchItemResponse`에 `estimatedDurationMinutes`를 추가한다.
- [x] 허용 범위 5~1440분 validation을 추가한다.
- [x] `null`은 사용자가 시간을 정하지 않은 상태로 유지한다.
- [x] `SCHEDULE`의 `startAt`/`endAt` 길이를 예상 시간에 자동 중복 저장하지 않는다.
- [x] `TaskTemplate.defaultDurationMinutes`가 template 기반 Task의 `estimatedDurationMinutes` 기본값으로 적용되는지 계약을 정리한다.
- [x] 반복 series/occurrence 수정 범위와 예상 시간 적용 규칙을 테스트한다.
- [x] Today 응답에는 합계 필드를 추가하지 않고 Task별 값을 내려준다.

선행 조건:

- 기존 template의 `defaultDurationMinutes` 의미와 Task 예상 시간의 관계를 프론트와 합의한다.

### P2. 조건부 프론트 요청

- [ ] 서버 push 기기 등록과 발송 멱등성은 로컬 예약 알림 한계가 실제로 확인된 뒤 도입한다.
- [ ] Workspace 템플릿은 제품 필요성이 확정된 뒤 별도 scope/권한/반복/D-Day 정책을 먼저 계약한다.

### B1. 계획/마감 Batch Mutation

목표: 하루 마감 중 여러 Task 이동/미룸/완료 변경을 한 번에 적용해야 할 때 atomic endpoint를 제공한다.

상태: 보류. 프론트 MVP는 기존 단건 mutation으로 먼저 검증한다.

- [ ] 부분 성공 문제가 실제 사용자 흐름에서 확인되면 `POST /api/v1/daily-plans/{date}/apply`를 추가한다.
- [ ] operation 전체 성공 또는 전체 rollback을 transaction으로 보장한다.
- [ ] 중복 Task ID, 권한 없음, 리소스 없음, 상태 충돌을 400/403/404/409로 구분한다.
- [ ] `Idempotency-Key`를 지원한다.
- [ ] 응답에는 갱신된 Task와 plan을 포함한다.

### B1. Checklist

목표: 깊은 subtask가 아니라 Task 아래 한 단계 checklist item으로 실행 단위를 쪼갠다.

상태: 구현 완료. 1차 범위는 개인 Task에 한정한다.

- [x] Task 상세 response 포함 방식과 별도 checklist endpoint 방식 중 하나를 선택한다.
- [x] item 생성, 제목 수정, 완료, 재개, 삭제 API를 설계한다.
- [x] 한 Task 안의 checklist item 정렬 방식을 설계한다.
- [x] 부모 Task 완료 시 미완료 item 처리 정책을 결정한다.
- [x] 반복 Task occurrence에서 checklist 복제와 수정 범위를 결정한다.
- [x] Workspace Task 권한은 기존 OWNER/EDITOR/VIEWER 계약과 일치시킨다.

### B2. 일일 결과 Summary

목표: 여러 조회 조합 비용이나 기기 간 결과 불일치가 실제로 확인될 때 하루 결과 요약을 제공한다.

상태: 구현 완료. 생산성 점수나 ranking 없이 focus Task 결과 count만 제공한다.

- [x] `GET /api/v1/daily-plans/{date}/summary`를 추가한다.
- [x] 계획 시점 focus 수, 완료 수, 다른 날짜 이동 수, Inbox 이동 수, 미결정 수만 1차 범위로 둔다.
- [x] 생산성 점수, 연속 달성, 비교 ranking은 범위에서 제외한다.

### P0. 일정 빠른 등록 API

목표: 모바일과 서버 화면에서 같은 backend API로 빠르게 일정을 입력한다. 첫 버전은 실패해도 안전하게 Inbox Task로 저장되는 보수적 파싱을 기준으로 한다.

상태: API와 회귀 테스트는 닫혔다. 현재 규칙은 상대 날짜, 축약 상대일, ISO 날짜, 한국어 날짜, 슬래시 날짜, 단독 요일, 주 단위 상대 요일, 매주 요일, 오전/오후 시간, 시 반, 콜론 시간 표현을 지원한다. 남은 작업은 실제 모바일 입력 로그를 반영한 파싱 coverage 확장이다.

- [x] `POST /api/v1/tasks/quick-capture` 계약을 추가한다.
- [x] request는 원문 `text`, 기준 날짜, 사용자 timezone, 선택적 기본 category를 받는다.
- [x] “내일 3시 회의”, “매주 월요일 운동” 같은 입력에서 title/date/time/recurrence 후보를 파싱한다.
- [x] 파싱 확신도가 낮으면 원문 title의 Inbox `TODO`로 저장한다.
- [x] 생성 결과에 `parsed` 여부와 적용된 date/time/type/recurrence 요약을 반환한다.
- [x] 모바일이 확인 화면을 띄울 수 있도록 원문과 서버 해석 결과를 함께 내려준다.
- [x] 파싱 실패가 데이터 손실이나 5xx로 이어지지 않도록 회귀 테스트를 추가한다.
- [x] “금요일 병원”처럼 단독 요일 표현을 날짜로 해석하는 규칙을 추가한다.
- [x] “낼모레 오후 3시 반 치과”, “다다음주 화요일 14:30 발표 준비” 같은 축약 상대일/시 반/콜론 시간 표현을 추가한다.
- [ ] 모바일 실제 입력 로그를 바탕으로 날짜/시간 표현 coverage를 확장한다.

선행 조건:

- 사용자 timezone 기준 날짜 경계는 현재 구현된 `User.timeZone`을 사용한다.
- 자연어 파서는 처음부터 완전한 한국어 NLP로 만들지 않고, 규칙 기반으로 시작한다.

### P0. 빠른 등록 템플릿

목표: 자주 쓰는 일정과 할 일을 한 번의 선택으로 생성할 수 있게 한다. 자연어 파싱보다 예측 가능하고 모바일 반복 사용성에 바로 도움을 준다.

상태: backend 1차 구현은 닫혔다. 모바일 화면 연동 중 추가 필드가 필요해질 때 계약을 보강한다.

- [x] `TASK_TEMPLATE` 모델을 추가한다.
- [x] `GET /api/v1/task-templates`, `POST /api/v1/task-templates`, `PUT /api/v1/task-templates/{id}`, `DELETE /api/v1/task-templates/{id}`를 추가한다.
- [x] template에는 title, type, category, allDay, default time, recurrence preset을 둔다.
- [x] `POST /api/v1/task-templates/{id}/tasks`로 template 기반 Task를 생성한다.
- [x] owner scope와 guest 승격/병합/cleanup 대상에 template을 포함한다.
- [x] D-Day 연결 템플릿 정책을 결정한다.
- [x] 공유 workspace 템플릿 정책을 공유 설계 단계에서 결정한다.

선행 조건:

- 빠른 등록 API와 request/response 형태를 맞춰 모바일 입력 UI가 둘을 함께 사용할 수 있어야 한다.

### P1. 일정 공유 설계

목표: 개인 owner 모델을 깨지 않고 공유 캘린더/공유 Task를 도입할 수 있는 권한 모델을 먼저 확정한다.

상태: 설계와 invariant 테스트 기준은 닫혔다. 구현 세부 계약은 `docs/api/SHARING_CONTRACT.md`를 원본으로 본다.

- [x] 공유 단위 결정: 1차 범위는 workspace 안의 Task/D-Day로 제한한다.
- [x] 권한 모델 결정: OWNER, EDITOR, VIEWER와 PENDING/ACTIVE/REMOVED membership을 사용한다.
- [x] 초대 방식 결정: 1차 범위는 email 초대이며 invite link는 제외한다.
- [x] 공유된 Task의 반복 series, D-Day 연결, push token, 알림 이력 소유권 정책을 문서화한다.
- [x] 기존 owner scope API와 공유 조회 API를 분리한다.
- [x] migration과 rollback 전략을 먼저 문서화한다.
- [x] `docs/api/SHARING_CONTRACT.md` 기준으로 구현 전후 invariant 테스트를 추가한다.

선행 조건:

- 개인 데이터 격리 invariant를 깨지 않는 repository query 정책을 테스트로 고정한다.
- 공유 기능은 바로 구현하지 않고 설계 문서와 테스트 전략부터 커밋한다.

### P1. 일정 공유 1차 구현

목표: 가장 작은 공유 단위를 실제 API로 연다. 1차 범위는 “공유 workspace 안의 Task/D-Day를 멤버가 조회”로 제한한다.

상태: backend 1차 구현은 닫혔다. 모바일 연동 단계에서는 개인 API와 workspace API가 섞이지 않는지, VIEWER/EDITOR 권한 UX가 맞는지 검증한다.

- [x] `SHARED_WORKSPACE`, `WORKSPACE_MEMBER` 모델과 migration을 추가한다.
- [x] workspace 생성, 멤버 초대, 수락, 제거 API를 추가한다.
- [x] Task/D-Day/반복 series에 `PERSONAL`/`WORKSPACE` scope 컬럼을 추가하고 개인 API 격리 테스트를 추가한다.
- [x] shared workspace Task 생성/조회 API를 개인 Task API와 명확히 구분한다.
- [x] viewer/editor 권한에 따른 Task 생성/조회 제한을 적용한다.
- [x] shared workspace D-Day 생성/조회 API를 개인 D-Day API와 명확히 구분한다.
- [x] shared workspace Task 수정/삭제 API를 추가한다.
- [x] shared workspace D-Day 삭제와 Task 연결 API를 추가한다.
- [x] workspace 반복 Task materialize를 구현한다.
- [x] workspace 알림 후보 API를 추가한다.
- [x] 게스트 계정은 공유 workspace 초대 대상에서 제외하거나 제한 정책을 둔다.
- [x] 감사 로그 또는 최소한 생성/수정자 필드를 남긴다.

선행 조건:

- P1 일정 공유 설계가 닫혀야 한다.

### P1. 서버 push 실제 발송

목표: 현재 구현된 push token, 알림 후보, 발송 이력 기반 위에 실제 provider 전송을 붙인다.

상태: provider 설정, 이력 저장 계약, Expo 단건 발송 client, 개인 owner와 shared workspace scheduler 기반 자동 발송까지 닫혔다. 남은 작업은 운영 credential 적용 뒤 실수신 smoke다.

- [x] Expo/APNs/FCM 중 production provider와 credential 보관 방식을 확정한다.
- [x] 발송 스케줄러 실행 시점과 look-ahead window를 정한다.
- [x] idempotency key 기준으로 중복 발송을 막는다.
- [x] provider 실패 응답에 따라 token 비활성화 정책을 적용한다.
- [x] Expo Push Service 단건 발송 client와 ticket 해석을 추가한다.
- [x] scheduler 기반 자동 발송을 구현한다.
- [x] shared workspace push 수신자/멤버별 중복 정책을 확정하고 자동 발송 범위에 포함한다.
- [x] 모바일 로컬 알림과 중복되지 않도록 `suppressLocalNotification` 정책을 검증한다.

선행 조건:

- production credential 관리 방식이 확정되어야 한다.

### P1. 검색/추천 고도화

목표: 이미 있는 검색과 Today 추천을 실제 사용 패턴에 맞게 개선한다.

- [x] 최근 완료/이월/미룸 패턴 기반 Today 추천 점수를 추가한다.
- [x] category와 D-Day 제목을 `q` 검색 매칭 범위에 포함한다.
- [x] title, category, D-Day 제목, description 순서로 검색 매칭 ranking을 적용한다.
- [x] 검색 결과에 `matchedFields`와 `highlight`를 반환한다.
- [x] 반복 여부 필터와 반복 매칭 ranking을 추가한다.
- [x] 모바일에서 빈 검색 결과일 때 추천 query/category를 반환한다.

### P1. 외부 캘린더 읽기 전용 feed

목표: Google Calendar, Apple Calendar 같은 외부 캘린더 앱에서 개인 Dooit 일정을 읽기 전용으로 구독할 수 있게 한다.

- [x] 개인 iCalendar feed token 발급/폐기 API를 추가한다.
- [x] token 원본은 저장하지 않고 hash만 저장한다.
- [x] 인증 없는 `GET /api/v1/calendar-feeds/{token}.ics` feed endpoint를 추가한다.
- [x] 개인 scope의 날짜 있는 미완료 Task만 feed에 포함한다.
- [x] Task 설명, category, D-Day 제목, 멤버 이름, access token은 feed에 넣지 않는다.
- [x] workspace calendar feed는 멤버별 공개 범위 정책이 확정된 뒤 별도 구현한다.

### P2. 장기 게스트 복구

목표: 31일 이상 미접속한 게스트의 데이터 복구가 제품 요구로 확정될 때 별도 인증 수단으로 해결한다.

- [x] refresh token, device-bound proof, recovery code 중 하나를 선택한다.
- [x] token 탈취 시 피해 범위와 회수 정책을 정의한다.
- [x] 이미 cleanup된 guest의 오류 코드와 UX 문구를 정한다.
- [x] 새 guest id 발급을 복구 정책으로 사용하지 않는 원칙을 유지한다.

## 3. 운영 마무리 로드맵

운영 로드맵은 실제 secret, private URL, DB dump 내용을 남기지 않고 통과한 명령과 검증 범위만 기록한다.

### P0. production 접근 경로 확정

목표: 공용 인터넷 포트포워딩 없이 Android production build가 Tailscale HTTPS로 production API에 접근한다.

- [x] Mac에서 `tailscale` CLI를 PATH에 설치하거나 `Tailscale.app` 실행 파일을 자동 감지한다.
- [ ] Mac과 Android 기기를 같은 tailnet에 연결한다.
- [x] Tailscale MagicDNS 이름을 production API 주소로 확정한다.
- [x] Tailscale Serve 또는 동등한 reverse proxy로 `https://<device>.<tailnet>.ts.net`을 `http://127.0.0.1:8080`에 연결한다.
- [x] `.env`에 `DOOIT_TAILSCALE_API_URL`을 저장하고 `DOOIT_REQUIRE_TAILSCALE_URL=true ./scripts/check-production-env.sh`를 통과시킨다.
- [x] `DOOIT_TAILSCALE_API_URL=... ./scripts/check-tailscale-production.sh`를 통과시킨다.
- [ ] Android 실제 기기에서 `GET /api/v1/auth/me`까지 HTTPS로 접근되는지 확인한다.
- [ ] Tailscale 연결이 끊긴 기기와 허가되지 않은 tailnet 사용자가 API에 접근하지 못하는지 확인한다.

증적:

- `./scripts/check-production-env.sh`
- `./scripts/check-tailscale-production.sh`
- Android production build의 `/api/v1/auth/me` 결과

### P0. Android production smoke

목표: production API URL 확정 후 모바일 핵심 흐름을 실제 기기에서 검증한다.

- [ ] Android production build에 Tailscale HTTPS API URL을 반영한다.
- [ ] `/api/v1/auth/login` 성공과 401 실패 처리를 확인한다.
- [ ] Today 조회, 생성, 완료를 확인한다.
- [ ] 게스트 발급, 게스트 `/auth/me`, 게스트 승격 또는 기존 계정 병합 흐름을 확인한다.
- [ ] 결과를 `docs/mobile/MOBILE_API_BACKEND_STATUS.md`에 날짜와 범위만 기록한다. token, 비밀번호, private URL은 기록하지 않는다.

증적:

- `DOOIT_SMOKE_BASE_URL=https://<device>.<tailnet>.ts.net ./scripts/smoke-production-api.sh`
- `docs/mobile/MOBILE_INTEGRATION_RUNBOOK.md`의 production 결과 기록 템플릿

### P0. host 상시 가용성 검증

목표: 재부팅, 재로그인, Docker Desktop 재시작 뒤 수동 코드 실행 없이 production API가 복구된다.

- [ ] `DOOIT_CONFIRM_POWER_POLICY=APPLY ./scripts/apply-production-power-policy.sh`를 관리자 권한으로 실행한다.
- [ ] `DOOIT_STRICT_POWER=true ./scripts/check-production-host.sh`를 통과시킨다.
- [x] 현재 부팅 상태에서 `./scripts/check-production-recovery.sh`를 통과시킨다.
- [x] Tailscale URL 확정 후 `DOOIT_TAILSCALE_API_URL=... ./scripts/check-production-recovery.sh`를 통과시킨다.
- [ ] 실제 재부팅 또는 로그아웃/로그인 후 `./scripts/check-production-recovery.sh`를 통과시킨다.
- [ ] Docker Desktop 재시작, 네트워크 변경, 절전 복귀 뒤 readiness와 Android 접근을 확인한다.

증적:

- `./scripts/check-production-host.sh`
- `./scripts/check-production-recovery.sh`
- `./scripts/report-production-status.sh`

### P1. offsite backup 확정

목표: PC 디스크 장애에도 복구 가능한 외부 매체 또는 신뢰할 수 있는 동기화 위치를 운영 절차에 포함한다.

- [x] local backup gzip, backup age, disk 여유 공간, readiness를 `./scripts/check-production-routine.sh`로 확인한다.
- [ ] 외부 디스크, NAS, cloud sync 중 하나를 `DOOIT_OFFSITE_BACKUP_DIR`로 결정한다.
- [ ] `DOOIT_OFFSITE_BACKUP_DIR=... ./scripts/sync-production-backup.sh`를 통과시킨다.
- [ ] `DOOIT_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh`를 통과시킨다.
- [ ] `DOOIT_OFFSITE_BACKUP_DIR=... ./scripts/check-production-routine.sh`를 통과시킨다.
- [ ] offsite 복사본에서 임시 DB restore를 1회 검증한다.

증적:

- 최신 backup gzip 검증
- SHA-256 checksum 일치
- 임시 DB restore 결과

### P1. Expo Web production origin 운영

목표: 공개 Web origin만 production API CORS에 허용하고, origin 변경 시 preflight를 다시 검증한다.

- [x] Expo Web production 배포 여부를 결정한다.
- [x] 실제 Web origin `https://dooit.hsng.pe.kr`만 `DOOIT_ALLOWED_ORIGINS`에 허용한다.
- [x] public API `https://dooitapi.hsng.pe.kr`에 대해 Web CORS preflight를 확인한다.
- [x] origin을 추가하거나 바꾸면 실제 origin만 허용하는 정책을 유지한다.

증적:

- `DOOIT_ALLOWED_ORIGINS` 설정 여부: `https://dooit.hsng.pe.kr`
- `OPTIONS /api/v1/auth/me` preflight 결과: public API와 Web origin 기준 통과

### P2. 제품 정책 후속 결정

- [x] 모바일 연결 완료 화면에서 병합 결과 count를 구체적으로 노출할지 결정한다.
- [x] TickTick처럼 좌측 상단 메뉴에서 Inbox, Today, Calendar, category를 한 번에 탐색하는 UX를 채택할 때 쓸 category 목록/count API를 추가한다.
- [x] 채택 시 기존 검색 `category` 필터와 추천 category만으로 충분한지, 별도 category 목록/count/order API가 필요한지 계약한다.
- [ ] 게스트가 31일 이상 미접속한 뒤 기존 데이터를 복구해야 하는지 결정한다.
- [x] 장기 게스트 복구가 필요하면 refresh token, device-bound proof, recovery code 중 별도 인증 수단을 먼저 설계한다.
- [ ] 서버 push 운영 credential을 실제 production에 적용할지 결정한다.

## 4. 현재 완료된 기준

아래 항목은 현재 코드와 문서 기준으로 닫힌 상태다. 상세 계약은 관련 문서에서 관리한다.

- v1 Auth, Task, D-Day, 검색, Today 재정렬, 반복, timezone, 알림 후보 API 계약
- owner scope와 JWT 인증/인가 계약
- 게스트 계정 생성, refresh, 승격, 기존 계정 병합, 병합 결과 count, rate limit, 만료 정리
- production DB migration 적용 이력과 수동 migration 관리 방식
- local production Docker Compose, readiness, rollback, backup/restore, routine/recovery/status report 스크립트
- API logging masking, UTF-8 response logging, 운영 문서 UI 비공개 기준

관련 문서:

- [`../api/API_V1_FRONTEND.md`](../api/API_V1_FRONTEND.md)
- [`../api/AUTH_CONTRACT.md`](../api/AUTH_CONTRACT.md)
- [`../api/GUEST_ACCOUNT_HANDOFF.md`](../api/GUEST_ACCOUNT_HANDOFF.md)
- [`../ops/LOCAL_PRODUCTION_RUNBOOK.md`](../ops/LOCAL_PRODUCTION_RUNBOOK.md)
- [`../ops/ENVIRONMENT_INTEGRATION.md`](../ops/ENVIRONMENT_INTEGRATION.md)
- [`../mobile/MOBILE_API_BACKEND_STATUS.md`](../mobile/MOBILE_API_BACKEND_STATUS.md)
- [`../db/MIGRATION_HISTORY.md`](../db/MIGRATION_HISTORY.md)

## 5. 기본 검증 명령

```bash
./gradlew test
./scripts/check-production-env.sh
./scripts/check-production-routine.sh
./scripts/check-production-recovery.sh
./scripts/report-production-status.sh
```

Tailscale URL과 offsite backup 경로가 확정된 뒤에는 strict 검증을 추가한다.

```bash
DOOIT_REQUIRE_TAILSCALE_URL=true ./scripts/check-production-env.sh
DOOIT_TAILSCALE_API_URL=https://<device>.<tailnet>.ts.net ./scripts/check-tailscale-production.sh
DOOIT_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh
DOOIT_OFFSITE_BACKUP_DIR=/absolute/offsite/path ./scripts/check-production-routine.sh
```

## 6. 문서 유지 원칙

- 완료 이력은 로드맵에 길게 누적하지 않고 관련 계약 문서 또는 migration 이력으로 이동한다.
- 미정인 실제 값은 추측하지 않고 `미정` 또는 `확정 필요`로 둔다.
- secret, access token, DB dump 내용, private production URL은 문서와 로그에 남기지 않는다.
- API 계약 변경은 OpenAPI JSON, `API_V1_FRONTEND.md`, 관련 테스트를 함께 갱신한다.
