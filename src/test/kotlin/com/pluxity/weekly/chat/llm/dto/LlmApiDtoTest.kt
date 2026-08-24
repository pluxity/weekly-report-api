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

        Given("OpenAiChatRequest 직렬화") {
            val objectMapper = JsonMapper()
            val base =
                OpenAiChatRequest(
                    model = "google/gemini-2.5-flash",
                    messages = listOf(Message(role = "user", content = "hi")),
                    temperature = 0.1,
                )

            When("response_format 없이 직렬화하면") {
                val json = objectMapper.writeValueAsString(base)

                Then("response_format/provider/reasoning 키가 빠진다 (기존 호출 바디 유지)") {
                    json.contains("response_format") shouldBe false
                    json.contains("provider") shouldBe false
                    json.contains("reasoning") shouldBe false
                }
            }

            When("response_format 을 실어 직렬화하면") {
                val request =
                    base.copy(
                        responseFormat =
                            ResponseFormat(
                                jsonSchema = JsonSchemaSpec(name = "weekly_report_classify", schema = mapOf("type" to "object")),
                            ),
                        provider = ProviderPreferences(),
                        reasoning = ReasoningConfig(),
                    )
                val json = objectMapper.writeValueAsString(request)

                Then("OpenAI 호환 snake_case 키로 나간다") {
                    json.contains(""""response_format":{"type":"json_schema"""") shouldBe true
                    json.contains(""""json_schema":{"name":"weekly_report_classify","strict":true""") shouldBe true
                    json.contains(""""provider":{"require_parameters":true}""") shouldBe true
                    json.contains(""""reasoning":{"enabled":false}""") shouldBe true
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
