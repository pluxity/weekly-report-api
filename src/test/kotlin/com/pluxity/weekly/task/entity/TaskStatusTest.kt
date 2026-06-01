package com.pluxity.weekly.task.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain

class TaskStatusTest :
    BehaviorSpec({
        Given("TaskStatus.initStates") {
            When("신규 생성 시 선택 가능한 상태 목록을 조회하면") {
                Then("TODO 와 IN_PROGRESS 만 포함된다") {
                    TaskStatus.initStates shouldContainExactlyInAnyOrder
                        listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS)
                }
            }
        }

        Given("TaskStatus.transitionStates") {
            When("관리자가 직접 변경 가능한 상태 목록을 조회하면") {
                Then("TODO 와 IN_PROGRESS 만 포함된다 (DONE / IN_REVIEW 는 리뷰 흐름 전용)") {
                    TaskStatus.transitionStates shouldContainExactlyInAnyOrder
                        listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS)
                }
                Then("DONE 은 직접 변경 불가") {
                    TaskStatus.transitionStates shouldNotContain TaskStatus.DONE
                }
                Then("IN_REVIEW 도 직접 변경 불가") {
                    TaskStatus.transitionStates shouldNotContain TaskStatus.IN_REVIEW
                }
            }
        }
    })
