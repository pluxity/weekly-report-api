# weekly-report-api

팀 기반 프로젝트 관리 및 주간 보고 시스템의 백엔드 API 서버.
프로젝트 · 업무 그룹 · 태스크의 CRUD, 태스크 리뷰 승인 흐름, LLM 기반 자연어 채팅 CRUD,
주간보고 작성/조회, Microsoft Teams 연동 알림까지 지원한다.

## 주요 기능

- **인증 / 인가**: 이메일·비밀번호 기반 로그인, JWT 액세스 + 리프레시 토큰, 역할(ADMIN / PO / PM / LEADER / WORKER) 기반 권한 제어
- **프로젝트 관리**: 프로젝트 CRUD, 소프트 삭제 및 복구, 진행률 집계
- **업무 그룹 (Epic) 관리**: 업무 그룹 CRUD, 멤버 배정/해제, 소프트 삭제 및 복구
- **태스크 관리**: 태스크 CRUD, `IN_PROGRESS → IN_REVIEW → DONE/REJECTED` 상태 흐름, PM 리뷰 승인/반려, 승인 이력 조회
- **팀 관리**: 팀 CRUD, 팀원 추가/제거
- **주간보고**: Chat 라우터를 통한 LLM 기반 작성·수정·삭제, 주간보고 목록·단건·요약 조회
- **자연어 채팅 (Chat)**: LLM 의도 분류 → 폼 조립용 JSON 반환, clarify 세션 처리 (Gemini / Ollama / OpenRouter 지원) → [상세](docs/llm-chat.md)
- **대시보드**: 작업자·PM·ADMIN 역할별 대시보드, 개인 KPI 상세
- **Microsoft Teams 연동**: 도메인 이벤트 기반 Adaptive Card 알림 발송, Teams Bot Webhook 수신, 알림 이력 관리 및 실패 재발사 → [상세](docs/teams-integration.md)

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Kotlin 2.3.10 / JDK 25 |
| Framework | Spring Boot 4.0.3 |
| Database | PostgreSQL (Flyway 마이그레이션) |
| Cache / Session | Redis 7 |
| ORM | Spring Data JPA + Kotlin JDSL 3.8.0 |
| Security | Spring Security + JWT (Nimbus JOSE JWT 10.3) |
| Docs | springdoc-openapi 3.0.1 (Swagger UI) |
| Logging | kotlin-logging 8.0.01 · Logbook 4.0.2 · p6spy 2.0.0 |
| Test | Kotest 5.9.1 |
| Code Style | Spotless + ktlint |
| CI/CD | GitHub Actions (Docker 이미지 빌드 → SSH 원격 배포) |

## 실행 방법

### 사전 요구사항

- JDK 25
- PostgreSQL (DB명 `weekly_report`)
- Redis (기본 `localhost:6379`)

### 로컬 실행

```bash
git clone <repo-url>
cd weekly-report-api
./gradlew bootRun
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### 빌드

```bash
./gradlew build
./gradlew build -x test   # 테스트 제외
```

### 프롬프트 회귀 테스트 (promptfoo)

LLM 프롬프트 변경 시 의도 분류·액션 생성 결과가 깨지지 않는지 검증한다. → [상세](docs/llm-chat.md#프롬프트-회귀-테스트-promptfoo)

```bash
npm run prompt:eval   # 테스트 실행
npm run prompt:view   # 결과 웹 UI로 확인
```

## 배포 환경

### 프로파일 구성

| 활성 프로파일 | 로드되는 yml |
|-------------|------------|
| `local` (기본) | `application-common.yml` + `application-local.yml` |
| `stage` | `application-stage.yml` + `application-common.yml` |
| `prod` | `application-common.yml` + `application-prod.yml` |

### stage vs prod 설정 차이

| 항목 | stage | prod |
|------|-------|------|
| `server.servlet.context-path` | `/api` | `/api` |
| DB URL | `jdbc:postgresql://${DB_HOST}:${DB_PORT}/weekly_report` | `jdbc:postgresql://${DB_HOST}:${DB_PORT}/weekly_report` |
| LLM — Gemini | 미설정 (환경 변수 없음) | `${GEMINI_API_KEY}` / `${GEMINI_MODEL}` 지원 |
| LLM — OpenRouter | `${OPENROUTER_API_KEY}` / `${OPENROUTER_MODEL}` | `${OPENROUTER_API_KEY}` / `${OPENROUTER_MODEL}` |
| LLM — Ollama | `${OLLAMA_URL}` / `${OLLAMA_MODEL}` | `${OLLAMA_URL}` / `${OLLAMA_MODEL}` |
| Teams 알림 | **비활성** (테스트 메시지 발송 방지를 위해 서버에 환경 변수 미주입) | 활성 (`${TEAMS_APP_ID}` 등 주입) |


### 배포 방식

GitHub Actions가 Docker 이미지를 빌드하고 원격 서버에 tar로 전송한 뒤, 서버의 `deploy.sh`를 실행하는 방식이다.

| 항목 | stage | prod |
|------|-------|------|
| 트리거 브랜치 | `develop` | `main` |
| 이미지 태그 | `weekly-report-api:stage` | `weekly-report-api:prod` |
| 서버 디렉토리 | `weeklyReport/stage` | `weeklyReport/prod` |
| 워크플로우 파일 | `deploy-stage.yml` | `deploy.yml` |

필요한 GitHub Secrets:

| Secret | 설명 |
|--------|------|
| `PLX_DEV_DOMAIN` | 배포 대상 서버 호스트 |
| `PLX_DEV_USER` | SSH 사용자명 |
| `PLX_DEV_PASSWORD` | SSH 비밀번호 |
| `PLX_DEV_SSH_PORT` | SSH 포트 |
| `DOCKER_PATH` | 서버의 Docker 작업 디렉토리 절대 경로 |


### 런타임 환경 변수 주입

DB 접속 정보, LLM API 키(`GEMINI_API_KEY`, `OPENROUTER_API_KEY` 등), Teams 자격 증명 같은
런타임 환경 변수는 GitHub Secrets가 아니라 **각 배포 서버의 `api.yml`(docker compose 파일) `environment` 블록**에 정의되어 있다.
새 환경 변수를 추가할 때는 yml 매핑 추가 후 서버의 `api.yml`도 함께 갱신해야 한다.

## 문서

| 문서 | 내용 |
|------|------|
| [docs/api.md](docs/api.md) | 전체 엔드포인트 표 (~60개) + 역할 체계(effectiveRole) |
| [docs/llm-chat.md](docs/llm-chat.md) | LLM Chat 파이프라인, clarify 흐름, LLM 제공자 설정, chat_logs 테이블, 프롬프트 회귀 테스트(promptfoo) |
| [docs/teams-integration.md](docs/teams-integration.md) | Teams 알림 이벤트 표, 발송 흐름, Bot Webhook 수신, Teams 앱 설정(pms-bot), teams_notification_logs 테이블 |
