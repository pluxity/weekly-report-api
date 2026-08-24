# chat-v2 Eval Spec (promptfoo HTTP provider)

> ⚠️ 로컬 실험용 스펙. 커밋 제외. 대상 데이터는 **로컬 도커(weekly-report-db)의 운영 복원본** — 프로덕션 아님. (작성: 2026-07-20)

## 1. 목적

- **주목적**: **현 코드(6-tool 확정 상태) 절대 평가.** 역할별 스코프 정확도(팀원/관리자/admin), tool 선택 정확도, 토큰/스텝 비용을 **감이 아니라 숫자로** 측정. 회귀 베이스라인 확보.
- ~~A/B(7 vs 6 tool)~~ — **폐기.** `get_item_details`가 `search_items.detail` enum으로 흡수돼(6-tool 확정, 커밋 d3a2738) 비교 대상이 사라짐. 단일 서버만 측정.
- **방법**: promptfoo **HTTP provider**로 `/chat/v2` **전체 루프**를 실서버에 돌려, 응답 `steps[]`·토큰을 측정. (LLM의 실제 tool 선택을 봐야 하므로 mock 무의미)

## 2. 비범위 (이번 스펙에서 제외)

- **멀티턴 오염**(프로젝트1→2→2상세) — promptfoo가 테스트를 병렬·임의순서로 돌려 history 순서 공유가 깨짐. **별도 수동 시나리오**로 검증(설계 노트 §6 history 항목).
- **기존 v1 promptfoo**(`promptfooconfig.yaml`) — v1 프롬프트 회귀용, **손대지 않음**. v2는 새 config 분리.
- 부하/동시성/레이턴시 SLA — 기능 정확도만.

## 3. 테스트 데이터 — 결정적 seed (prod 복원 폐기)

**prod dump 복원 + 실이름 결합을 폐기하고, repo에 딸린 결정적 seed로 전환** (2026-07-21). 근거: prod 이름 결합이 재복원·데이터 변동에 취약 + 다른 환경 재현 불가.

- **전용 eval DB**: `weekly_report_eval` (현재 복원 prod DB `weekly_report`와 분리 — seed만 들어가 결정적, aggregate 개수·지연 순위 안 흔들림).
- **seeder**: `chat/v2/eval/EvalDataSeeder.kt` — `@Profile("eval")` CommandLineRunner. 리포지토리 직접 사용, `PasswordEncoder`로 비번 인코딩, 실행마다 wipe→재삽입. 날짜는 `LocalDate.now(Asia/Seoul)` 상대값(이번주/지연/다음주 의미 유지).
- **프로파일 오버라이드**: `application-eval.yml`이 datasource를 `weekly_report_eval`로. 실행 `--spring.profiles.active=local,eval`.

**seed 페르소나 (비번 공통 `evaltest123`):**

| 페르소나 | username | 이름 | 역할 | 검증 대상 |
|---|---|---|---|---|
| **관리자** | `leader` | 이도경 | TEAM_LEADER + PM | `team_me`·`pm_me`·주간보고·복잡 (겸직) |
| **팀원** | `member` | 박서준 | 무역할 | `assignee_me`·회고·팀원 복잡 |
| **admin** | `admin` | 김관리 | ADMIN | 전체/지연 프로젝트 스코프 |

- 팀 `플랫폼개발팀`(리더=이도경, 멤버=박서준·최유나·정민호), 프로젝트 2개(PM=이도경): `싱가포르 물류 플랫폼`(최고 지연)·`CCTV 통합관제 고도화`. 태스크에 미완/DONE(completed_at)/IN_REVIEW/지연을 담당자별로 분포(이번주 마감 최다=최유나). 승인로그(반려사유 포함)·주간보고 1건 포함.
- 테스트 메시지의 이름은 이 seed에 맞춰짐: 상세=`CCTV 연동 API`, 복잡=`싱가포르 물류 플랫폼`, CUD거절=`운송장 번호 검증`, 가드레일=`존재하지않는프로젝트zzz`(없어야 함).

## 4. 인증

- `POST /auth/sign-in {username, password}` → 세션 쿠키(Set-Cookie)
- 유저별 쿠키 1회 획득 → promptfoo provider `headers.Cookie`에 주입 (유저마다 다르므로 케이스를 유저별로 분리)

## 5. 하네스 (promptfoo v2 config)

- 파일: `promptfoo/promptfooconfig.v2.yaml`
- **provider = 단일 서버**: `http://localhost:8081/chat/v2` (이 워크트리 local 프로파일 = http·8081, 6-tool). config: `method: POST`, `headers: {Content-Type, Cookie: {{cookie}}}`, `body: {message: "{{message}}"}`.
- **⚠️ 응답 래퍼 주의**: 200 body는 `DataResponseBody` = `{status, message, data: ChatV2Response}`. `ChatV2Response = {reply, steps[], inputTokens, outputTokens, cachedTokens}`, `ChatV2Step = {tool, arguments(JSON문자열), result(JSON문자열)}`. → provider `transformResponse: "json.data"`로 래퍼를 벗겨 `output = ChatV2Response`로 만든다. (기존 config가 `.steps`를 바로 봐서 틀렸던 지점 — 실행 안 해봐 미발견)
- `--repeat 3` (CLI, LLM 비결정성 → 다수결/평균)
- 유저별 쿠키는 케이스의 `vars.cookie=${*_COOKIE}`로 주입 (shyoon/dkfkqpffk/admin)

## 6. 테스트 케이스

### 6-1. 정밀 케이스 — `steps[]` 정확 assert (변화가 landing했나)

| 유저 | 메시지 | 기대 (steps assert) |
|---|---|---|
| 팀원 | "내 남은 일" | search_items: `assignee_me=true` + `exclude_done=true` |
| 팀원 | "이번주 내가 한 일" | search_items: `completed_from`/`completed_to` |
| 관리자 | "우리팀원 누구있지" | search_items: `type=team` + `team_me=true` |
| 관리자 | "우리 팀 지연된 거" | search_items: `team_me=true` + `exclude_done=true` |
| 관리자 | "내가 PM인 프로젝트" | search_items: `pm_me=true` (**aggregate엔 pm_me 없음 → search만 정답**) |
| 전원 | "OO 태스크 상세히" | search_items: `detail=detailed` (**get_item_details 나오면 실패** — 폐기 검증) |
| 관리자 | "프로젝트별 진행률 평균" | aggregate_items: `type=project` |
| 관리자 | "리뷰 대기 뭐 있어" | list_pending_reviews |
| 리더 | "우리 팀 주간보고 보여줘" | get_weekly_report |
| 팀원 | "AA 태스크 완료해줘" | reply에 "보드/폼" 안내 (CUD 거절) |
| 리더 | "주간보고 작성해줘" | 붙여넣기 입력 안내 (조회 tool 미호출) |

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

## 7. 측정 프로토콜 (단일 — A/B 폐기)

1. 8080 = 현 브랜치(6-tool) 기동 → 케이스셋 × 3회 → **베이스라인 리포트** 확보
2. 판정 = 절대 통과율:
   - **정밀(A·B군)**: steps assert pass율. 목표는 케이스별로 near-100% (실패 케이스는 프롬프트/스키마 수정 대상)
   - **복잡·가드레일(C·D군)**: llm-rubric pass율
   - **비용**: `inputTokens`/`outputTokens`/`steps.length`를 리포트로 기록 (concise 린화·detail 트리 이후 baseline)
3. 회귀 용도: 프롬프트·tool 수정 후 재측정해 이 baseline 대비 악화 없는지 확인

## 8. 실행 순서

**최초 1회 (환경 세팅):**
1. eval DB 생성 (빈 DB): `docker exec weekly-report-db psql -U pluxity -c "CREATE DATABASE weekly_report_eval OWNER pluxity;"`
2. seed 주입 = **서버를 eval 프로파일로 기동**: `.\gradlew.bat bootRun --args='--spring.profiles.active=local,eval'` → ddl-auto가 테이블 생성 + `EvalDataSeeder`가 결정적 데이터 심음(기동 로그에 개수 요약). 이후 서버는 이 DB를 봄.

**매 측정 (같은 PowerShell 창):**
3. 쿠키: `. .\promptfoo\get-cookies.ps1` (leader/member/admin, evaltest123) — 표의 cookie 칸이 `AccessToken=...`이면 OK, `{{ env... }}`면 미설정
4. eval: `npx promptfoo eval -c promptfoo/promptfooconfig.v2.yaml -j 1` (**`-j 1` 필수** — 동시성이면 유저락 429·OpenRouter 레이트리밋으로 No output)
5. baseline 리포트 확보(`npx promptfoo view`) → 실패 케이스는 프롬프트/스키마 수정 → 재측정

**주의**: seed는 서버 재기동(eval 프로파일)마다 wipe→재삽입이라 결정적. 프롬프트 바꾸면 서버 재기동해야 반영(프롬프트 `by lazy` 캐시).

체크: [x] promptfoo config 6-tool 재작성 + seed 이름 반영(2026-07-21) · [x] 결정적 seed 전환 · [ ] eval DB 생성+seed 기동 · [ ] baseline 재측정(seed 기준)

## 9. 리스크 / 유의

- **동시성 = No output**: promptfoo 기본 동시 4면 (a) 같은 유저 케이스가 겹쳐 `ChatV2UserLock` **429**, (b) 서버→OpenRouter 동시호출로 **레이트리밋 → 루프 중 500**(③ Safe Exit 갭). → **`-j 1` 필수**. (실측: -j4에서 복잡·not-found 5개 No output, -j1+쿠키로 15/16.)
- **쿠키는 셸 env** — `get-cookies.ps1` 돌린 그 창에서만 삶. 새 창이면 다시. 표 cookie 칸이 `{{ env… }}`면 미설정(→ 전부 401 No output).
- **history 오염**: 옵션 D로 턴 전체 저장이라, 같은 유저의 여러 케이스가 순차로 돌면 history가 누적돼 뒤 케이스에 영향 가능. 필요 시 실행 전 `chatv2:history:*` 비우기. (멀티턴 지칭 검증은 이 하네스 비범위)
- **실 LLM 비용**: 케이스 16(+`--repeat`) × 케이스당 여러 콜 + llm-rubric 채점 콜(OpenRouter 과금).
- **비결정성**: `--repeat 3`으로 완화하되 경계 케이스는 편차. seed는 결정적이라 데이터 변동 요인은 제거됨.
