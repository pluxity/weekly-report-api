package com.pluxity.weekly.report.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate

class WeekResolverTest :
    BehaviorSpec({
        // 2026-05-27(수) 기준. 이번주 월요일 = 5/25, 지난주 월요일 = 5/18
        val today = LocalDate.of(2026, 5, 27)
        val thisMonday = LocalDate.of(2026, 5, 25)
        val lastMonday = LocalDate.of(2026, 5, 18)

        Given("resolveWeekStart") {
            When("\"this\"") {
                Then("이번주 월요일") { resolveWeekStart("this", today) shouldBe thisMonday }
            }
            When("null / 빈 문자열") {
                Then("이번주 월요일") {
                    resolveWeekStart(null, today) shouldBe thisMonday
                    resolveWeekStart("", today) shouldBe thisMonday
                }
            }
            When("\"last\"") {
                Then("지난주 월요일") { resolveWeekStart("last", today) shouldBe lastMonday }
            }
            When("대소문자/공백 섞인 값") {
                Then("정규화되어 해석된다") { resolveWeekStart("  LAST ", today) shouldBe lastMonday }
            }
            When("ISO 날짜 (5/12 화)") {
                Then("그 주 월요일(5/11)로 정규화된다") {
                    resolveWeekStart("2026-05-12", today) shouldBe LocalDate.of(2026, 5, 11)
                }
            }
            When("파싱 불가한 쓰레기 값") {
                Then("이번주 월요일로 fallback (500 방지)") {
                    resolveWeekStart("pending", today) shouldBe thisMonday
                }
            }
            When("\"N주차\" (ISO 주차)") {
                Then("해당 ISO 주의 월요일로 계산된다 (28주차 → 7/6)") {
                    resolveWeekStart("28주차", today) shouldBe LocalDate.of(2026, 7, 6)
                    resolveWeekStart("29주차", today) shouldBe LocalDate.of(2026, 7, 13)
                    resolveWeekStart("1주차", today) shouldBe LocalDate.of(2025, 12, 29)
                }
                Then("공백/접두어가 섞여도 추출된다") {
                    resolveWeekStart("제28주차", today) shouldBe LocalDate.of(2026, 7, 6)
                    resolveWeekStart("28 주차", today) shouldBe LocalDate.of(2026, 7, 6)
                }
                Then("유효 범위를 벗어난 주차는 이번주로 fallback") {
                    resolveWeekStart("99주차", today) shouldBe thisMonday
                    resolveWeekStart("0주차", today) shouldBe thisMonday
                }
            }
            When("\"M월 N주차\" (A안: 1일 포함 주 = 1주차)") {
                // 2026-05-01은 금요일 → 1주차 월요일 = 4/27
                Then("월내 주차의 월요일로 계산된다 (5월 4주차 → 5/18)") {
                    resolveWeekStart("5월 4주차", today) shouldBe LocalDate.of(2026, 5, 18)
                    resolveWeekStart("5월 1주차", today) shouldBe LocalDate.of(2026, 4, 27)
                    resolveWeekStart("5월4주차", today) shouldBe LocalDate.of(2026, 5, 18)
                }
                Then("\"N주차\"(연중 ISO)로 오독되지 않는다") {
                    // "5월 4주차"가 ISO 4주차(1월)로 새면 안 됨
                    resolveWeekStart("5월 4주차", today) shouldNotBe LocalDate.of(2026, 1, 19)
                }
                Then("월내 유효 범위를 벗어나면 이번주로 fallback") {
                    resolveWeekStart("5월 9주차", today) shouldBe thisMonday
                    resolveWeekStart("13월 2주차", today) shouldBe thisMonday
                }
            }
        }
    })
