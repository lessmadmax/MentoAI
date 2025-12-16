# MentoAI
캡스톤디자인 멘토아이(MentoAI) 백엔드 서버입니다.  
사용자 프로필/희망 직무/질의를 기반으로 활동(공모전/대회/스터디 등) 및 채용 공고를 추천합니다.

## 요구사항
- **Java 17**
- (선택) **PostgreSQL** (로컬 `local` 프로파일에서 사용)
- (선택) **Qdrant** (벡터 검색 사용 시)
- (선택) **Gemini API Key** (LLM/임베딩 사용 시)

## 문서/헬스 체크
- **Swagger UI**: `/docs`
- **OpenAPI JSON**: `/v3/api-docs`
- **Health check**: `/healthz`

---

## 빠른 실행 (H2 데모: DB 설치 없이 API 확인)
`h2` 프로파일은 인메모리 DB + 샘플 데이터(`data-h2.sql`)가 자동 로드되고, 기본 사용자로 인증을 우회해 테스트하기 쉽습니다.

### 실행
**PowerShell**

```powershell
$env:SPRING_PROFILES_ACTIVE="h2"
.\gradlew.bat bootRun
```

**Bash**

```bash
SPRING_PROFILES_ACTIVE=h2 ./gradlew bootRun
```

### 접속
- Swagger: `http://localhost:8080/docs`
- H2 Console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:mentoai`

---

## 로컬 실행 (PostgreSQL 사용: local 프로파일)
주의: 기본 프로파일이 `prod`라서 그대로 실행하면 `DATABASE_URL` 등이 없어 실패할 수 있습니다.  
로컬에서는 **반드시** `SPRING_PROFILES_ACTIVE=local`(또는 `h2`)로 실행하세요.

### 1) PostgreSQL 준비 (Docker 예시)

```bash
docker run --name mentoai-postgres -d -p 5432:5432 \
  -e POSTGRES_DB=mentoai_local \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=0000 \
  postgres:16
```

### 2) local 프로파일로 실행
**PowerShell**

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun
```

**Bash**

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

- `local` 프로파일은 `application-local.properties`의 DB 설정을 사용합니다.
- Flyway가 켜져 있어 `src/main/resources/db/migration` 마이그레이션이 적용됩니다.

---

## 벡터 검색(Qdrant) 사용/비활성화
추천/검색은 기본적으로 벡터 검색이 켜져 있습니다(`recommendation.vector-search.enabled=true`).

### Qdrant 사용 시(권장)
아래 환경변수를 설정하세요.
- `QDRANT_URL` (예: `http://localhost:6333` 또는 Qdrant Cloud URL)
- `QDRANT_API_KEY` (Cloud 사용 시)
- (선택) `QDRANT_COLLECTION` (기본값: `linkareer_contest`)
- (선택) `QDRANT_VECTOR_DIM` (기본 768)

### Qdrant 없이 실행하고 싶으면(로컬 임시)
실행 파라미터로 끌 수 있습니다.

```powershell
$env:SPRING_PROFILES_ACTIVE="h2"
.\gradlew.bat bootRun --args="--recommendation.vector-search.enabled=false"
```

---

## 프로덕션(prod) 실행 개요
`prod` 프로파일은 주로 환경변수 기반 설정을 사용합니다.

### 필수(대표)
- DB: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- OAuth: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`
- JWT: `JWT_SECRET`
- Gemini: `GEMINI_API_KEY`
- Qdrant: `QDRANT_URL` (+ 필요 시 `QDRANT_API_KEY`)

---

## (선택) S3 데이터 적재 트리거
S3 적재 기능은 설정이 없으면 비활성화되어 있습니다.

### 관련 설정(대표)
- `AWS_S3_INGEST_ENABLED`, `AWS_REGION`, `AWS_S3_BUCKET`, `IAM_ACCESS_KEY`, `IAM_SECRET_KEY`

### 트리거 API
- `POST /ai/ingest/contests`
- `POST /ai/ingest/job-postings`
- `POST /ai/ingest/all`

`application.ai.api-key`(ENV: `AI_INTERNAL_KEY`)를 설정한 경우, 호출 시 헤더가 필요합니다.
- `X-AI-API-KEY: <키>`

---

## 로컬에서 추천 API 한 번 호출해보기 (curl 예시)
아래 예시는 서버를 `h2` 프로파일로 실행 중이라고 가정합니다.

### 활동 추천: `POST /recommend`
**PowerShell (Windows는 curl 별칭 이슈가 있을 수 있어 `curl.exe` 권장)**

```powershell
curl.exe -X POST "http://localhost:8080/recommend" `
  -H "Content-Type: application/json" `
  -d "{`"userId`": 1, `"query`": `"AI 관련 공모전 추천해줘`", `"topK`": 5, `"useProfileHints`": true}"
```

**샘플 요청 JSON (자세한 버전)**

```json
{
  "userId": 1,
  "query": "AI 관련 공모전 추천해줘",
  "topK": 5,
  "useProfileHints": true,
  "timeWindow": { "from": "2025-12-12", "to": "2025-12-31" },
  "preferTags": ["AI", "공모전"]
}
```

### 채용 공고 추천: `POST /recommend/jobs`

```powershell
curl.exe -X POST "http://localhost:8080/recommend/jobs" `
  -H "Content-Type: application/json" `
  -d "{`"userId`": 1, `"limit`": 5}"
```

---

## 테스트
테스트는 기본적으로 `h2` 프로파일로 동작합니다.

```bash
./gradlew test
```

---

## Docker로 실행 (앱만)
현재 `Dockerfile`은 앱만 빌드/실행합니다. (DB/Qdrant는 별도 준비 필요)

```bash
docker build -t mentoai .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=... \
  -e DATABASE_USERNAME=... \
  -e DATABASE_PASSWORD=... \
  mentoai
```
