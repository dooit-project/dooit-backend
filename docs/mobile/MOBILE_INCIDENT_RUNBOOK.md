# Mobile Incident Runbook

Last updated: 2026-07-25

이 문서는 모바일 real mode 연동 중 장애가 발생했을 때 백엔드에서 확인할 순서를 정리한다.

## 1. 접수 정보

- 발생 시각과 timezone
- 환경: local, staging, production
- 앱 실행 형태: Expo Web, iOS Simulator, Android Emulator, 실제 기기
- API method/path/query
- 백엔드 `X-Request-Id`
- HTTP status와 `error.code`
- 사용자에게 노출된 message

## 2. 1차 분류

| 증상 | 우선 확인 |
| --- | --- |
| CORS preflight 실패 | `Origin`, `Access-Control-Request-Headers`, `TODOLAB_ALLOWED_ORIGINS` |
| 401 | access token 존재 여부, 만료, Bearer prefix, issuer |
| 403 | 인증 사용자 role과 owner scope |
| 400 | enum/date 형식, required field, request body JSON |
| 404 | v1 path 여부, resource id, owner scope |
| 409 | Today 재정렬 stale 목록, 중복/누락 id |
| 500 | 서버 stack trace, DB 상태, 외부 연동 실패 |

## 3. 백엔드 확인 순서

1. `/v3/api-docs` 기준으로 요청 path, method, schema가 맞는지 확인한다.
2. 응답의 `X-Request-Id`로 백엔드 `API_REQUEST_IN`, `API_RESPONSE_OUT` 로그를 찾는다.
3. [`../api/API_ERROR_CODES.md`](../api/API_ERROR_CODES.md)에서 `error.code` 의미와 retry 가능 여부를 확인한다.
4. 인증 문제면 [`../api/AUTH_CONTRACT.md`](../api/AUTH_CONTRACT.md)의 TTL, claim, 401/403 기준을 확인한다.
5. CORS 문제면 [`../ops/ENVIRONMENT_INTEGRATION.md`](../ops/ENVIRONMENT_INTEGRATION.md)의 origin 설정과 preflight 기준을 확인한다.
6. 반복 occurrence 문제면 [`../api/RECURRENCE_MODEL.md`](../api/RECURRENCE_MODEL.md)와 [`../api/NOTIFICATION_CONTRACT.md`](../api/NOTIFICATION_CONTRACT.md)를 확인한다.

## 4. 기록 형식

```text
Date:
Environment:
Client:
API:
RequestId:
Status/ErrorCode:
Symptom:
Root cause:
Backend change:
Mobile action:
Verification:
```
