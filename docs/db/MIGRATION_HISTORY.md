# DB Migration History

Last updated: 2026-10-17

이 문서는 Flyway 도입 전 production DB에 수동 적용한 migration 이력을 기록한다. secret, dump 내용, access token은 기록하지 않는다.

## 정책

- 현재는 Flyway를 도입하지 않고 `docs/db/migrations/*.sql`과 이 이력 문서로 수동 migration을 관리한다.
- schema 변경 release 전에는 production DB 백업과 복구 검증을 먼저 수행한다.
- migration 적용 전에는 대상 column, index, constraint 존재 여부를 확인해 중복 적용을 피한다.
- migration 적용 후에는 schema 검증 쿼리와 app readiness를 확인한다.
- schema 변경 빈도가 늘어나거나 다중 환경 migration 순서 관리가 필요해지면 Flyway 도입을 별도 작업으로 전환한다.

## 2026-10-17 개인 시스템·목표 migration 적용 완료

명시적인 운영 유지보수 승인을 받은 뒤 자동 복구 launchd를 잠시 내리고 기존 app을 중지했다. 중지 상태에서 최종 백업을 생성하고 gzip 무결성/덤프 완료 표식을 확인한 뒤 10-12 → 10-14 → 10-15 → 10-16 DDL을 순서대로 적용했다. 각 단계 및 최종 사전 점검은 READY였다(선행 4/4, 16/16, 37/37, 2/2, 9/9). DDL 전후 기존 테이블의 행 수가 일치했다.

기존 app 컨테이너를 재시작하고 launchd를 다시 로드했다. image digest는 전후 동일하며 image tag는 `3bda00c`다. 로컬 및 공개 HTTPS curl 검증에서 readiness 200/UP, metadata 200, 미인증 auth/me 401과 공개 Web origin CORS 200을 확인했다. 새 backend image 배포와 사용자 개인 시스템 이관 API 호출은 수행하지 않았다. 새 API의 인증 후 기능 smoke는 새 backend 배포 후 별도로 진행한다.

최종 백업 `dooit-before-goal-stopped-20261017-232416-d6fa3577.sql.gz`와 검증 JSON `goal-migration-verification-20261017-232427.json`은 git에서 제외된 backups 디렉터리에 0600 권한으로 보존한다. 검증 기록에는 SHA-256과 결과만 남기고 token/credential/dump 내용은 남기지 않는다. 이전 차단은 운영 명령 실행 전의 승인 검토였으며, 이번 명시적 승인 후 실제 적용이 완료됐다.

### 선행 백업·복구 리허설

`dooit-before-goal-20261017-230727-403e3471.sql.gz`의 무결성을 확인하고, network none 및 production volume과 별개인 일회용 MySQL 8.4.11에 복구했다. source schema metadata와 일치한 뒤 네 DDL을 순서대로 적용하고 전체 사전 점검을 통과했다. 임시 컨테이너와 volume은 제거했다.

리허설에서 점검 도구가 InnoDB의 NO ACTION/RESTRICT를 다르다고 오판한 문제를 발견해 engine이 InnoDB인 경우에만 동등 처리하도록 보정했다. [MySQL 8.4 FOREIGN KEY 문서](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)의 동작을 따른다. CASCADE/SET NULL이나 잘못된 FK 대상은 계속 거절한다. Python unittest 8개와 전체 backend 테스트 618개가 통과했고 실행 JAR 빌드도 성공했다.

### 검증 범위와 호환성

- 읽기 전용 `scripts/check-goal-schema.py`는 현재 DB의 테이블 존재, 핵심 추가 컬럼 type/length/nullability, opaque key collation, 명시한 index의 열 순서/unique, FK 열/대상/삭제 규칙/동일 DB 참조와 InnoDB engine을 확인한다. READY는 모든 baseline column/default/update rule 또는 실제 데이터 이관 완료를 보장하지 않는다. source 행 수 보존은 확인했지만 데이터 값 전체의 대조 검증을 의미하지 않는다.
- schema health는 21개 테이블과 핵심 추가 컬럼 5개의 존재를 확인한다. 실제 readiness 응답은 여전히 기존 운영 image의 검사 결과이며 새 main의 readiness 구현을 배포한 결과로 해석하지 않는다. Hibernate validate와 기존 배포 점검을 계속 수행한다.
- 새 테이블과 nullable TASK.LINKED_GOAL_ID를 추가해 기존 image의 API 계약을 유지한다. CREATE_REQUEST_HASH가 null인 기존 기록은 최초 생성 본문을 복구할 수 없어 동일 key 재시도를 409/10004로 거절하는 계약을 유지한다. 현재 수정값으로 임의 backfill하지 않는다.
- mapping DDL만으로 개인 데이터가 이관되지는 않는다. 클라이언트 준비 후 명시적 API를 호출해야 하며, 이관된 canonical 기록이 변경된 계정은 mapping 미지원 backend로 단순 rollback하지 않는다. 쓰기 중지와 새 기록 조정/복구 계획이 먼저 필요하다. 원본 테이블이나 mapping을 임의로 비우지 않는다.
- MySQL DDL은 파일의 START TRANSACTION/COMMIT만으로 전체 rollback을 보장하지 않는다. 이미 적용된 네 파일을 재실행하지 않으며 이후 drift가 생기면 객체별로 점검한다.

## 적용 이력

| Applied At | File | Environment | Result |
| --- | --- | --- | --- |
| 2026-10-17 23:24 KST | `docs/db/migrations/20261016_add_personal_system_goal_mapping.sql` | local production Docker MySQL | 성공. mapping 컬럼·unique·FK 점검 9/9 통과 |
| 2026-10-17 23:24 KST | `docs/db/migrations/20261015_add_goal_direct_log_request_hash.sql` | local production Docker MySQL | 성공. 지문 컬럼과 opaque key utf8mb4_bin 점검 2/2 통과 |
| 2026-10-17 23:24 KST | `docs/db/migrations/20261014_add_goal_journey.sql` | local production Docker MySQL | 성공. 목표 여정 7개 테이블·Task 연결·index/FK 점검 37/37 통과 |
| 2026-10-17 23:24 KST | `docs/db/migrations/20261012_add_personal_system.sql` | local production Docker MySQL | 성공. 개인 시스템 4개 테이블·index/FK 점검 16/16 통과 |
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
