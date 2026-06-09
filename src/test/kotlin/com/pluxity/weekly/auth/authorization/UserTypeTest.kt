package com.pluxity.weekly.auth.authorization

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class UserTypeTest :
    BehaviorSpec({

        Given("대표 역할(effectiveRole) 선정") {
            When("역할이 하나만 있으면") {
                Then("그 역할명이 그대로 반환된다") {
                    UserType.effectiveRoleName(setOf("PM")) shouldBe "PM"
                    UserType.effectiveRoleName(setOf("LEADER")) shouldBe "LEADER"
                }
            }

            When("역할이 여러 개 있으면") {
                Then("우선순위(ADMIN > PO > PM > LEADER)가 가장 높은 역할이 선택된다") {
                    UserType.effectiveRoleName(setOf("PM", "PO")) shouldBe "PO"
                    UserType.effectiveRoleName(setOf("LEADER", "PM")) shouldBe "PM"
                    UserType.effectiveRoleName(setOf("LEADER", "ADMIN", "PM")) shouldBe "ADMIN"
                }
            }

            When("역할 집합이 비어 있으면") {
                Then("기본값 WORKER 가 반환된다") {
                    UserType.effectiveRoleName(emptySet()) shouldBe "WORKER"
                }
            }

            When("enum 에 매칭되지 않는 역할만 있으면") {
                Then("기본값 WORKER 가 반환된다") {
                    UserType.effectiveRoleName(setOf("WORKER")) shouldBe "WORKER"
                    UserType.effectiveRoleName(setOf("CEO")) shouldBe "WORKER"
                }
            }

            When("매칭되는 역할과 알 수 없는 역할이 섞여 있으면") {
                Then("매칭되는 것 중 우선순위가 높은 역할이 선택된다") {
                    UserType.effectiveRoleName(setOf("CEO", "PM")) shouldBe "PM"
                }
            }
        }
    })
