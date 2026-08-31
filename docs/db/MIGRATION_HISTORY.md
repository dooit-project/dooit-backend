# DB Migration History

Last updated: 2026-08-31

이 문서는 Flyway 도입 전 production DB에 수동 적용한 migration 이력을 기록한다. secret, dump 내용, access token은 기록하지 않는다.

## 정책

- 현재는 Flyway를 도입하지 않고 `docs/db/migrations/*.sql`과 이 이력 문서로 수동 migration을 관리한다.
- schema 변경 release 전에는 production DB 백업과 복구 검증을 먼저 수행한다.
- migration 적용 전에는 대상 column, index, constraint 존재 여부를 확인해 중복 적용을 피한다.
- migration 적용 후에는 schema 검증 쿼리와 app readiness를 확인한다.
- schema 변경 빈도가 늘어나거나 다중 환경 migration 순서 관리가 필요해지면 Flyway 도입을 별도 작업으로 전환한다.

## 적용 이력

| Applied At | File | Environment | Result |
| --- | --- | --- | --- |
| 미적용 | `docs/db/migrations/20260831_add_daily_plan.sql` | local production Docker MySQL | 일일 계획 release 전 적용 필요 |
| 미적용 | `docs/db/migrations/20260830_add_task_estimated_duration.sql` | local production Docker MySQL | Task 예상 소요 시간 release 전 적용 필요 |
| 미적용 | `docs/db/migrations/20260827_add_calendar_feed_token.sql` | local production Docker MySQL | 2026-08-29 문서 점검 기준 미적용. 개인/workspace iCalendar feed token release 전 적용 필요 |
| 미적용 | `docs/db/migrations/20260825_add_task_notification_preferences.sql` | local production Docker MySQL | 2026-08-29 문서 점검 기준 미적용. Task 알림 preference release 전 적용 필요 |
| 미적용 | `docs/db/migrations/20260823_add_refresh_token_session.sql` | local production Docker MySQL | 2026-08-29 문서 점검 기준 미적용. 등록 계정 refresh/logout release 전 적용 필요 |
| 미적용 | `docs/db/migrations/20260823_add_idempotency_record.sql` | local production Docker MySQL | 2026-08-29 문서 점검 기준 미적용. Idempotency-Key release 전 적용 필요 |
| 미적용 | `docs/db/migrations/20260823_add_password_reset_token.sql` | local production Docker MySQL | 2026-08-29 문서 점검 기준 미적용. 비밀번호 재설정 release 전 적용 필요 |
| 2026-08-17 01:40 KST | `docs/db/migrations/20260814_add_workspace_audit_columns.sql` | local production Docker MySQL | 성공. `TASK`, `DDAY_GOAL`, `RECURRENCE_SERIES` audit 컬럼 생성 확인 |
| 2026-08-17 01:40 KST | `docs/db/migrations/20260813_add_workspace_scope_columns.sql` | local production Docker MySQL | 성공. `TASK`, `DDAY_GOAL`, `RECURRENCE_SERIES` scope/workspace 컬럼 생성 확인 |
| 2026-08-17 01:40 KST | `docs/db/migrations/20260813_add_shared_workspace.sql` | local production Docker MySQL | 성공. `SHARED_WORKSPACE`, `WORKSPACE_MEMBER` 테이블 생성 확인 |
| 2026-08-17 01:40 KST | `docs/db/migrations/20260813_add_task_template.sql` | local production Docker MySQL | 성공. `TASK_TEMPLATE` 테이블 생성 확인 |
| 2026-08-10 20:18 KST | `docs/db/migrations/20260809_add_guest_account_columns.sql` | local production Docker MySQL | 성공. `APP_USER` 게스트 컬럼 추가, email/password/displayName nullable 전환, `IDX_APP_USER_ACCOUNT_TYPE_EXPIRES` 생성 확인 |
