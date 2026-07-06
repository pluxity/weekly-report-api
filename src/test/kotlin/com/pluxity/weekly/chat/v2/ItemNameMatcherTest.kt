package com.pluxity.weekly.chat.v2

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ItemNameMatcherTest :
    BehaviorSpec({

        Given("토큰 기반 이름 매칭") {
            When("검색어 단어들이 이름에 순서 무관하게 포함되면") {
                Then("매칭된다") {
                    ItemNameMatcher.matches("cctv API", "CCTV 목록 API") shouldBe true
                    ItemNameMatcher.matches("목록", "CCTV 목록 API") shouldBe true
                    ItemNameMatcher.matches("api 목록 cctv", "CCTV 목록 API") shouldBe true
                }
            }

            When("대소문자·공백이 달라도") {
                Then("무시하고 매칭된다") {
                    ItemNameMatcher.matches("safers", "SAFERS") shouldBe true
                    ItemNameMatcher.matches("SAFERS 관제", "safers관제") shouldBe true
                }
            }

            When("포함되지 않는 단어가 하나라도 있으면") {
                Then("매칭되지 않는다") {
                    ItemNameMatcher.matches("cctv 결제", "CCTV 목록 API") shouldBe false
                    ItemNameMatcher.matches("세이퍼스", "SAFERS") shouldBe false // 음차 변환은 모델(프롬프트 규칙 4) 담당
                }
            }

            When("검색어가 공백뿐이면") {
                Then("매칭되지 않는다") {
                    ItemNameMatcher.matches("   ", "CCTV 목록 API") shouldBe false
                }
            }
        }
    })
