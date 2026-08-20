# ToDoLab API Compatibility Policy

Last updated: 2026-08-21

이 문서는 `/api/v1/**` 모바일 API를 변경할 때 지켜야 할 호환성 기준과 OpenAPI 변경 검토 방식을 정리한다.

## 1. v1 유지 정책

- 모바일이 사용하는 안정 API는 `/api/v1/**`이다.
- 기존 v1 endpoint의 path, method, request field, response field, error envelope는 호환성을 유지한다.
- 새 기능은 가능한 한 기존 v1 계약을 깨지 않고 field 또는 endpoint를 추가하는 방식으로 확장한다.
- 기존 `/api/**` legacy endpoint는 과거 연동 호환을 위해 유지하되, 모바일/웹 클라이언트 신규 기능에는 사용하지 않는다.
- 모바일 호환을 위해 legacy `/api/ddays/**` alias를 새로 추가하지 않는다. 모바일은 D-Day API를 `/api/v1/dday-goals/**`로 전환한다.
- legacy endpoint 제거는 과거 클라이언트 사용 여부와 운영 로그를 확인한 뒤 별도 deprecation 절차로 진행한다.

## 2. Breaking Change 기준

아래 변경은 breaking change로 본다.

- 기존 endpoint path 또는 HTTP method 변경
- 기존 request field 삭제, 이름 변경, 타입 변경
- optional request field를 required로 변경
- response field 삭제, 이름 변경, 타입 변경
- enum 값 삭제 또는 의미 변경
- 날짜/시간 형식 변경
- 성공/실패 envelope 구조 변경
- 기존 오류 code 또는 HTTP status의 의미 변경
- owner scope, 인증 필요 여부, 권한 기준을 더 엄격하게 바꾸는 변경

아래 변경은 일반적으로 non-breaking change로 본다.

- 새 endpoint 추가
- optional request field 추가
- nullable response field 추가
- enum 값 추가. 단, 모바일이 unknown enum을 안전하게 처리할 수 있는지 확인해야 한다.
- 더 구체적인 error response 문서 추가

## 3. 변경 절차

API 계약 변경 PR에는 아래를 함께 반영한다.

- Controller/DTO/OpenAPI annotation 변경
- 관련 테스트
- `docs/api/API_V1_FRONTEND.md`
- 필요하면 `docs/mobile/MOBILE_API_BACKEND_STATUS.md`와 `docs/project/ROADMAP.md`
- breaking change이면 마이그레이션 계획 또는 기존/신규 API 병행 기간

## 4. OpenAPI 변경 검토

현재 결정:

- CI에는 별도 OpenAPI JSON snapshot diff를 추가하지 않는다.
- 대신 `OpenApiDocumentationIntegrationTest`를 전체 테스트에 포함해 `/v3/api-docs`의 v1 tag, security, error schema, request schema 제약, 문서 UI 접근성을 검증한다.
- 릴리스 전에는 `/v3/api-docs`를 기준으로 `docs/api/API_V1_FRONTEND.md`와 모바일 타입의 차이를 검토한다.

별도 OpenAPI JSON diff를 도입할 조건:

- 외부 배포 클라이언트가 v1 OpenAPI JSON을 자동 생성 원본으로 사용하기 시작할 때
- 모바일 릴리스 주기와 백엔드 배포 주기가 분리되어 계약 변경 감지가 더 중요해질 때
- OpenAPI JSON의 불필요한 순서/생성 차이를 안정적으로 정규화할 스크립트를 갖췄을 때

도입 시 권장 방식:

- `/v3/api-docs`에서 생성한 JSON을 정규화한다.
- path, method, schema, enum, required field, security scheme 변경만 diff 대상으로 삼는다.
- 설명문, operationId, schema 순서처럼 런타임 생성 차이가 잦은 항목은 별도 검토 대상으로 분리한다.

## 5. Deprecation 기준

기존 v1 계약을 제거하거나 의미를 바꿔야 한다면 아래 순서를 따른다.

1. 새 API 또는 새 field를 먼저 추가한다.
2. 기존 API와 새 API를 함께 유지하는 기간을 정한다.
3. 모바일 앱에서 새 계약으로 전환한다.
4. `docs/api/API_V1_FRONTEND.md`에 deprecation 상태를 표시한다.
5. 실제 제거는 모바일 지원 버전 정책과 운영 영향 범위를 확인한 뒤 진행한다.

## 6. Legacy API 정책

현재 결정:

- `/api/v1/**`는 모바일/외부 클라이언트용 안정 계약이다.
- `/api/tasks/**`, `/api/ddays/**`는 legacy 계약이다.
- legacy `/api/tasks/**`는 과거 호환 범위로만 유지한다.
- legacy `/api/ddays/**`는 현재 제공 중인 범위만 유지한다. 없는 endpoint를 모바일 호환용 alias로 새로 추가하지 않는다.
- legacy API에는 v1 owner scope, JWT 인증, OpenAPI 품질 보강을 새 기능 수준으로 확대하지 않는다.
- 새 모바일 API는 반드시 `/api/v1/**`에 추가한다.

제거 검토 조건:

- 모바일과 외부 클라이언트가 legacy path를 호출하지 않음
- 운영 로그에서 legacy path 사용이 충분한 기간 동안 관측되지 않음
- 대체 v1 endpoint와 마이그레이션 문서가 준비됨
