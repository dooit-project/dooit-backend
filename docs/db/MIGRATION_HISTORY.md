# DB Migration History

Last updated: 2026-10-16

이 문서는 Flyway 도입 전 production DB에 수동 적용한 migration 이력을 기록한다. secret, dump 내용, access token은 기록하지 않는다.

## 정책

- 현재는 Flyway를 도입하지 않고 `docs/db/migrations/*.sql`과 이 이력 문서로 수동 migration을 관리한다.
- schema 변경 release 전에는 production DB 백업과 복구 검증을 먼저 수행한다.
- migration 적용 전에는 대상 column, index, constraint 존재 여부를 확인해 중복 적용을 피한다.
- migration 적용 후에는 schema 검증 쿼리와 app readiness를 확인한다.
- schema 변경 빈도가 늘어나거나 다중 환경 migration 순서 관리가 필요해지면 Flyway 도입을 별도 작업으로 전환한다.

## 신규 migration 적용 확인 필요

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
