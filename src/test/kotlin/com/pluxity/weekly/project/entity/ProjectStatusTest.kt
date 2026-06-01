package com.pluxity.weekly.project.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain

class ProjectStatusTest :
    BehaviorSpec({
        Given("ProjectStatus.initStates") {
            When("신규 생성 시 선택 가능한 상태 목록을 조회하면") {
                Then("TODO 와 IN_PROGRESS 만 포함된다") {
                    ProjectStatus.initStates shouldContainExactlyInAnyOrder
                        listOf(ProjectStatus.TODO, ProjectStatus.IN_PROGRESS)
                }
                Then("DONE 으로는 신규 생성할 수 없다") {
                    ProjectStatus.initStates shouldNotContain ProjectStatus.DONE
                }
            }
        }

        Given("ProjectStatus.transitionStates") {
            When("관리자가 직접 변경 가능한 상태 목록을 조회하면") {
                Then("TODO / IN_PROGRESS / DONE 모두 포함된다") {
                    ProjectStatus.transitionStates shouldContainExactlyInAnyOrder
                        listOf(ProjectStatus.TODO, ProjectStatus.IN_PROGRESS, ProjectStatus.DONE)
                }
            }
        }
    })
