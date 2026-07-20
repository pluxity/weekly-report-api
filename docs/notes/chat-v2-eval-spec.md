# chat-v2 Eval Spec (promptfoo HTTP provider)

> ⚠️ 로컬 실험용 스펙. 커밋 제외. 대상 데이터는 **로컬 도커(weekly-report-db)의 운영 복원본** — 프로덕션 아님. (작성: 2026-07-20)

## 1. 목적

- **주목적**: `get_item_details`를 `search_items`의 `response_format`(detailed/concise) enum으로 흡수하는 리팩토링(=(a))을 **감이 아니라 숫자로** 검증 — 현 **7-tool vs 6-tool** A/B 비교. (근거: Anthropic "writing tools for agents" — tool 수 최소화, 상세는 모드로)
- **부수 측정**: 역할별 스코프 정확도(팀원/관리자/admin), tool 선택 정확도, 토큰/스텝 비용.
- **방법**: promptfoo **HTTP provider**로 `/chat/v2` **전체 루프**를 실서버에 돌려, 응답 `steps[]`·토큰을 측정. (LLM의 실제 tool 선택을 봐야 하므로 mock 무의미)

## 2. 비범위 (이번 스펙에서 제외)

- **멀티턴 오염**(프로젝트1→2→2상세) — promptfoo가 테스트를 병렬·임의순서로 돌려 history 순서 공유가 깨짐. **별도 수동 시나리오**로 검증(설계 노트 §6 history 항목).
- **기존 v1 promptfoo**(`promptfooconfig.yaml`) — v1 프롬프트 회귀용, **손대지 않음**. v2는 새 config 분리.
- 부하/동시성/레이턴시 SLA — 기능 정확도만.

## 3. 테스트 유저 (로컬 복원 데이터, 비번 리셋)

새 유저 생성 ❌ (팀·태스크·역할 없어 조회가 전부 빈 결과). 기존 데이터 그래프 안의 유저 3명 비번만 리셋.

| 페르소나 | username | id | 데이터 | 검증 대상 |
|---|---|---|---|---|
| **관리자** | `shyoon` | 14 (윤승현) | 마케팅팀 **리더**(멤버 2·태스크 22) + **PM 5개 프로젝트**, 본인 태스크 10 | `team_me`·`pm_me`·겸직 |
| **팀원** | `dkfkqpffk` | 48 (임우정) | **무역할**, 본인 태스크 5, DT Tech Unit 멤버 | `assignee_me` 페르소나 프레이밍 |
| **admin** | `admin` | 1 (관리자) | ADMIN, 태스크 6 | 전체/임의 프로젝트 스코프 |

- 공통 비번: `evaltest123` (6~20자 제약 충족)
- **리셋 방식**: `/auth/sign-up`으로 throwaway 유저 1개 생성 → 앱이 만든 올바른 해시를 DB에서 읽어 → 위 3 row의 `password`에 UPDATE 복사 (bcrypt 툴 불필요, 앱 해싱 재사용)

## 4. 인증

- `POST /auth/sign-in {username, password}` → 세션 쿠키(Set-Cookie)
- 유저별 쿠키 1회 획득 → promptfoo provider `headers.Cookie`에 주입 (유저마다 다르므로 케이스를 유저별로 분리)

## 5. 하네스 (promptfoo v2 config)

- 새 파일: `promptfoo/promptfooconfig.v2.yaml`
- **providers = A/B 두 서버**:
  - A: `https://localhost:8080/chat/v2` — 현 브랜치(7-tool)
  - B: `https://localhost:8081/chat/v2` — (a) 브랜치(6-tool+enum)
  - config: `method: POST`, `headers: {Content-Type, Cookie}`, `body: {message: "{{message}}"}`, `transformResponse: json`
- `repeat: 3` (LLM 비결정성 → 다수결/평균)
- 유저별 쿠키가 다르므로 **페르소나별로 config 파일 or provider 분리** (예: shyoon/dkfkqpffk/admin)

## 6. 테스트 케이스

### 6-1. 정밀 케이스 — `steps[]` 정확 assert (변화가 landing했나)

| 유저 | 메시지 | 기대 (steps assert) |
|---|---|---|
| 팀원 | "내 남은 일" | search_items: `assignee_me=true` + `exclude_done=true` |
| 팀원 | "이번주 내가 한 일" | search_items: `completed_from`/`completed_to` |
| 관리자 | "우리팀원 누구있지" | search_items: `type=team` + `team_me=true` |
| 관리자 | "우리 팀 지연된 거" | search_items: `team_me=true` (+ 지연: exclude_done/due_date_to) |
| 관리자 | "내 프로젝트 진행률" | search_items or aggregate: `pm_me=true` |
| **모두** | **"cctv api 상세히"** | **A: get_item_details 호출 / B: search_items `detail=detailed`** ← A/B 판별 |
| 팀원 | "AA 태스크 완료해줘" | tool 호출 없음 + reply에 "보드/폼" 안내 (CUD 거절) |

### 6-2. 복잡 케이스 — 최종 reply를 `llm-rubric`로 판정 + 메트릭

정답 경로가 여러 개라 tool 시퀀스로 못 잡음. 메시지 하나 던지면 서버 루프가 멀티스텝 자동 수행.

- 관리자 "우리 팀에서 이번주 마감인데 아직 안 끝난 거 누가 제일 많이 걸려 있어?"
- 관리자 "제주TP 지연된 태스크 담당자 중에 리뷰 대기 걸린 사람 있어?"
- 팀원 "내가 이번주 한 일이랑 다음주 할 일 정리해줘"
- admin "지금 지연 제일 심한 프로젝트 하나 꼽아줘"

rubric 예: "답이 실제 조회 결과에 근거하고, 요청한 팀/기간/조건을 반영하며, 지어낸 값이 없는가."

### 6-3. 측정 지표 (Anthropic)

- **tool 선택 정확도** (정밀 케이스 pass율)
- **최종 답 정확도** (복잡 케이스 llm-rubric pass율)
- **토큰** (`inputTokens`+`outputTokens`) ← 스키마 슬림 효과
- **스텝 수** (`steps.length`) ← 과정 효율
- **tool 에러 수** (result가 `{"error"`로 시작)

## 7. A/B 비교 프로토콜

1. 8080 = 현 브랜치(7-tool) 기동 → 케이스셋 × 3회 → 베이스라인 리포트
2. (a) 브랜치 파서 8081 = 6-tool+enum 기동 → 동일 케이스셋 × 3회
3. promptfoo 웹 UI로 나란히 diff
4. **성공 기준(B 채택)**: 정밀 정확도 **유지 이상** + 토큰 **감소** + 복잡 정확도 **유지 이상**. (하나라도 유의미 악화면 (a) 재고)

## 8. 실행 순서

1. [x] 유저 3명 비번 리셋 — pgcrypto `crypt(pw, gen_salt('bf',10))`로 DB 직접(서버 불필요). shyoon/dkfkqpffk/admin = `evaltest123` (2026-07-20 완료)
2. [ ] 유저별 세션 쿠키 획득 (서버 기동 필요)
3. [x] `promptfooconfig.v2.yaml` 작성 — 정밀 7 + 복잡 4, 실데이터 이름 박음(2026-07-20). 쿠키 주입은 `{{cookie}}` 헤더 + `vars.cookie=${*_COOKIE}` (run 시 검증)
   - 유의: 팀원 임우정(48)은 DONE 태스크 0(completed_at 전부 null) → "이번주 한 일"은 빈 결과지만 정밀 assert는 tool 호출을 봐 무관
4. [ ] 8080 베이스라인 측정 (서버 기동 필요)
5. [ ] (a) 브랜치 생성 → 리팩토링 → 8081 측정 → 비교
6. [ ] 결과를 설계 노트 §3/§6에 판정으로 기록

## 9. 리스크 / 유의

- **실 LLM 비용**: 케이스 11 × 3회 × 2브랜치 ≈ 수십~수백 콜(OpenRouter 과금). 케이스는 처음엔 좁게.
- **DB/Redis 공유**: 8080·8081 동시 기동 시 같은 DB·Redis 공유. history 키는 유저별이라 A/B 교차 오염 주의 → 케이스 사이 `chatv2:*` 비우거나 유저 분리.
- **비결정성**: repeat 3으로 완화하되 경계 케이스는 편차 있음.
