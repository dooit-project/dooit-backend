# DB Migration History

Last updated: 2026-10-17

이 문서는 Flyway 도입 전 production DB에 수동 적용한 migration 이력을 기록한다. secret, dump 내용, access token은 기록하지 않는다.

## 정책

- 현재는 Flyway를 도입하지 않고 `docs/db/migrations/*.sql`과 이 이력 문서로 수동 migration을 관리한다.
- schema 변경 release 전에는 production DB 백업과 복구 검증을 먼저 수행한다.
- migration 적용 전에는 대상 column, index, constraint 존재 여부를 확인해 중복 적용을 피한다.
- migration 적용 후에는 schema 검증 쿼리와 app readiness를 확인한다.
- schema 변경 빈도가 늘어나거나 다중 환경 migration 순서 관리가 필요해지면 Flyway 도입을 별도 작업으로 전환한다.

## 신규 migration 적용 확인 필요

### 2026-10-17 읽기 전용 사전 점검

`python3 -B scripts/check-goal-schema.py`를 현재 Docker Compose MySQL의 애플리케이션 DB 사용자로 실행했다. 선행 조건 4/4는 통과했고 10-12(0/16), 10-14(0/37), 10-15(0/2), 10-16(0/9)은 모두 MISSING이었다. 종료 코드 1은 점검 실패를 올바르게 탐지한 결과다. 이 실행은 information_schema SELECT만 수행했으며 DDL·개인 데이터 이관·재배포를 하지 않았다. 적용 이력 표에도 migration 성공으로 추가하지 않는다.

도구는 테이블 존재, 핵심 추가 컬럼 type/length/nullability와 opaque key collation, 명시한 index의 열 순서/unique 여부, FK 연결 열/대상/삭제 규칙/동일 DB 참조를 점검한다. baseline 테이블의 모든 컬럼/type/default, FK update rule, 실제 데이터 일관성/중복/고아 행이나 migration 실행 이력은 보장하지 않는다. metadata 조회 권한으로 보이지 않는 객체도 누락으로 표시될 수 있으므로 MISSING만 보고 DDL을 중복 실행하지 않는다.

`scripts/release-production.sh`는 빌드와 app 교체 전에 같은 점검을 실행하며 실패 시 중단한다. 운영 적용 전 백업/복구 확인, 실제 객체/권한 점검, 미적용 DDL의 순서별 수동 실행과 데이터 검증은 계속 필요하다. MySQL DDL은 migration 안의 START TRANSACTION/COMMIT만으로 전체 원자성이 보장되지 않으므로 부분 적용 상태에서 파일 전체를 다시 실행하지 않는다.

- 2026-10-17 readiness 보강은 DDL을 적용하지 않는다. `20261012_add_personal_system.sql`과 `20261014_add_goal_journey.sql`은 아래 적용 이력에 기록되어 있지 않으므로 운영 DB의 실제 존재 여부를 먼저 확인하고, 미적용이면 이 순서로 적용한다. 이어 직접 기록 지문(10-15), 명시적 이관 mapping(10-16)을 점검한다. 기존의 2026-09-05 적용 완료를 이 신규 migration의 적용 증거로 사용하지 않는다.
- schema health는 현재 연결 catalog/schema 안에서 기본 10개와 개인 시스템/목표 11개 테이블의 정확한 이름, `TASK.LINKED_GOAL_ID`, `GOAL_DIRECT_LOG.CREATE_REQUEST_HASH`, `PERSONAL_SYSTEM.MIGRATED_GOAL_ID/MIGRATED_PLAN_ID`, `PERSONAL_SYSTEM_OCCURRENCE.MIGRATED_OCCURRENCE_ID` 존재 여부를 검사한다. 빠진 항목은 내부 health detail의 missingTables/missingColumns에 표시하고 readiness를 DOWN으로 한다. 다른 schema의 동명 테이블이나 `_` wildcard와 비슷한 이름으로 통과하지 않는다.
- 이 검사는 테이블/주요 추가 컬럼 존재 확인이며 모든 column type, nullability, collation, unique/FK/index와 실제 데이터 이관 완료 여부를 보장하지 않는다. 기존 migration 사전 점검과 Hibernate validate를 계속 수행한다. 운영 DB 변경, app 재배포와 사용자 데이터 이관은 이번 작업에서 실행하지 않았다.

- `docs/db/migrations/20261016_add_personal_system_goal_mapping.sql`: 구 시스템의 새 Goal/Plan ID와 구 회차의 새 회차 ID를 nullable unique FK로 보존한다. 선행 조건은 `20261012_add_personal_system.sql`, `20261014_add_goal_journey.sql`이며 직접 기록 지문 migration도 별도로 적용해야 한다. 운영 DB에는 아직 적용하지 않았다.
- mapping DDL은 계정 데이터를 이관하지 않는다. 백업·column/index/constraint 점검 후 DDL과 새 backend를 적용하고, 새 API 전환 준비가 된 클라이언트만 명시적 이관 API를 호출한다. 이관 실패는 한 트랜잭션 전체를 롤백하고 재시도는 mapping을 재사용한다.
- 이관 후 canonical 기록이 변경된 계정은 구 backend로 단순 rollback하지 않는다. mapping 미지원 버전은 동결된 원본을 다시 수정할 수 있으므로 쓰기 중지와 새 기록 조정/복구 계획을 먼저 준비한다. 구 테이블/대응 ID를 임의로 삭제하거나 mapping을 null로 되돌리지 않는다.

- `docs/db/migrations/20261015_add_goal_direct_log_request_hash.sql`: 직접 기록의 최초 생성 요청 SHA-256 지문을 `CREATE_REQUEST_HASH`에 보존하고 idempotency key의 대소문자 구분을 `utf8mb4_bin`으로 명시한다. 이 작업에서는 운영 DB에 적용하지 않았다.
- 선행 조건은 `20261014_add_goal_journey.sql`이다. 적용 전 `GOAL_DIRECT_LOG`와 `CREATE_REQUEST_HASH`, key collation 존재 여부 및 기존 적용 이력을 확인한다. 백업 후 신규 migration을 적용하고 schema/readiness를 확인한다.
- 새 지문 컬럼은 nullable이므로 기존 행을 보존한다. 지문이 없는 기존 행은 최초 본문을 확인할 수 없어 같은 key 재시도를 409/`10004`로 거절한다. 수정·취소는 계속 지원하며, 최초 요청 지문을 현재 수정된 값으로 임의 backfill하지 않는다.

## 적용 이력

| Applied At | File | Environment | Result |
| --- | --- | --- | --- |
| 2026-09-05 15:24 KST | `docs/db/migrations/20260903_add_daily_plan_initial_focus_task.sql` | local production Docker MySQL | 성공. `DAILY_PLAN_INITIAL_FOCUS_TASK` 테이블과 index 생성 확인 |
| 2026-09-05 15:24 KST | `docs/db/migrations/20260901_add_task_checklist_item.sql` | local production Docker MySQL | 성공. `TASK_CHECKLIST_ITEM` 테이블과 index 생성 확인 |
| 2026-09-05 15:24 KST | `docs/db/migrations/20260831_add_daily_plan.sql` | local production Docker MySQL | 성공. `DAILY_PLAN`, `DAILY_PLAN_FOCUS_TASK` 테이블과 index 생성 확인 |
| 2026-09-05 15:23 KST | `docs/db/migrations/20260830_add_task_estimated_duration.sql` | local production Docker MySQL | 성공. `TASK.ESTIMATED_DURATION_MINUTES` 컬럼 생성 확인 |
| 2026-09-05 15:22 KST | `docs/db/migrations/20260827_add_calendar_feed_token.sql` | local production Docker MySQL | 기존 적용 확인. `CALENDAR_FEED_TOKEN` 테이블과 index 존재 확인 |
| 2026-09-05 15:28 KST | `docs/db/migrations/20260825_add_task_notification_preferences.sql` | local production Docker MySQL | 기존 컬럼 적용 확인 후 누락 index 2개 보강. `TASK.NOTIFICATION_ENABLED`, `TASK.NOTIFY_AT`, notification index 존재 확인 |
| 2026-09-05 15:22 KST | `docs/db/migrations/20260823_add_refresh_token_session.sql` | local production Docker MySQL | 기존 적용 확인. `REFRESH_TOKEN_SESSION` 테이블과 index 존재 확인 |
| 2026-09-05 15:22 KST | `docs/db/migrations/20260823_add_idempotency_record.sql` | local production Docker MySQL | 기존 적용 확인. `IDEMPOTENCY_RECORD` 테이블과 index 존재 확인 |
| 2026-09-05 15:22 KST | `docs/db/migrations/20260823_add_password_reset_token.sql` | local production Docker MySQL | 기존 적용 확인. `PASSWORD_RESET_TOKEN` 테이블과 index 존재 확인 |
| 2026-08-17 01:40 KST | `docs/db/migrations/20260814_add_workspace_audit_columns.sql` | local production Docker MySQL | 성공. `TASK`, `DDAY_GOAL`, `RECURRENCE_SERIES` audit 컬럼 생성 확인 |
| 2026-08-17 01:40 KST | `docs/db/migrations/20260813_add_workspace_scope_columns.sql` | local production Docker MySQL | 성공. `TASK`, `DDAY_GOAL`, `RECURRENCE_SERIES` scope/workspace 컬럼 생성 확인 |
| 2026-08-17 01:40 KST | `docs/db/migrations/20260813_add_shared_workspace.sql` | local production Docker MySQL | 성공. `SHARED_WORKSPACE`, `WORKSPACE_MEMBER` 테이블 생성 확인 |
| 2026-08-17 01:40 KST | `docs/db/migrations/20260813_add_task_template.sql` | local production Docker MySQL | 성공. `TASK_TEMPLATE` 테이블 생성 확인 |
| 2026-08-10 20:18 KST | `docs/db/migrations/20260809_add_guest_account_columns.sql` | local production Docker MySQL | 성공. `APP_USER` 게스트 컬럼 추가, email/password/displayName nullable 전환, `IDX_APP_USER_ACCOUNT_TYPE_EXPIRES` 생성 확인 |
