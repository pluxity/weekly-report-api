package com.pluxity.weekly.report.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
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
        }
    })
