# 빠른 기록 선택 유형 보존 — 백엔드 회신

2026-10-18 · 목표 중심 변경과 별도 요청 · STG 배포/실 API 검증 완료, PRD 미배포

모바일 요청서의 선택 유형 보존을 백엔드에 구현했다. 아래는 서버 적용 정책이며 제품/모바일의 공동 합의나 모바일 연동 완료를 뜻하지 않는다. 운영 API·운영 데이터 검증은 수행하지 않았다. STG의 localhost:18080에는 새 필드를 반영하고 합성 데이터로 실 API 검증을 완료했다. PRD 배포 일정은 미확정이며 운영 반영 전에는 PRD 클라이언트가 새 필드 지원을 전제로 요청하지 않는다.

## 요청과 호환성

기존 `POST /api/v1/tasks/quick-capture`에 선택 필드 `requestedType`을 추가한다. 값은 대문자 `TODO | SCHEDULE | IDEA`이고 생략 또는 JSON `null`이면 기존 자동 해석을 그대로 사용한다. URL과 응답 필드는 유지한다. 기존 클라이언트와 DB migration은 필요하지 않다. 모바일에서 명시적 유형을 보내려면 요청 타입과 mock을 함께 갱신해야 한다.

```json
{
  "text": "오늘 영단어 30개 외우기",
  "referenceDate": "2026-10-18",
  "timeZone": "Asia/Seoul",
  "requestedType": "TODO"
}
```

`referenceDate` 생략 시 선택/사용자 timezone의 오늘을 사용한다. 날짜 없는 TODO에 기준 날짜를 자동으로 계획일로 넣지는 않는다. `defaultCategory`는 기존과 같이 선택 필드다.

## 적용 정책

| 선택 | 입력 | 실제 저장 |
| --- | --- | --- |
| TODO | 오늘 로그인 버튼 연결하기 | TODO, plannedDate/targetDate=2026-10-18, status=TODAY |
| TODO | 내일 영단어 30개 | TODO, plannedDate/targetDate=2026-10-19, status=TODAY. 10월 18일 Today에는 미포함 |
| TODO | 로그인 버튼 연결하기 | TODO, INBOX, 계획일 없음. referenceDate가 있어도 동일 |
| TODO | 내일 오후 3시 공부 | 400/10001, 생성 없음. 시각 있는 TODO 빠른 기록은 이번 범위에서 미지원 |
| TODO | 매주 월요일 운동 | 400/10001, 생성 없음. 지원하는 주간 반복을 TODO로 등록하는 조합은 미지원 |
| SCHEDULE | 내일 오후 3시 회의 | SCHEDULE, 10월 19일 15:00 시작·16:00 종료, allDay=false |
| SCHEDULE | 내일 회의 | SCHEDULE, 10월 19일 00:00 시작·20일 00:00 종료(exclusive), allDay=true |
| SCHEDULE | 오후 3시 회의 | 기준 날짜 15:00~16:00, allDay=false |
| SCHEDULE | 회의 준비 | 400/10001, 생성 없음. 날짜·시각·지원하는 반복 중 하나 필요 |
| IDEA | 오늘 오후 3시 메모 / 매주 월요일 운동 | IDEA, INBOX. 날짜·시각·반복은 해석하지 않고 문구 그대로 보존 |
| 생략/null | 오늘 공부 | 기존 자동 해석: 종일 SCHEDULE |
| 생략/null | 공부하기 | 기존 자동 해석: INBOX TODO |

표는 referenceDate=2026-10-18, timezone=Asia/Seoul 기준이다.

날짜를 지정한 TODO는 일정 시작/종료 시각을 만들지 않는다. `startAt/endAt=null`, `allDay=false`다. `unscheduled=true`는 시각 구간이 없다는 기존 필드 의미이며 계획일 유무의 기준으로 쓰지 않는다. 계획일은 `plannedDate/targetDate`로 판단한다. 기존 미정 조회도 시각 구간이 없는 항목을 반환하므로 계획된 TODO가 포함될 수 있다. 기존 Calendar 범위 조회는 시각 구간을 기준으로 하므로 이 TODO는 해당 조회에 포함되지 않으며 계획일 목록은 Today 날짜 조회를 사용한다. `TODAY` 상태는 오늘뿐 아니라 미래 계획도 포함하며, `GET /api/v1/tasks/today?date=...`가 요청 날짜로 구분한다. 기존 `/tasks/{id}/today` 이동 API의 자동 종일 구간 부여 정책은 이번에 변경하지 않는다.

TODO는 기존 파서가 지원하는 날짜 표현을 계획일로 사용하고 제목에서 해석한 날짜 토큰을 제거한다. 날짜 없는 TODO의 기본 계획일을 오늘로 바꾸는 정책과 TODO 시각/반복 지원은 후속 제품 합의 대상으로 남긴다.

SCHEDULE의 종료 시각은 기존 기본값인 시작+1시간이며 종료 시각/기간 문장 해석은 추가하지 않는다. 날짜만 있으면 종일 하루, 시간만 있으면 기준 날짜를 사용한다. `매주 요일`은 기준 날짜 이후 가장 가까운 해당 요일부터 시작한다. 기존 지원 표현 목록은 [프론트 API 문서](API_V1_FRONTEND.md#빠른-등록)를 따른다. 인식하지 못한 반복 문구까지 반복 설정으로 안내하지 않는다.

IDEA는 날짜가 유효한지도 해석하지 않는다. `2026-02-30` 같은 표현도 텍스트다. trim/연속 공백 정규화 후 문구를 제목에 보존하고, 제목이 30자를 넘으면 제목은 30자로 줄이고 전체 문구를 description에 보존한다. timezone 자체의 유효성 검사는 기존대로 적용한다.

## 응답과 실제 반복 저장

`data.task`가 실제 저장된 원본이고 표시의 기준이다. `parsed*`는 제안이 아니라 이번 생성에 **실제 적용한 값**이다.

- `parsed`: 날짜·시각·지원하는 반복을 해석하여 적용했는지 여부. 선택 유형만 적용한 경우에는 false다.
- `parsedType`: 실제 저장 유형. parsed=false여도 TODO 또는 IDEA를 반환한다.
- `parsedDate`: TODO의 적용 계획일 또는 SCHEDULE의 적용 시작 날짜. 적용하지 않으면 null.
- `parsedTime`: 실제 적용 시각. 날짜만 지정한 TODO와 종일 일정은 null.
- IDEA는 parsed=false, parsedDate/Time/RecurrenceFrequency=null, parsedByDays=[]다.
- `originalText`: trim/연속 공백 정규화한 입력 문구다.
- `매주 요일`을 SCHEDULE 또는 자동 해석으로 등록하면 RecurrenceSeries와 첫 Task를 같은 트랜잭션에서 저장한다. `task.recurrenceSeriesId`, `task.recurrence`, `occurrenceDate`가 실제 저장 근거다. 이후 범위 조회 시 다음 occurrence를 생성하는 기존 정책을 유지한다. 단순 메타데이터만 반환하는 기능이 아니다.

TODO 날짜 입력 응답 예시(공통 envelope와 task의 다른 필드 생략):

```json
{
  "task": {
    "type": "TODO",
    "title": "영단어 30개 외우기",
    "status": "TODAY",
    "plannedDate": "2026-10-18",
    "targetDate": "2026-10-18",
    "startAt": null,
    "endAt": null,
    "allDay": false,
    "unscheduled": true,
    "recurrenceSeriesId": null,
    "recurrence": null
  },
  "parsed": true,
  "originalText": "오늘 영단어 30개 외우기",
  "parsedDate": "2026-10-18",
  "parsedTime": null,
  "parsedType": "TODO",
  "parsedRecurrenceFrequency": null,
  "parsedByDays": [],
  "timeZone": "Asia/Seoul"
}
```

## 오류와 재시도

미지원 유형, TODO+시각/지원 주간 반복, 날짜·시각·지원 반복 없는 SCHEDULE, 유효하지 않은 파싱 날짜·시각·timezone은 HTTP 400과 공통 error.code=10001이다. 검증 실패 시 Task와 반복 시리즈를 만들지 않고 실패 응답도 멱등성 기록으로 저장하지 않는다. 오류 코드는 추가하지 않는다.

기존 `Idempotency-Key` 계약을 그대로 적용한다. 모바일 timeout 재시도는 최초 키와 **동일한 요청 본문 바이트**를 재사용한다. 같은 사용자·경로·메서드·키에 저장된 성공 기록이 있으면 최초 status/body를 재전송하며 `Idempotency-Replayed: true`를 반환한다. 성공 후 유형 등 본문을 바꾸면 409/10004이고 새 항목은 생성하지 않는다. 400 이후 수정한 본문은 성공 기록이 없으므로 같은 키로 다시 요청할 수 있다.

키는 공백이 아닌 160자 이하를 사용한다. 키 없음/빈 값/160자 초과는 기존 필터가 멱등성 처리를 생략하므로 중복 방지를 제공하지 않는다. 보관 기준은 기존 24시간이며 만료 기록 정리 후에는 재생성을 막지 않는다. Task 저장과 멱등성 응답 기록 저장은 별도 트랜잭션이고 잠금도 프로세스 내부이므로, 저장 직후 서버 장애나 다중 인스턴스 동시 처리까지 중복 없는 생성을 보장하지 않는다. 이번 유형 필드 추가로 이 기존 제약을 해소했다고 안내하지 않는다. timeout을 새 키/일반 생성 요청으로 우회하는 모바일 흐름은 만들지 않는다. 장애 구간까지 보장하는 원자적 멱등성 개선은 별도 백엔드 후속 작업이다.

## 검증

`./gradlew test` 전체 647개 통과(실패/오류/skip 0). 선택 유형·날짜별 Today 분리·IDEA 원문 보존·미지원 입력 생성 없음·주간 시리즈 실제 저장·필드 생략/null 호환·OpenAPI·동일 요청 재생/유형 변경 충돌을 로컬 통합 테스트로 검증했다. 운영 API 호출이나 모바일/mock 검사는 수행하지 않았다.

STG 검증은 `scripts/smoke-staging.py`로 수행했고 합성 Task는 삭제했다. 게스트와 멱등성 기록은 STG에만 남는다. PRD 빠른 기록 API나 실제 사용자 데이터 검증은 수행하지 않았다.

## 남은 일

- 서버 정책에 대한 모바일/제품 확인, 특히 TODO 시각·반복 오류 처리와 날짜 없는 TODO의 기록함 정책.
- 변경 커밋 및 STG 반영 완료. STG 정책 확인 후 PRD 배포 범위·일정 확정. 유형 필드 자체의 DB 변경 없음.
- 모바일 c6cb6b4의 요청 타입·mock·Today TODO 전달과 실제 API client → STG 검증은 완료했다. 화면·공용 스크린샷 검증은 프론트 담당이다. 백엔드는 정책 확인 후 승인된 PRD API 배포·검증을 진행한다.
- 기존 생성 멱등성의 트랜잭션/다중 인스턴스 보장 강화 검토.
