# ToDoLab Environment Integration

Last updated: 2026-07-25

이 문서는 모바일 real mode가 백엔드에 붙을 때 사용하는 환경별 URL, CORS origin, 문서 UI 공개 기준, API 로그 운영 기준을 정리한다.

## 1. 환경별 API URL

| 환경 | API URL | 상태 | 비고 |
| --- | --- | --- | --- |
| local | `http://localhost:8080` | 확정 | 백엔드 로컬 실행 기준 |
| local, Android Emulator | `http://10.0.2.2:8080` | 확정 | Android Emulator에서 호스트 머신 접근 시 사용 |
| local, 실제 기기 | `http://<dev-machine-lan-ip>:8080` | 확인 필요 | 같은 네트워크에서 개발 머신 IP로 접근 |
| staging | 배포 URL 미정 | 확인 필요 | 배포 URL 확정 후 갱신 |
| production | 운영 도메인 미정 | 확인 필요 | 운영 도메인 확정 후 갱신 |

모바일 기본 base path는 모든 환경에서 `/api/v1`이다.

## 2. CORS Origin

현재 코드 기준 CORS 설정은 `app.cors.allowed-origins` 값을 사용한다. 운영 프로필에서는 `TODOLAB_ALLOWED_ORIGINS` 환경변수로 주입한다.

| 환경 | Origin | 상태 | 설정 위치 |
| --- | --- | --- | --- |
| local, Expo Web | `http://localhost:8081` | 확인됨 | `application.yml`, `application-local.yml` |
| local, Expo Web 대체 포트 | `http://localhost:8090` | 확인됨 | `application.yml`, `application-local.yml` |
| local, iOS Simulator | CORS origin 없음 | 확인됨 | native 요청은 브라우저 CORS 대상이 아님 |
| local, Android Emulator | CORS origin 없음 | 확인됨 | native 요청은 브라우저 CORS 대상이 아님 |
| local, 실제 기기 | CORS origin 없음 | 확인 필요 | Expo Go/native 요청은 브라우저 CORS 대상이 아님 |
| staging | 미정 | 확인 필요 | `TODOLAB_ALLOWED_ORIGINS` |
| production | 미정 | 확인 필요 | `TODOLAB_ALLOWED_ORIGINS` |

허용 request header:

- `Content-Type`
- `Accept`
- `Authorization`

허용 method:

- `GET`
- `POST`
- `PUT`
- `PATCH`
- `DELETE`
- `OPTIONS`

## 3. 환경변수 운영 방식

운영 프로필은 아래 환경변수로 CORS origin을 주입한다.

```bash
TODOLAB_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
TODOLAB_DOCS_PUBLIC_ENABLED=false
```

원칙:

- staging과 production origin은 코드에 하드코딩하지 않는다.
- 여러 origin은 쉼표로 구분한다.
- secret 값은 문서에 기록하지 않는다.
- origin을 추가한 뒤 `Authorization` header가 포함된 preflight를 확인한다.
- iOS Simulator, Android Emulator, 실제 기기 native 앱 요청은 CORS 대상이 아니므로 API URL 접근성만 확인한다.
- Expo Web은 브라우저 CORS 대상이므로 실제 접속 origin을 `TODOLAB_ALLOWED_ORIGINS`에 추가한다.
- production에서는 `TODOLAB_DOCS_PUBLIC_ENABLED=false`를 기본값으로 유지한다.

## 4. 문서 UI 공개 기준

| 환경 | Swagger UI `/swagger-ui` | Scalar `/scalar.html` | 비고 |
| --- | --- | --- | --- |
| local | 공개 | 공개 | 개발 확인용 |
| staging | 제한 공개 | 제한 공개 | `TODOLAB_DOCS_PUBLIC_ENABLED=true`일 때만 공개하고 접근 제어 또는 네트워크 제한 적용 |
| production | 비공개 기본 | 비공개 기본 | `TODOLAB_DOCS_PUBLIC_ENABLED=false` 기본값 유지 |

현재 백엔드는 `app.docs.public-enabled`로 문서 UI와 `/v3/api-docs` 공개 여부를 제어한다. local/test 기본값은 `true`, prod 기본값은 `false`다.

## 5. API 로그 운영 기준

백엔드는 `/api/**` 요청에 공통 API 로그 필터를 적용한다. 서버 렌더링 화면과 정적 리소스는 이 필터 대상이 아니다.

기본 기록 항목:

- request id: `X-Request-Id` 요청 헤더가 있으면 재사용하고, 없으면 서버가 생성해 응답 헤더로 내려준다.
- request: method, path, query, remote IP, user agent, headers
- response: status, elapsed time, response headers
- payload: `app.api-logging.payload-enabled=true`일 때 요청/응답 body를 기록한다.

운영 환경변수:

```bash
TODOLAB_API_LOGGING_ENABLED=true
TODOLAB_API_LOGGING_PAYLOAD_ENABLED=false
TODOLAB_API_LOGGING_MAX_PAYLOAD_LENGTH=4096
```

운영 원칙:

- production의 전문 로깅은 장애 재현이나 제한된 점검 시간에만 켠다.
- `Authorization`, `Cookie`, `Set-Cookie`, `password`, `token`, `secret`, `jwt` 계열 값은 로그에 `[MASKED]`로 남긴다.
- payload는 `TODOLAB_API_LOGGING_MAX_PAYLOAD_LENGTH` 길이를 넘으면 잘라서 남긴다.
- JSON, text, form payload만 기록하고 multipart 또는 binary payload는 전문을 남기지 않는다.
- 로그 공유 시 request id, 발생 시각, method/path, status, elapsed time을 우선 전달한다.
