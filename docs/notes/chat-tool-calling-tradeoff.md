# Chat 파이프라인 Tool Calling 전환 검토 (2026-07-06)

> 결론 요약: 현재 구조의 문제 절반(파싱 실패·환각·매칭 오류)은 "텍스트로 JSON을 받는" 구조 비용이며, tool calling 전환 시 구조적으로 사라진다. 다만 FE 계약 재설계가 딸려오는 큰 공사이므로 **지금 당장이 아니라 명확한 트리거(하단)가 왔을 때 전환**을 권장.

---

## 1. Tool Calling이란

모델이 호출할 수 있는 함수 목록을 API 요청에 스키마로 등록하면, 모델이 텍스트 대신 "이 함수를 이 인자로 실행해달라"는 **구조화된 응답**(`tool_calls` 필드)을 돌려주는 LLM API 기능. 서버가 함수를 실행해 결과를 다시 모델에게 주고, 모델이 다음 행동 또는 최종 자연어 답변을 생성하는 **루프** 구조.

### 현재 방식 vs Tool Calling

```
[현재 — 수제 2단 파이프라인]
사용자 입력
 → ① intent LLM (텍스트 JSON 출력 → 파싱)
 → ② 서버가 DB 전체 스냅샷을 프롬프트에 주입 (context stuffing, 턴당 ~4.7k 토큰)
 → ③ action LLM (텍스트 JSON 출력 → stripCodeFence → 파싱 → 검증)
 → ④ ChatActionRouter가 라우팅 (액션 1개 고정)

[Tool Calling]
사용자 입력 + tool 스키마 등록
 → 모델: search_tasks(name="크롤러") 호출 요청
 → 서버: 실행, 결과 반환 (필요한 데이터만)
 → 모델: update_task(id=1004, progress=80) 호출 요청
 → 서버: 실행
 → 모델: "'크롤러 개발' 진행률을 80%로 수정했어요." (최종 자연어)
```

핵심: **현재 구조는 사실상 tool calling을 손으로 구현한 것**이다. intent 분류 = tool 선택, `LlmAction` JSON = tool arguments, `ChatActionRouter` = tool executor, clarify = 모델의 되물음. 전환은 개념 변경이 아니라 수제 부품 → 표준 부품 교체에 가깝다.

---

## 2. 왜 우리 시스템에 적합한가 — 실제 겪은 문제 기준

2026-07 chat_logs·개선 작업에서 확인된 문제와, 각각이 전환 시 어떻게 되는지:

| 실제 겪은 문제 | 현재 대응 (프롬프트/코드 방어) | Tool calling에서는 |
|---|---|---|
| 주간보고 환각: 본문 없이 "작성해줘" → LLM이 가짜 보고서 마크다운 생성 → JSON 파싱 실패 500 | 핸들러 본문 검증 + 프롬프트 규칙 2겹 | 응답이 구조화 필드로 와서 **파싱 실패 자체가 없음** |
| "OO PM 보여줘" 같은 존재하지 않는 명령 안내 (환각) | "지원 기능 표 외 명령 발명 금지" 규칙 | 등록된 tool만 호출 가능 — **구조적으로 불가능** |
| "safers 완료 처리" → task에서만 찾다 not-found | 매칭 규칙 8(target 정정) 하드코딩 | 모델이 search_tasks → search_projects로 **스스로 재시도** |
| "이번주 마감" → `due_date_from` 누락으로 과거 마감 전부 조회 | 프롬프트에 날짜 범위 규칙 추가 | 날짜 해석을 tool 구현부(Kotlin)에서 **결정적으로 처리** |
| "용인 플랫폼 PM 누구야" → answer는 데이터를 못 봐서 조회 명령 안내만 | intent를 read로 유도 | 모델이 직접 조회 후 **"PM은 윤지선님입니다" 즉답** |
| 데이터 증가 시 context stuffing 토큰 폭증 (전체 스냅샷 주입) | 조회 범위 제한(ChatScope)으로 완화 | 필요한 것만 tool로 조회 — **토큰이 데이터 규모와 분리** |
| `stripCodeFence`, blank 방어, 3회 재시도 계층 | LlmService에 직접 구현 | 불필요 |

요약: 이번 v0.6.x 개선에서 한 수정의 상당수가 **증상 치료**였고, tool calling은 그 원인("LLM을 프롬프트로 통제")을 제거한다. 유지보수 지점이 250줄짜리 system-prompt에서 **컴파일러가 잡아주는 tool 스키마 코드**로 이동한다.

---

## 3. 장단점

### 얻는 것

- **정확성**: JSON 스키마 보장, 명령 환각 불가, 이름 매칭·날짜 계산이 서버 코드로 이동 (LLM 추론 → 결정적 로직)
- **확장성**: 턴당 토큰이 데이터 규모와 무관해짐. 태스크 1만 건이어도 비용 유사
- **UX 상한**: 멀티스텝 자가 복구(검색→정정→실행), 데이터 질문 즉답, 자연스러운 최종 응답이 기본 동작
- **유지보수**: 기능 추가 = tool 하나 추가. 프롬프트 규칙+few-shot+promptfoo 동시 수정 부담 감소

### 잃는 것 / 새로 생기는 비용

- **FE 계약 전면 재설계**: 현재 "폼 조립 JSON"(dto/selectFields/clarify 400 흐름) 계약이 근본적으로 달라짐 — 전환 비용의 대부분
- **레이턴시**: LLM 2회 고정 → 스텝당 왕복(3~5회 가능). 단순 요청은 느려질 수 있음 (스트리밍으로 체감 완화 필요)
- **통제력 재설계**: "update는 폼 확인, delete는 즉시" 같은 흐름을 지금은 서버가 100% 결정. 전환 후엔 tool 구현부에 가드레일(권한 체크, 확인 단계)을 다시 심어야 함
- **회귀 테스트 난이도**: promptfoo 단발 입출력 검증 → 멀티스텝 시나리오 검증으로 상승
- **provider 제약**: OpenRouter(gemini-2.5-flash)는 tool calling 지원. 단 Ollama 로컬 폴백은 모델별 품질 편차 큼 — LlmService의 3-provider 추상화 재검토 필요

### 유지되는 것

- 권한 체크(AuthorizationService), 도메인 서비스 로직은 그대로 tool 구현부가 됨
- ChatLog 토큰·비용 집계 (usage는 동일하게 응답에 옴)
- Redis 분산락, 히스토리 저장 구조

---

## 4. 전환 시 tool 목록 초안 (현재 액션 매핑)

현재 `ChatActionRouter`가 처리하는 액션이 그대로 tool이 된다:

| tool | 매핑되는 현재 코드 | 비고 |
|---|---|---|
| `search_tasks / search_epics / search_projects / search_teams` | ChatReadHandler + 각 Service.search | 이름·상태·기간 파라미터. 날짜 해석("이번주")은 서버에서 |
| `create_task / update_task ...` | ChatDtoMapper → 폼 반환 | "폼 확정" 정책 유지 시 tool 결과 = 폼 DTO |
| `delete_task`, `request_review`, `assign / unassign` | ChatExecutor | 즉시 실행 계열 |
| `create_weekly_report ...` | WeeklyReportChatHandler | classify는 유지 (paste 정제는 별개 문제) |
| `ask_user` | clarify/select 흐름 | 후보 선택 UI 트리거 |

---

## 5. 전환 판단 기준 (트리거)

다음 중 하나가 관측되면 전환 착수를 권장:

1. **토큰 비용**: context stuffing 토큰이 데이터 증가로 턴당 10k+ 상시화 (chat_logs의 intent/action_input_tokens 추이로 확인)
2. **정확도 한계**: 프롬프트 규칙을 추가해도 promptfoo 회귀·실사용 오분류가 잡히지 않는 상태 반복
3. **기능 요구**: 멀티스텝이 필수인 기능(예: "지연된 태스크 전부 다음주로 미뤄줘" 같은 일괄 작업) 요구 발생

전환 전 저비용 중간 단계 옵션: 현재 구조 유지 + OpenAI 호환 **structured output(json_schema response_format)** 만 도입 — 파싱 실패·환각 계열만 먼저 제거 가능 (OpenRouter 지원 모델 한정). FE 계약 변경 없음.

---

## 참고

- 현재 파이프라인 코드: `chat/service/ChatService.kt`, `chat/llm/LlmService.kt`, `chat/service/ChatActionRouter.kt`
- 프롬프트: `src/main/resources/llm/*.txt`
- v0.6.x 개선 배경: `docs/notes/chat-ux-improvement.md`
- OpenRouter tool calling 문서: https://openrouter.ai/docs/features/tool-calling
