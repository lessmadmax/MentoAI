# OpenAPI 1.2.0 대비 구현 현황

## 요약
- ✅ **완료**: `/auth/google/start`, `/auth/google/callback`(redirect only), `/auth/refresh`, `/auth/me`(401 on unauth), `/auth/logout`, `/activities/**`, `/tags/**`, `/users/{id}` 관련 기본 CRUD, `/recommend/activities/**`, `/ingest/**`.
- ⚠️ **부분 충족**: `/auth/me` 응답 포맷, `/auth/google/callback`의 `mode=json`/`redirectUri`, `/users/{id}/calendar` 경로, 추천/검색 API가 명세와 다름.
- ❌ **미구현**: `/auth/providers`, `/search`, `/users/{id}/profile`, `/users/{id}/role-fit*`, `/users/{id}/improvements`, `/roles` 계열, `/meta/*`, 다수 DTO/스키마.

## 상세 매핑
| 영역 | 스펙 경로 | 구현 상태 | 비고 |
| --- | --- | --- | --- |
| Auth | `/auth/google/start` | ✅ | Redirect만 지원, `redirectUri` 옵션 없음 |
| Auth | `/auth/google/callback` | ⚠️ | 항상 302 redirect; `mode=json`, Query `redirectUri` 미지원 |
| Auth | `/auth/me` | ⚠️ | 미로그인 시 401; AuthStatus 응답 구조 없음 |
| Auth | `/auth/logout`, `/auth/refresh` | ✅ | DTO 명세(`TokenPair`) 미적용 |
| Auth | `/auth/providers` | ❌ | 엔드포인트 부재 |
| Activities | `/activities`, `/activities/{id}`, `/activities/{id}/attachments` | ✅ | 정렬/응답 DTO는 내부 전용 구조 |
| Activities | `/search` | ❌ | 대신 `/recommend/semantic-search` 존재 |
| Tags | `/tags` | ✅ | POST/GET 모두 제공 |
| Users | `/users`, `/users/{id}` | ✅ | UserEntity 직접 노출 |
| Users | `/users/{id}/interests` | ✅ | DTO 미적용 |
| Users | `/users/{id}/calendar/events` | ❌ | `/users/{id}/calendar`로만 제공 |
| Users | `/users/{id}/profile` | ❌ | 엔드포인트/DTO 없음 |
| Users | `/users/{id}/role-fit*`, `/users/{id}/improvements` | ❌ | 서비스/DTO 없음 |
| Recommend | `/recommend` (POST) | ❌ | 현재는 `/recommend/activities/{userId}` 등 별도 구조 |
| Recommend | `/roles`, `/roles/{roleId}` | ❌ | TargetRole 관련 로직 없음 |
| Meta | `/meta/skills`, `/meta/certifications` | ❌ | 정적 데이터 제공 미구현 |
| Admin | `/ingest/trigger` | ✅ | `/status`, `/campus` 등 추가 route 존재 |

## DTO/스키마 간극
- **Auth**: `AuthResult`, `TokenPair`, `AuthStatus` 구조 미구현. 현재 `AuthResponse`, `AuthTokens`, `UserSummary` 사용.
- **Activities**: `PagedActivities`, `Activity`, `Attachment` 등 DTO 미스펙 (필드명/구조 차이, 날짜 처리 방식 상이).
- **Users**: `UserProfile`, `UserProfileUpsert`, `UserInterest`, `CalendarEvent` 등 명세 DTO 부재.
- **Recommend/RoleFit**: `RoleFitRequest/Response`, `RoleFitSimulation`, `ImprovementItem`, `TargetRole*`, `SkillDictionary`, `CertificationDictionary` 미구현.

## 후속 조치 제안
1. 인증 흐름부터 명세 적용 (`AuthStatus`, JSON 모드, redirect 옵션) 후 단계별 확장.
2. 검색/활동 응답 구조를 `PagedActivities` 기준으로 DTO 화.
3. 사용자 프로필/캘린더, role-fit/roles/meta 순으로 서비스/엔드포인트 추가.
4. 신규 DTO 정의 시 `controller/dto` 패키지 확장 및 Mapper 작성.


