# Structured Outputs 도입 스펙 — weekly-report classify

> 배경 조사: [structured-outputs-weekly-report.md](./structured-outputs-weekly-report.md)
> 목적: **현 기능이 에러 없이 도는 것**. 멀티팀 기획은 나중에.

## 0. 전제 (현 기획 수준)

- 팀장이 본인 팀 주간보고를 붙여넣음 → LLM 정규화 → 저장 → 전주 비교. 그 이상 없음.
- 저장 팀은 **작성자의 leader 팀 `first()`** (`WeeklyReportChatHandler.kt:120-124`). LLM의 `team`/`team_name_raw`는 표시용.
- 복수 팀 리더는 존재하지 않음 → 검증·되물어보기 로직 **안 만든다**.
- 즉 멀티팀 입력이 와도 **본인 팀 하나로 저장이 정답**. 스키마도 단일 팀 유지.

## 1. 결정 사항

| 항목 | 결정 |
|---|---|
| 프로바이더 | **OpenRouter 단일**. Gemini·Ollama 코드/설정 제거, 폴백 없음 |
| 적용 범위 | **classify 호출만**. intent/match/generate/answer는 현행 유지 |
| 스키마 정의 | **Kotlin 코드 상수** (resources JSON 파일 아님) |
| 방어층 | **전부 유지**. `stripCodeFence`·`decodeJson` blank·try/catch·3회 재시도 손대지 않음 |
| 추론 | classify 호출은 `reasoning.enabled=false` (구 Gemini `thinkingBudget=0` 복원) |
| 진단 | `finish_reason`·`reasoning_tokens`·응답 `id` 를 **파싱 전에** 로깅 |
| 저장 로직 | 무변경 |
| 프롬프트 파일 | 무변경 (스키마가 정본, 프롬프트의 출력 형식 섹션은 중복이지만 남겨둠) |

## 2. 변경 대상

### 2-1. `LlmApiDto.kt`

`OpenAiChatRequest`에 필드 추가 + `@JsonInclude(NON_NULL)` (다른 호출은 필드가 직렬화에서 빠져 현행 동일):

```kotlin
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double,
    @param:JsonProperty("response_format") val responseFormat: ResponseFormat? = null,
    val provider: ProviderPreferences? = null,   // require_parameters=true
)
```

추가 DTO: `ResponseFormat(type, jsonSchema)`, `JsonSchemaSpec(name, strict, schema)`, `ProviderPreferences(requireParameters)`, `ReasoningConfig(enabled=false)`.
응답 쪽에는 진단 필드 — `OpenAiChatResponse.id`, `OpenAiChoice.finish_reason`/`native_finish_reason`, `OpenAiUsage.completion_tokens_details.reasoning_tokens`.
`schema` 타입은 `Map<String, Any>` (Kotlin 상수를 Jackson이 그대로 직렬화).

**삭제**: `OllamaChatRequest/Options/Response/Message`, `GeminiRequest/Content/Part/GenerationConfig/ThinkingConfig/Response/Candidate`.

### 2-2. 스키마 상수 (신규 `chat/llm/schema/ClassifySchema.kt`)

`WeeklyReportClassifyResult` + `FormattedReport`/`ReportItem`과 1:1. strict 규칙상 **모든 프로퍼티 required + `additionalProperties:false`**, `$ref`는 **인라인**(Gemini 스키마 서브셋 회피).

```
weekly_report_classify (object, strict)
├ team            string|null
├ team_name_raw   string|null
├ week_start      string        desc "YYYY-MM-DD"
└ formatted       object
  ├ thisWeek / nextWeek / issues / others : array<item>
  └ item (object)
    ├ assignee  string|null
    ├ category  string|null
    ├ text      string|null
    ├ progress  string|null
    └ due_date  string|null   desc "YYYY-MM-DD 또는 null"
```

- `due_date`에 `"format": "date"` **안 건다** — `LenientLocalDateDeserializer`("5/22" 등)와 충돌.
- `week_start`는 non-null string. 형식 위반 시 Jackson 실패 → 기존 재시도로 흡수. 월요일 정규화는 `upsertFromClassify`가 계속 담당.

### 2-3. `LlmService.kt`

- `ollamaClient`/`geminiClient`/`callOllama`/`callGemini` 삭제. 분기가 하나뿐이라 **`callLlm` 자체를 없애고** `callWithRetry`가 `callOpenRouter`를 직접 호출한다. 미설정 시 `LLM_SERVICE_UNAVAILABLE` 유지.
- `callWithRetry`/`callOpenRouter`에 `responseFormat`·`reasoning` 전달 배선. 두 파라미터는 **`parse` 앞**에 둔다 — 뒤에 두면 develop의 `answerChat` 후행 람다 호출이 머지 후 깨진다(§7). 대신 기존 호출부는 `parse = ::parseX` 명명 인자가 된다.
- `classifyWeeklyReport`에만 `CLASSIFY_RESPONSE_FORMAT` + `REASONING_OFF` 주입.
- `callOpenRouter` 안에서 **파싱 전에** 진단 로깅(`id`/`finish_reason`/`reasoning_tokens`/본문 길이).
- `init` 로그 문구 정리.

### 2-4. 설정·문서

- `application-{local,prod,stage}.yml`: `llm.gemini`, `llm.ollama` 블록 제거. (local의 `${OLLAMA_URL}`은 기본값이 없어 미설정 시 기동 실패 — 같이 해소)
- 배포 환경변수 `OLLAMA_URL/OLLAMA_MODEL/GEMINI_API_KEY/GEMINI_MODEL` 제거.
- `ChatLogService.kt:42` 주석("OpenRouter 외 provider…") 수정.
- `docs/llm-chat.md` 프로바이더 표·토큰 주석, `README.md` 기능 소개·환경변수 표 갱신 — **main 에 두 파일이 없어 PR #96 에서 제외했고, develop 머지 시점에 반영했다**.
- 삭제 대상 테스트 없음 (`chat/llm` 하위 테스트는 `dto`뿐, `LlmServiceTest` 부재). `ChatLogServiceTest`는 `OpenRouterProperties`만 써서 무영향.

## 3. `stripCodeFence` — 손대지 않는다

`stripCodeFence`는 classify 전용이 아니라 **`decodeJson`(intent/match/generate)과 `answerChat`이 공유**한다. 스키마는 classify에만 붙으므로 지우면 나머지 4개가 방어를 잃는다. 특히 `answerChat`은 평문 응답이라 재시도해도 같은 마크다운이 다시 오고, ` ``` `가 사용자에게 그대로 노출된다.

반대로 남겨도 손해가 없다 — 스키마가 붙으면 classify 응답에 코드펜스가 애초에 오지 않아 그 호출은 no-op이 된다. **`LlmService.kt` 변경을 시그니처 추가로만 한정**해 머지 표면도 최소가 된다(§7).

## 4. 검증 결과 (2026-08-24, local + OpenRouter `google/gemini-2.5-flash`)

3개 팀이 섞인 실제 주간보고를 `POST /chat` 으로 투입.

| 항목 | 결과 |
|---|---|
| 팀별 JSON 나열 | **소멸.** 최상위 객체 하나만 반환 |
| `strict:true` + `["string","null"]` 번역 | **통과** (400 없음) |
| `provider.require_parameters` 라우팅 | 통과 |
| `week_start` | LLM `2026-08-18`(화) → `upsertFromClassify` 가 `2026-08-17`(월)로 정규화. 정상 |
| `due_date` 관용 값 | `(8/20)` → `2026-08-20`. 정상 |
| 저장 | 200, 요청자 leader 팀(`team_id=15`)에 3개 팀 항목이 병합 저장 — 기획대로 |

### 남은 문제: 1차 응답 잘림

1차 시도 응답이 `{"team": "경` 에서 끊겨 `UnexpectedEndOfInputException`. 2차 시도에서 정상 응답.

- **structured outputs 가 못 막는 계열이다.** 스키마는 생성된 토큰의 문법을 보장할 뿐 생성 완료를 보장하지 않는다. 파싱 방어층·재시도를 남긴 판단(§3)이 여기서 값을 했다 — 걷어냈으면 500 이었다.
- 원인 미확정. 후보는 (a) 추론 토큰이 출력 예산 소모 (b) 프로바이더 출력 상한.
- 대응: classify 에 `reasoning.enabled=false` 를 걸고(원인과 무관하게 분류 작업엔 추론이 불필요), 동시에 `finish_reason`/`reasoning_tokens`/응답 `id` 를 **`callOpenRouter` 안, 파싱 전에** 로깅한다. 파싱이 실패하면 `LlmResult` 가 버려져 성공 시점 로깅으로는 이 케이스가 안 잡히기 때문.
- 재발 시 `finish_reason=length` 면 상한, `reasoning_tokens` 가 크면 추론이 범인. 응답 `id` 로 OpenRouter `GET /api/v1/generation?id=` 사후 조회 가능.
- **1회 관측이라 재현성 미확인.** reasoning 을 끈 뒤 재발 여부를 계속 봐야 한다.

### 회귀 확인 (같은 날)

| 경로 | 결과 |
|---|---|
| 기존에 통과하던 단일 팀 보고 3건 | 통과 |
| 주간보고 외 chat 요청(intent→generate/answer) | 통과 |
| 전주 보고 존재 시 `matchWeeklyReport` | 통과 — 스키마 미적용 경로라 방어층이 그대로 동작 |
| 같은 주차 재전송 UPSERT | 통과 |
| `./gradlew test` 전체 | 통과 |

### 부수 발견

- 실패한 시도의 usage 는 `chat_logs` 에 집계되지 않는다(성공 시도만 기록). 과금은 됐는데 비용이 과소 집계된다 — 별건.

## 5. 범위 밖 (기획 생기면)

- 멀티팀 배열 스키마 + 팀별 저장 루프, 여러 팀 감지 시 되물어보기, 서버측 팀 검증
- intent/match/generate 스키마화 — 그때 `stripCodeFence` 정리도 함께
- Gemini 직접 호출 경로 복구, 토큰 사용량 추출 TODO
- 실패한 재시도의 토큰·비용 집계
- 모델 교체 시 재검증: `GET /api/v1/models/{model}/endpoints` 의 `supported_parameters` 에 `structured_outputs`·`response_format` 이 있는지 확인 후 실제 호출 1회. 미지원 모델이면 `response_format` 이 조용히 무시된다

## 6. 롤백

`responseFormat = null`로 두면 요청 바디가 현행과 동일해진다. 프로바이더 제거만 되돌리기 어려우니 **프로바이더 정리와 스키마 도입은 커밋을 분리**한다.

## 7. 브랜치 영향 (main에서 분기할 경우)

`main`은 `develop`에 완전히 포함됨 (main 0 ahead / develop 18 ahead). 조사 결과:

**충돌 없음 (main == develop)**: `LlmApiDto.kt`, `LlmProperties.kt`, `ChatLogService.kt`, `application-{local,prod,stage}.yml` — 스펙 변경분의 대부분이 여기 속한다.

**주의 1 — `LlmService.kt`.** develop만 `answerChat` +6줄을 갖는다(`a14b0cb`). 삽입 위치가 `classifyWeeklyReport` 세 줄 위라 시그니처 변경과 컨텍스트가 겹쳐 텍스트 충돌이 날 수 있다 — 뜨면 양쪽 다 채택하면 끝나는 종류다. 진짜 위험했던 건 이게 아니라 `stripCodeFence` 삭제였다: 두 변경이 파일의 다른 위치라 git이 충돌로 잡지 않고 머지된 뒤 빌드가 깨진다. §3대로 함수를 그대로 두면 이 경로는 존재하지 않는다.

**주의 2 — 문서.** `README.md`와 `docs/llm-chat.md`는 **main에 없다**(develop에서 신규 추가). §2-4의 문서 갱신은 main 커밋에서 빼고 develop 머지 후에 한다.

**주의 3 — 검증 환경 차이.** main엔 `requireReportBody`/`requireClassifiedItems`(`f3fbdd2`), Testcontainers 통합 테스트(`a3ace9e`), V1 baseline 마이그레이션(`4e2254d`)이 없다. main 브랜치에서의 테스트 결과가 develop과 다를 수 있다.

**chat-v2 브랜치**: `feature/chat-v2-*`에도 같은 설계의 structured output이 이미 있다 — `ChatV2LlmClient.callStructured`, `ChatV2WeeklyReportSchemas`, `ResponseFormat`/`JsonSchemaSpec`. 패키지가 `chat/v2`라 텍스트 충돌은 없지만 **타입이 중복된다**. 이번 작업은 chat-v2를 참조하지 않고 `chat/llm` 아래에 독립 구현했으므로, chat-v2가 살아날 경우 둘을 합치는 판단이 따로 필요하다. 단 그 브랜치들은 7월 stale이라 보안 커밋 이전 상태여서, 이 변경과 무관하게 이미 충돌이 예정돼 있다.
