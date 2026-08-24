# OpenRouter Structured Outputs 도입 검토 — 주간보고 classify

> **이 문서는 도입 전 조사 기록이다. 확정 스펙과 실제 구현은 [structured-outputs-spec.md](./structured-outputs-spec.md).**
> 여기 §4·§6의 설계안(리소스 JSON 파일, `$ref`)은 채택되지 않았다 — 스키마는 Kotlin 상수 + `$ref` 인라인으로 갔다.

> 대상: `chat` classify(주간보고 정제) 경로 **우선**. 다른 LLM 호출(intent/match/generate/answer)은 후속.
> 배경: `temperature 0.1` 비결정성 + 멀티팀 입력 → LLM이 팀별로 ```json 객체를 쪼개 반환 → `Unexpected character ('`')` 파싱 실패.

---

## 0. TL;DR

- **`google/gemini-2.5-flash`는 OpenRouter에서 `response_format`(json_schema) + `structured_outputs`를 지원한다.** 모든 Google 엔드포인트(Vertex AI / AI Studio) 동일. → **도입 가능**.
- 도입하면 **파싱 실패(코드펜스·백틱·잘못된 JSON) 계열이 원천 소멸**한다. 강제는 OpenRouter가 아니라 **Gemini 내부의 constrained decoding**이 수행하고, OpenRouter는 OpenAI식 `response_format`을 Gemini 네이티브 `responseSchema`로 번역만 한다.
- **단, "멀티팀 데이터 보존"은 스키마 설계 문제로 별개다.** 지금 스키마(`WeeklyReportClassifyResult`)는 단일 팀이고 **caller도 `teams.first()`로 첫 팀만 저장**하므로, 우선 단계는 **단일 팀 스키마로 강제(현 동작과 일치, 크래시 제거)** 하고, 멀티팀 배열화는 caller까지 함께 고치는 후속 과제로 분리한다.
- `decodeJson` 방어층 + 방금 넣은 재시도 hotfix는 **그대로 유지**한다 (프로바이더가 파라미터를 조용히 무시하는 경우 대비).

---

## 1. 지원 여부 확인 결과

OpenRouter API로 엔드포인트별 `supported_parameters` 조회:

- `GET /api/v1/models/google/gemini-2.5-flash/endpoints`
- 6개 엔드포인트(Google Vertex AI EU/Global/Priority, Google AI Studio Standard/Flex/Priority) **모두** 아래 포함:

```
reasoning, include_reasoning, structured_outputs, response_format,
max_tokens, temperature, top_p, seed, tools, tool_choice, stop
```

→ `response_format` ✅ / `structured_outputs` ✅ / `tools` ✅ / `temperature` ✅

OpenRouter 공식 문서(Structured Outputs)도 "Google Gemini: Full support"로 명시.

---

## 2. Structured Outputs ≠ Tool Calling (정리)

| | Tool calling | Structured Outputs (`response_format`) |
|---|---|---|
| 개념 | 모델이 함수 호출 여부를 스스로 결정 | 응답 자체를 무조건 스키마 JSON으로 |
| 요청 | `tools` + `tool_choice` | `response_format: {type:"json_schema"}` |
| 응답 위치 | `message.tool_calls[].function.arguments` | `message.content` (그 자체가 JSON) |
| 용도 | 액션 선택(에이전트) | **고정 틀 데이터 추출(= classify)** |

→ classify엔 **Structured Outputs**가 직결. tool call 불필요.

### OpenRouter → Gemini 강제 원리

```
요청(OpenAI 형식)              OpenRouter 번역              Gemini(Google)
response_format:      ──────►  generationConfig:    ──────►  디코더가 스키마로
 {json_schema,strict}          responseMimeType:             제약된 토큰만 생성
                                "application/json"           (constrained decoding)
                               responseSchema:{...}
```

매 토큰 생성 시 **스키마를 유효하게 유지하는 토큰만 후보**로 허용 → 백틱·설명문·객체 3개 나열 등이 문법적으로 불가능.

---

## 3. 현재 코드 흐름 (classify)

- `WeeklyReportChatHandler.createOrUpdate()` → `llmService.classifyWeeklyReport(messages)` (`WeeklyReportChatHandler.kt:101`)
- `LlmService.classifyWeeklyReport()` → `callWithRetry(messages, "LLM classify", ::parseClassify)` (`LlmService.kt:83`)
- `callWithRetry` → `callLlm` → **`callOpenRouter`** (`LlmService.kt:126`) — 여기서 `OpenAiChatRequest` 조립
- `OpenAiChatRequest`(`LlmApiDto.kt:51`): `model / messages / temperature` **뿐, `response_format` 없음**
- 응답 → `parseClassify` → `decodeJson` → `objectMapper.readValue(WeeklyReportClassifyResult)`

### 관련 사실

- **caller가 이미 단일 팀만 저장**: `teams.first()` + 주석 "다중 팀 leader는 후속 — 일단 첫 팀" (`WeeklyReportChatHandler.kt:103~109`). 멀티팀은 저장 단계에서도 미대응.
- `WeeklyReportClassifyResult`: `team?`, `team_name_raw?`, `week_start`(LocalDate), `formatted`(단일).
- `ReportItem.due_date`는 `LenientLocalDateDeserializer`(`5/22` 등 허용). Structured Outputs로 엄격 date를 강제하면 이 관용 처리와 상충 가능 → **due_date는 `string|null`로 두고 기존 관용 파서 유지 권장**.
- Jackson은 3.x(`tools.jackson.*`). `@JsonInclude`/`@JsonProperty` 애노테이션 패키지는 여전히 `com.fasterxml.jackson.annotation.*`.

---

## 4. 도입 설계 (classify only, 단일 팀 우선)

### 4-1. 요청 DTO 확장 (`LlmApiDto.kt`)

```kotlin
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.JsonNode   // 또는 Map<String, Any?>

@JsonInclude(JsonInclude.Include.NON_NULL) // response_format=null이면 직렬화에서 제외 (다른 호출에 영향 X)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double,
    @param:JsonProperty("response_format")
    val responseFormat: ResponseFormat? = null,
    // 선택: 스키마 미지원 프로바이더로 라우팅되는 것 방지
    val provider: ProviderPreferences? = null,
)

data class ResponseFormat(
    val type: String,                       // "json_schema"
    @param:JsonProperty("json_schema") val jsonSchema: JsonSchemaSpec,
)

data class JsonSchemaSpec(
    val name: String,
    val strict: Boolean,
    val schema: JsonNode,                   // resources에서 로드한 스키마
)

data class ProviderPreferences(
    @param:JsonProperty("require_parameters") val requireParameters: Boolean = true,
)
```

### 4-2. classify 전용으로 스키마 주입 경로

`callLlm`/`callWithRetry`는 intent/match/generate/answer가 공유하므로, **optional 파라미터로 흘려보내되 OpenRouter에서만 사용**한다:

```kotlin
private fun <T> callWithRetry(
    messages: List<Message>,
    label: String,
    parse: (String) -> T,
    responseFormat: ResponseFormat? = null,   // 추가
): LlmResult<T> { ... callLlm(messages, responseFormat) ... }

private fun callLlm(messages: List<Message>, responseFormat: ResponseFormat? = null) =
    when {
        properties.openrouter.isEnabled -> callOpenRouter(messages, responseFormat)
        properties.gemini.isEnabled     -> callGemini(messages)   // 후속: responseSchema 직접 매핑
        properties.ollama.isEnabled     -> callOllama(messages)   // format:json 별도
        else -> throw CustomException(ErrorCode.LLM_SERVICE_UNAVAILABLE)
    }

private fun callOpenRouter(messages: List<Message>, responseFormat: ResponseFormat? = null): LlmResult<String> {
    val request = OpenAiChatRequest(
        model = props.model,
        messages = messages,
        temperature = properties.temperature,
        responseFormat = responseFormat,
        provider = responseFormat?.let { ProviderPreferences(requireParameters = true) },
    )
    ...
}

// classify만 스키마 지정
fun classifyWeeklyReport(messages: List<Message>): LlmResult<WeeklyReportClassifyResult> =
    callWithRetry(messages, "LLM classify", ::parseClassify, responseFormat = CLASSIFY_RESPONSE_FORMAT)
```

`CLASSIFY_RESPONSE_FORMAT`은 리소스 파일에서 1회 로드(프롬프트 파일과 동일 패턴):
`ClassPathResource("llm/weekly-report-classify-schema.json")` → `objectMapper.readTree(...)`.

### 4-3. classify JSON Schema (단일 팀, strict)

`resources/llm/weekly-report-classify-schema.json`:

```jsonc
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "team":          { "type": ["string", "null"], "description": "팀명(마지막 팀 단위). 추정 불가 시 null" },
    "team_name_raw": { "type": ["string", "null"], "description": "부서·본부 포함 원문 팀명 표기" },
    "week_start":    { "type": "string", "description": "주차 시작일 YYYY-MM-DD" },
    "formatted": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "thisWeek": { "type": "array", "items": { "$ref": "#/$defs/item" } },
        "nextWeek": { "type": "array", "items": { "$ref": "#/$defs/item" } },
        "issues":   { "type": "array", "items": { "$ref": "#/$defs/item" } },
        "others":   { "type": "array", "items": { "$ref": "#/$defs/item" } }
      },
      "required": ["thisWeek", "nextWeek", "issues", "others"]
    }
  },
  "required": ["team", "team_name_raw", "week_start", "formatted"],
  "$defs": {
    "item": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "assignee": { "type": ["string", "null"] },
        "category": { "type": ["string", "null"] },
        "text":     { "type": ["string", "null"] },
        "progress": { "type": ["string", "null"] },
        "due_date": { "type": ["string", "null"], "description": "YYYY-MM-DD 또는 null" }
      },
      "required": ["assignee", "category", "text", "progress", "due_date"]
    }
  }
}
```

> ⚠️ **Gemini 스키마 서브셋 주의**: Gemini `responseSchema`는 JSON Schema 전체가 아니라 OpenAPI 서브셋이다. `$defs`/`$ref`·`additionalProperties`·union type(`["string","null"]`) 지원이 프로바이더/번역 버전에 따라 다를 수 있다. **가능하면 `$ref`를 인라인**하고, nullable/strict 조합은 실제 요청으로 한 번 검증한 뒤 확정한다. strict가 "모든 필드 required + additionalProperties:false"를 요구하는 점도 현 DTO의 `@JsonIgnoreProperties(ignoreUnknown=true)`(여분 필드 허용) 철학과 반대이므로, 스키마엔 필요한 필드만 정확히 정의한다.

### 4-4. 방어층 유지

- `stripCodeFence` + `decodeJson`의 blank/파싱 예외 처리 **유지**.
- 재시도 hotfix(파싱 실패 = `LLM_INVALID_RESPONSE` 재시도) **유지**.
- 이유: 프로바이더가 `response_format`을 조용히 무시하거나, 스키마 번역이 실패해 자유 텍스트가 돌아올 여지가 남는다.

---

## 5. 멀티팀 결정 포인트 (별도 과제)

| 방식 | 파싱 크래시 | 멀티팀 데이터 | caller 변경 |
|---|---|---|---|
| **단일 팀 스키마 강제** (우선 단계) | 제거 ✅ | 첫 팀만(현 동작 유지) | 불필요 |
| **팀 배열 스키마** (후속) | 제거 ✅ | 전 팀 보존 ✅ | **필요** (`teams.first()` → 팀별 매칭·UPSERT 루프) |

- 우선 단계는 **단일 팀 스키마**: 지금도 caller가 첫 팀만 저장하므로 동작이 바뀌지 않고, 크래시만 사라진다.
- 멀티팀 정식 지원은 스키마를 `{ "teams": [ ... ] }` 배열로 바꾸고 **`WeeklyReportChatHandler`의 저장 로직까지** 팀별 루프로 확장해야 한다 (leader가 여러 팀인지 검증, 팀별 `matchAgainstPrev` + `upsertFromClassify`). 프롬프트에도 "팀이 여러 개면 각 팀을 배열 원소로" 규칙 추가.

---

## 6. 구현 순서 (체크리스트)

1. [ ] `resources/llm/weekly-report-classify-schema.json` 작성 (단일 팀, strict, `$ref` 인라인)
2. [ ] `OpenAiChatRequest`에 `responseFormat`/`provider` 추가 + `@JsonInclude(NON_NULL)`, 관련 DTO(`ResponseFormat`/`JsonSchemaSpec`/`ProviderPreferences`) 추가
3. [ ] `LlmService`: `callWithRetry`/`callLlm`/`callOpenRouter`에 optional `responseFormat` 배선, classify 상수 로드 및 주입
4. [ ] 실제 요청으로 검증: (a) 멀티팀 입력이 단일 객체로 오는지 (b) strict/nullable 스키마가 Gemini에서 통과하는지 (c) `require_parameters`로 라우팅 확인
5. [ ] `decodeJson` 방어층 + 재시도 유지 확인
6. [ ] (후속) 멀티팀 배열 스키마 + `WeeklyReportChatHandler` 팀별 루프

---

## 7. 리스크 / 주의

- **스키마 번역 실패 여지**: Gemini responseSchema 서브셋 제약 → 첫 도입 시 반드시 실제 호출로 검증.
- **thinking 토큰 빈 응답**은 Structured Outputs로도 안 사라진다(파싱이 아닌 생성 실패). 별도 max_tokens/재시도로 관리.
- **due_date 관용 처리**와 strict date의 상충 → `string|null` 유지.
- **다른 호출(intent/match/generate/answer)**엔 아직 미적용. classify 검증 후 동일 패턴으로 확장.

---

## Sources

- OpenRouter Structured Outputs 문서: https://openrouter.ai/docs/features/structured-outputs
- 모델 엔드포인트 파라미터: https://openrouter.ai/api/v1/models/google/gemini-2.5-flash/endpoints
- 코드 근거: `LlmService.kt`(callOpenRouter/callWithRetry/classifyWeeklyReport), `LlmApiDto.kt`(OpenAiChatRequest), `WeeklyReportClassifyResult.kt`, `FormattedReport.kt`, `WeeklyReportChatHandler.kt`(createOrUpdate)
