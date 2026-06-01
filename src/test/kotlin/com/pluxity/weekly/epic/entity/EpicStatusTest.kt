package com.pluxity.weekly.epic.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain

class EpicStatusTest :
    BehaviorSpec({
        Given("EpicStatus.initStates") {
            When("신규 생성 시 선택 가능한 상태 목록을 조회하면") {
                Then("TODO 와 IN_PROGRESS 만 포함된다") {
                    EpicStatus.initStates shouldContainExactlyInAnyOrder
                        listOf(EpicStatus.TODO, EpicStatus.IN_PROGRESS)
                }
                Then("DONE 으로는 신규 생성할 수 없다") {
                    EpicStatus.initStates shouldNotContain EpicStatus.DONE
                }
            }
        }

        Given("EpicStatus.transitionStates") {
            When("관리자가 직접 변경 가능한 상태 목록을 조회하면") {
                Then("TODO / IN_PROGRESS / DONE 모두 포함된다") {
                    EpicStatus.transitionStates shouldContainExactlyInAnyOrder
                        listOf(EpicStatus.TODO, EpicStatus.IN_PROGRESS, EpicStatus.DONE)
                }
            }
        }
    })
