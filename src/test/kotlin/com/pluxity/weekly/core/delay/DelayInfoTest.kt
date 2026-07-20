package com.pluxity.weekly.core.delay

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class DelayInfoTest :
    BehaviorSpec({

        val due = LocalDate.of(2026, 6, 30)
        val today = LocalDate.of(2026, 7, 6)

        Given("마감일이 없으면") {
            When("완료 여부와 무관하게") {
                val done = DelayInfo.of(dueDate = null, completedAt = LocalDate.of(2026, 6, 28), today = today)
                val notDone = DelayInfo.of(dueDate = null, completedAt = null, today = today)
                Then("지연일은 null, 지연 아님") {
                    done.delayed shouldBe false
                    done.delayDays shouldBe null
                    notDone.delayDays shouldBe null
                }
            }
        }

        Given("완료된 건 (completedAt 존재)") {
            When("마감보다 일찍 완료하면") {
                val info = DelayInfo.of(due, completedAt = LocalDate.of(2026, 6, 28), today = today)
                Then("음수 지연일, 지연 아님") {
                    info.delayDays shouldBe -2
                    info.delayed shouldBe false
                }
            }
            When("마감일에 정확히 완료하면") {
                val info = DelayInfo.of(due, completedAt = due, today = today)
                Then("0, 지연 아님") {
                    info.delayDays shouldBe 0
                    info.delayed shouldBe false
                }
            }
            When("마감보다 늦게 완료하면") {
                val info = DelayInfo.of(due, completedAt = LocalDate.of(2026, 7, 3), today = today)
                Then("양수 지연일, 지연") {
                    info.delayDays shouldBe 3
                    info.delayed shouldBe true
                }
            }
        }

        Given("미완료 건 (completedAt 없음)") {
            When("마감을 넘겼으면") {
                val info = DelayInfo.of(due, completedAt = null, today = LocalDate.of(2026, 7, 2))
                Then("오늘 기준 지연일, 지연") {
                    info.delayDays shouldBe 2
                    info.delayed shouldBe true
                    info.completedAt shouldBe null
                }
            }
            When("아직 마감 이내면") {
                val info = DelayInfo.of(due, completedAt = null, today = LocalDate.of(2026, 6, 29))
                Then("지연일 null, 지연 아님") {
                    info.delayDays shouldBe null
                    info.delayed shouldBe false
                }
            }
            When("오늘이 마감일 당일이면") {
                val info = DelayInfo.of(due, completedAt = null, today = due)
                Then("아직 지연 아님 (null)") {
                    info.delayDays shouldBe null
                    info.delayed shouldBe false
                }
            }
        }
    })
