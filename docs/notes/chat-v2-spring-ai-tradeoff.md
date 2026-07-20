# Spring AI 도입 트레이드오프 (chat-v2)

> 질문: chat-v2는 tool 스키마·wire DTO·tool 루프·structured output 스키마를 **전부 수작업**한다. Spring AI가 `@Tool`로 스키마 자동생성, ChatClient가 루프 자동, `.entity()`로 structured output 자동이라는데 — 도입할까? (조사: 2026-07-20, 아래 출처)

## 1. Spring AI가 자동화하는 것 (현 수작업 → Spring AI)

| 현재 (수작업) | Spring AI |
|---|---|
| `ChatV2Tools` — JSON 스키마 Map 수동 조립 | `@Tool`/`@ToolParam` 메서드 → **리플렉션으로 스키마 자동생성** |
| `ChatV2ToolArgs` — tool_calls 역직렬화 DTO 6종 | `@Tool` 메서드 파라미터 → 자동 바인딩 |
| `ChatV2Service` — tool_calls 루프 수동 | `ChatClient` + `ToolCallingAdvisor` → **루프 투명 처리** |
| `ChatV2LlmClient` + `dto/ChatV2ApiDto` — OpenAI wire DTO 수동 | Spring AI OpenAI 클라이언트 내장 |
| `ChatV2WeeklyReportSchemas` — structured output JSON 스키마 수동 | `BeanOutputConverter`/`.entity()` → 스키마 생성+프롬프트+파싱 자동 (2.0: self-correcting) |
| OpenRouter 전용 클라이언트 | OpenAI starter + `base-url` 오버라이드 (OpenRouter는 OpenAI 호환) |

→ 우리가 앓던 **"필터 1개 = 스키마+DTO+핸들러 3점 편집" 중복**을 상당 부분 없앤다. `@Tool` 메서드 하나 = 스키마+바인딩+실행이 한 곳.

## 2. Pros (도입 이점)

1. **보일러플레이트 대량 제거** — 스키마 Map, wire DTO, 루프 코드 삭제.
2. **스키마 드리프트 제거** — 메서드/DTO가 단일 소스, 스키마 자동 파생. (우리 "단일 필터 레지스트리" 소망을 프레임워크가 해결)
3. **structured output 자동** — `.entity()`가 DRAFT_2020_12 스키마 생성 + 파싱 + 2.0 self-correcting.
4. **프로바이더 이식성** — OpenRouter/OpenAI/Anthropic/Ollama 전환 용이 (조회 폴백 미결 해소 가능).
5. **생태계** — MCP tools, advisor, observability 내장.

## 3. Cons / 리스크 (우리 가치와 충돌하는 지점)

1. **가드레일 통제력 — 핵심 쟁점.** chat-v2의 진짜 가치는 tool 목록이 아니라 **환각 바운드 가드레일**이다.
   - 이름→id 해소, IdRegistry, `FAIL_ON_UNKNOWN`, error 반환 → **@Tool 메서드 본문에 그대로 넣을 수 있음** (마찰 적음).
   - **루프 통제(MAX_STEPS, error 후 재유도, 초과 시 graceful 안내, per-step cached_tokens 로깅)** → `ToolCallingAdvisor`의 "투명한 루프"가 **감춘다.** 되찾으려면 `internalToolExecutionEnabled=false`로 자동실행 끄고 **수동 루프** → 자동 루프 이점이 반감.
2. **스키마 제어 정밀도 손실.** 우리 structured output 스키마는 의도적 반(反)환각 구조 — **최상위 단일 팀 오브젝트**(3팀 환각 차단), `additionalProperties=false`, `["string","null"]` union. 자동생성은 DTO 모양에 종속이라 일부만 제어됨.
   - ⚠️ **실측 함정**: `BeanOutputConverter`가 Swagger `@Schema` 설명을 **무시**한다 ([Issue #5341](https://github.com/spring-projects/spring-ai/issues/5341)) → `@JsonPropertyDescription`/`@ToolParam`로 **전면 재작성 필요.** 우리 DTO는 `@Schema` 투성이.
3. **cached_tokens·커스텀 헤더 불확실.** 우리는 OpenRouter `prompt_tokens_details.cached_tokens` 로깅 + `HTTP-Referer`/`X-Title` 헤더를 쓴다. Spring AI가 이 둘을 노출/주입하는지 **검증 필요**.
4. **Kotlin 마찰.** Spring AI는 Java-first. data class nullable→optional 매핑, 리플렉션 스키마의 Kotlin 특이점.
5. **버전 변동성.** tool calling API가 M6→1.1→2.0에서 계속 바뀜(FunctionCallback→ToolCallback→@Tool). 이식 후 업그레이드 비용.
6. **마이그레이션 비용 + 시점.** LlmClient·Service 루프·Tools·structured output 재작성. **PoC로 아키텍처를 검증 중인데 기반을 갈아엎는 것.**

## 4. 판단 프레임

- **Spring AI는 보일러플레이트를 줄이지, 가드레일을 주지 않는다.** 환각 바운드는 이식 후에도 우리가 @Tool 본문·수동 루프로 넣어야 한다.
- "스키마 자동생성"은 진짜 이득(3점 중복 해소)이지만, **그거 하나 때문에 검증 중인 PoC 기반을 프레임워크로 갈아엎는 건 시점상 위험** — A/B(7 vs 6 tool)·history 실험 신호가 프레임워크 이슈와 섞여 오염된다.

## 5. 권고

- **지금 전면 도입 ✗.** PoC는 아키텍처(tool 루프 + 스코프 + 가드레일)를 검증 중. 프레임워크 이식은 아키텍처가 굳은 뒤 "구현 최적화"로 결정할 일.
- **대신 timeboxed 스파이크** (별도 브랜치): `@Tool` 2~3개 + `ChatClient` 루프 + OpenRouter 최소 재현으로 **4대 불확실성만** 확인 —
  1. `internalToolExecutionEnabled=false` 수동 루프로 MAX_STEPS/graceful 통제 유지되나
  2. cached_tokens 노출되나
  3. 커스텀 헤더(HTTP-Referer/X-Title) 주입되나
  4. Kotlin nullable → 스키마 optional 정상 매핑되나
  → 결과로 "이식 가능/비용" 판정.
- **순서**: (a) enum 리팩토링 · eval · history 판정을 먼저 마쳐 **아키텍처를 확정** → 그다음 Spring AI 이식 검토. 이식은 아키텍처가 굳은 뒤가 안전.

## 6. 이식하기로 하면 지킬 것 (체크리스트)

- 이름→id 해소 · IdRegistry · FAIL_ON_UNKNOWN → @Tool 본문/역직렬화 설정에 유지
- 루프 통제(MAX_STEPS·graceful·error 재유도) → `internalToolExecutionEnabled=false` 수동 루프
- structured output 반환각 구조(단일 팀·additionalProperties=false) → DTO 모양 + `getJsonSchema()`(2.0) 커스텀
- cached_tokens·헤더 → 스파이크에서 검증 후 유지 or 손실 감수
- 설명은 `@Schema` 말고 `@ToolParam`/`@JsonPropertyDescription`

## 출처

- [Tool Calling :: Spring AI Reference](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Tool Calling in Spring AI 2.0: Composable, Agentic Architecture](https://spring.io/blog/2026/06/15/spring-ai-composable-tool-calling/)
- [Structured Output Converter :: Spring AI Reference](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Self-Correcting Structured Output in Spring AI 2.0](https://spring.io/blog/2026/06/23/spring-ai-self-correcting-structured-output/)
- [Issue #5341 — BeanOutputConverter ignores @Schema](https://github.com/spring-projects/spring-ai/issues/5341)
- [Integrate OpenRouter with Spring AI (BootcampToProd)](https://bootcamptoprod.com/integrate-openrouter-with-spring-ai/)
- [OpenAI Chat :: Spring AI Reference](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
