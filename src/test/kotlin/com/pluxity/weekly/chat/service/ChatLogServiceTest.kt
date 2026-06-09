package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.config.LlmProperties
import com.pluxity.weekly.chat.config.OpenRouterProperties
import com.pluxity.weekly.chat.repository.ChatLogRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class ChatLogServiceTest :
    BehaviorSpec({

        val inputPrice = BigDecimal("0.30")
        val outputPrice = BigDecimal("2.50")

        Given("ChatLogData.calculateCost") {
            When("input/output 토큰과 단가로 비용을 계산하면") {
                Then("input·output 단가를 각각 적용해 8자리 USD가 산출된다") {
                    // 실측 케이스: input 8247 / output 3547 → 0.01134160
                    ChatLogData.calculateCost(8247, 3547, inputPrice, outputPrice) shouldBe BigDecimal("0.01134160")
                }
            }

            When("토큰이 0이면") {
                Then("비용은 0") {
                    ChatLogData.calculateCost(0, 0, inputPrice, outputPrice) shouldBe BigDecimal("0.00000000")
                }
            }

            When("output만 1M 토큰이면") {
                Then("output 단가(2.50/M)만 반영된다") {
                    ChatLogData.calculateCost(0, 1_000_000, inputPrice, outputPrice) shouldBe BigDecimal("2.50000000")
                }
            }
        }

        Given("ChatLogData.toEntity") {
            When("토큰 데이터에 cost·model 을 주입해 변환하면") {
                val data =
                    ChatLogData(
                        userId = 37L,
                        requestMessage = "내 업무 보여줘",
                        success = true,
                        intentInputTokens = 2362,
                        intentOutputTokens = 9,
                        actionInputTokens = 4858,
                        actionOutputTokens = 20,
                    )
                val entity = data.toEntity(cost = BigDecimal("0.00223850"), model = "google/gemini-2.5-flash")

                Then("필드·cost·model 이 그대로 매핑된다") {
                    entity.userId shouldBe 37L
                    entity.requestMessage shouldBe "내 업무 보여줘"
                    entity.success shouldBe true
                    entity.intentInputTokens shouldBe 2362
                    entity.actionOutputTokens shouldBe 20
                    entity.cost shouldBe BigDecimal("0.00223850")
                    entity.model shouldBe "google/gemini-2.5-flash"
                }
            }
        }

        Given("ChatLogData total 토큰") {
            When("intent/action 토큰이 채워지면") {
                val data =
                    ChatLogData(
                        userId = 1L,
                        requestMessage = "msg",
                        intentInputTokens = 100,
                        intentOutputTokens = 10,
                        actionInputTokens = 200,
                        actionOutputTokens = 20,
                    )
                Then("input/output 별로 합산된다") {
                    data.totalInputTokens shouldBe 300
                    data.totalOutputTokens shouldBe 30
                }
            }
        }

        Given("ChatLogService.record") {
            val llmProperties =
                LlmProperties(
                    openrouter =
                        OpenRouterProperties(
                            model = "test-model",
                            inputPricePerMillion = inputPrice,
                            outputPricePerMillion = outputPrice,
                        ),
                )
            val data = ChatLogData(userId = 1L, requestMessage = "msg", success = true)

            When("저장이 정상이면") {
                val repository: ChatLogRepository = mockk()
                every { repository.save(any()) } answers { firstArg() }
                val service = ChatLogService(repository, llmProperties)

                service.record(data)

                Then("repository.save 가 호출된다") {
                    verify(exactly = 1) { repository.save(any()) }
                }
            }

            When("저장이 예외를 던지면") {
                val repository: ChatLogRepository = mockk()
                every { repository.save(any()) } throws RuntimeException("DB down")
                val service = ChatLogService(repository, llmProperties)

                Then("예외를 삼키고 전파하지 않는다 (chat 흐름 보호)") {
                    shouldNotThrowAny { service.record(data) }
                }
            }
        }
    })
