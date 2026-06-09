package com.pluxity.weekly.chat.llm.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import tools.jackson.databind.json.JsonMapper

class LlmApiDtoTest :
    BehaviorSpec({

        Given("TokenUsage.plus") {
            When("두 usage 를 더하면") {
                val a = TokenUsage(promptTokens = 3988, completionTokens = 11, totalTokens = 3999)
                val b = TokenUsage(promptTokens = 4259, completionTokens = 3536, totalTokens = 7795)
                val sum = a + b

                Then("필드별로 합산된다") {
                    sum.promptTokens shouldBe 8247
                    sum.completionTokens shouldBe 3547
                    sum.totalTokens shouldBe 11794
                }
            }
        }

        Given("OpenAiChatResponse usage 역직렬화") {
            val objectMapper = JsonMapper()

            When("snake_case usage 가 포함된 응답을 파싱하면") {
                val json =
                    """
                    {
                      "choices": [{"message": {"content": "hi"}}],
                      "usage": {"prompt_tokens": 2362, "completion_tokens": 9, "total_tokens": 2371}
                    }
                    """.trimIndent()
                val response = objectMapper.readValue(json, OpenAiChatResponse::class.java)

                Then("prompt_tokens/completion_tokens/total_tokens 가 매핑된다") {
                    response.usage?.promptTokens shouldBe 2362
                    response.usage?.completionTokens shouldBe 9
                    response.usage?.totalTokens shouldBe 2371
                }
            }

            When("usage 가 없는 응답을 파싱하면") {
                val json = """{"choices": [{"message": {"content": "hi"}}]}"""
                val response = objectMapper.readValue(json, OpenAiChatResponse::class.java)

                Then("usage 는 null") {
                    response.usage shouldBe null
                }
            }
        }
    })
