# Sharing Contract

Last updated: 2026-08-13

이 문서는 일정 공유 기능을 구현하기 전 고정해야 하는 backend 설계 기준이다. 현재 production API에는 공유 API가 아직 없다.

## 목표

- 개인 Task/D-Day owner scope를 깨지 않고 공유 workspace를 추가한다.
- 1차 공유 범위는 workspace 안의 Task와 D-Day 조회/생성/수정/삭제다.
- 기존 `/api/v1/tasks/**`, `/api/v1/dday-goals/**` 개인 API에는 공유 데이터가 섞이지 않아야 한다.
- 반복 series, occurrence, 알림 후보, D-Day 연결은 workspace scope 안에서만 일관되게 동작해야 한다.

## 1차 범위

### 포함

- workspace 생성
- workspace 멤버 초대, 수락, 제거
- workspace Task 생성/조회/수정/삭제
- workspace D-Day 생성/조회/삭제
- workspace D-Day와 workspace Task 연결
- workspace 반복 Task 생성과 occurrence materialize

### 제외

- 게스트 계정 초대
- public invite link
- 개인 Task를 workspace로 이동하거나 workspace Task를 개인 Task로 이동
- workspace 템플릿 공유
- workspace 서버 push 실제 발송
- workspace별 세부 알림 설정

## 권한 모델

| Role | 의미 |
| --- | --- |
| `OWNER` | workspace 설정, 멤버 관리, 모든 Task/D-Day 변경 가능 |
| `EDITOR` | workspace Task/D-Day 생성, 수정, 삭제 가능 |
| `VIEWER` | workspace Task/D-Day 조회만 가능 |

멤버십 상태:

| Status | 의미 |
| --- | --- |
| `PENDING` | 초대됐지만 아직 수락하지 않음 |
| `ACTIVE` | workspace 접근 가능 |
| `REMOVED` | 제거됨. 과거 감사 목적 row 보존 가능 |

규칙:

- `OWNER`는 최소 1명이어야 한다.
- `PENDING`, `REMOVED` 멤버는 workspace 데이터를 조회할 수 없다.
- 게스트 계정은 초대 대상과 workspace 생성 대상에서 제외한다.
- 같은 user는 같은 workspace에 활성 멤버십을 하나만 가진다.

## 데이터 모델 방향

예상 migration:

- `SHARED_WORKSPACE`
- `WORKSPACE_MEMBER`
- `TASK.SCOPE`
- `TASK.WORKSPACE_ID`
- `DDAY_GOAL.SCOPE`
- `DDAY_GOAL.WORKSPACE_ID`
- `RECURRENCE_SERIES.SCOPE`
- `RECURRENCE_SERIES.WORKSPACE_ID`

scope 값:

| Scope | 의미 |
| --- | --- |
| `PERSONAL` | 기존 개인 데이터. 기본값 |
| `WORKSPACE` | 공유 workspace 데이터 |

기존 `OWNER_USER_ID`는 유지한다.

- `PERSONAL` row에서는 현재처럼 owner scope 기준이다.
- `WORKSPACE` row에서는 생성자 또는 최초 작성자 기록으로만 사용하고, 접근 권한은 `WORKSPACE_MEMBER`로 판단한다.
- workspace Task/D-Day 조회는 `WORKSPACE_ID + ACTIVE membership` 조건을 사용한다.
- 개인 API repository query는 반드시 `SCOPE=PERSONAL` 조건을 포함한다.

## API 방향

예상 endpoint:

```http
POST /api/v1/workspaces
GET /api/v1/workspaces
GET /api/v1/workspaces/{workspaceId}
PUT /api/v1/workspaces/{workspaceId}
DELETE /api/v1/workspaces/{workspaceId}

POST /api/v1/workspaces/{workspaceId}/members
GET /api/v1/workspaces/{workspaceId}/members
PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}
DELETE /api/v1/workspaces/{workspaceId}/members/{memberId}

POST /api/v1/workspaces/{workspaceId}/tasks
GET /api/v1/workspaces/{workspaceId}/tasks
GET /api/v1/workspaces/{workspaceId}/tasks/{taskId}
PUT /api/v1/workspaces/{workspaceId}/tasks/{taskId}
DELETE /api/v1/workspaces/{workspaceId}/tasks/{taskId}

POST /api/v1/workspaces/{workspaceId}/dday-goals
GET /api/v1/workspaces/{workspaceId}/dday-goals
GET /api/v1/workspaces/{workspaceId}/dday-goals/{goalId}
DELETE /api/v1/workspaces/{workspaceId}/dday-goals/{goalId}
```

개인 API와 workspace API는 path를 분리한다. 모바일은 개인 화면과 공유 workspace 화면을 명시적으로 구분해야 한다.

## 반복과 D-Day 정책

- workspace 반복 series는 `RECURRENCE_SERIES.SCOPE=WORKSPACE`, `WORKSPACE_ID`를 가진다.
- workspace occurrence materialize는 workspace membership을 먼저 검증한 뒤 workspace scope에서 실행한다.
- workspace Task는 personal D-Day에 연결할 수 없다.
- personal Task는 workspace D-Day에 연결할 수 없다.
- workspace D-Day 삭제 시 같은 workspace의 연결 Task만 연결 해제한다.

## 알림 정책

- 1차 구현에서 workspace 알림 후보는 개인 알림 후보 API에 포함하지 않는다.
- workspace 알림 후보 API를 열 경우 `/api/v1/workspaces/{workspaceId}/tasks/notification-candidates`처럼 별도 path를 사용한다.
- 서버 push 실제 발송 전까지 workspace 알림도 모바일 로컬 알림 책임으로 둔다.
- workspace 알림 이력은 전송자를 기록할 수 있도록 별도 정책 확정 후 구현한다.

## Guest 정책

- 게스트는 workspace 생성, 초대, 수락 대상에서 제외한다.
- 게스트가 정식 계정으로 승격된 뒤에는 일반 사용자처럼 workspace 기능을 사용할 수 있다.
- 게스트 병합 시 workspace membership은 병합 대상에 포함하지 않는다. 게스트는 workspace membership을 만들 수 없기 때문이다.

## 테스트 전략

구현 전 먼저 아래 invariant를 테스트로 고정한다.

- 개인 Task API는 workspace Task를 반환하지 않는다.
- 개인 D-Day API는 workspace D-Day를 반환하지 않는다.
- workspace API는 `ACTIVE` member만 접근 가능하다.
- `VIEWER`는 조회만 가능하고 생성/수정/삭제는 403이다.
- 다른 workspace의 Task/D-Day ID는 존재하지 않는 리소스처럼 처리한다.
- workspace Task와 personal D-Day 연결은 400 또는 404로 거부한다.
- workspace 반복 occurrence materialize는 같은 workspace 안에서만 생성된다.

## Migration과 Rollback

적용 순서:

1. `SHARED_WORKSPACE`, `WORKSPACE_MEMBER`를 추가한다.
2. `TASK`, `DDAY_GOAL`, `RECURRENCE_SERIES`에 scope/workspace 컬럼을 nullable/default 안전 방식으로 추가한다.
3. 기존 row를 `SCOPE=PERSONAL`로 backfill한다.
4. personal repository query에 `SCOPE=PERSONAL` 조건을 적용한다.
5. workspace API를 추가한다.

Rollback:

- workspace API 배포 직후 문제가 있으면 workspace endpoint만 닫고 personal API는 유지한다.
- workspace 데이터가 생성된 뒤에는 단순 column drop rollback을 하지 않는다.
- production rollback 전에는 workspace row 수, workspace Task/D-Day row 수, membership row 수를 기록하고 백업한다.
