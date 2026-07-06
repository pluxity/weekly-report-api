package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.ToolCall
import com.pluxity.weekly.chat.v2.dto.ToolCallFunction
import com.pluxity.weekly.chat.v2.dto.ToolChatRequest
import com.pluxity.weekly.chat.v2.dto.ToolChatResponse
import com.pluxity.weekly.chat.v2.dto.ToolMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import tools.jackson.databind.json.JsonMapper

/** OpenRouter tool calling wire 포맷(snake_case) 직렬화/역직렬화 검증 — 여기 깨지면 API 호출 자체가 실패한다. */
class ChatV2ApiDtoTest :
    BehaviorSpec({

        val objectMapper = JsonMapper()

        Given("ToolChatRequest 직렬화") {
            When("tool_calls를 포함한 assistant 메시지와 tool 결과 메시지를 직렬화하면") {
                val request =
                    ToolChatRequest(
                        model = "google/gemini-2.5-flash",
                        messages =
                            listOf(
                                ToolMessage(role = "user", content = "AA 수정해줘"),
                                ToolMessage(
                                    role = "assistant",
                                    toolCalls =
                                        listOf(
                                            ToolCall(
                                                id = "call_1",
                                                function = ToolCallFunction(name = "search_tasks", arguments = """{"name":"AA"}"""),
                                            ),
                                        ),
                                ),
                                ToolMessage(role = "tool", content = """{"count":1}""", toolCallId = "call_1"),
                            ),
                        temperature = 0.1,
                        tools = ChatV2Tools.ALL,
                    )

                val json = objectMapper.writeValueAsString(request)

                Then("snake_case 필드명으로 출력된다") {
                    json shouldContain "\"tool_calls\""
                    json shouldContain "\"tool_call_id\""
                    json shouldContain "\"search_items\""
                    json shouldContain "\"update_task\""
                    json shouldNotContain "toolCalls"
                    json shouldNotContain "toolCallId"
                }

                Then("null 필드는 출력에서 제외된다 (content 없는 assistant 메시지 등)") {
                    // assistant 메시지의 content=null이 "content":null 로 나가지 않아야 함
                    json shouldNotContain "\"content\":null"
                }
            }
        }

        Given("ToolChatResponse 역직렬화") {
            When("OpenRouter tool_calls 응답 JSON을 파싱하면") {
                val raw =
                    """
                    {
                      "id": "gen-123",
                      "choices": [{
                        "finish_reason": "tool_calls",
                        "message": {
                          "role": "assistant",
                          "content": null,
                          "tool_calls": [{
                            "id": "call_abc",
                            "type": "function",
                            "function": {"name": "search_tasks", "arguments": "{\"name\":\"AA\"}"}
                          }]
                        }
                      }],
                      "usage": {"prompt_tokens": 100, "completion_tokens": 20, "total_tokens": 120}
                    }
                    """.trimIndent()

                val response = objectMapper.readValue(raw, ToolChatResponse::class.java)

                Then("tool_calls와 usage가 매핑된다") {
                    val message = response.choices?.first()?.message!!
                    message.toolCalls?.size shouldBe 1
                    message.toolCalls?.first()?.id shouldBe "call_abc"
                    message.toolCalls
                        ?.first()
                        ?.function
                        ?.name shouldBe "search_tasks"
                    message.toolCalls
                        ?.first()
                        ?.function
                        ?.arguments shouldBe """{"name":"AA"}"""
                    response.usage?.promptTokens shouldBe 100
                    response.usage?.completionTokens shouldBe 20
                }
            }

            When("최종 자연어 응답(content, tool_calls 없음)을 파싱하면") {
                val raw =
                    """
                    {
                      "choices": [{
                        "finish_reason": "stop",
                        "message": {"role": "assistant", "content": "수정했어요."}
                      }]
                    }
                    """.trimIndent()

                val response = objectMapper.readValue(raw, ToolChatResponse::class.java)

                Then("content가 매핑되고 toolCalls는 null이다") {
                    val message = response.choices?.first()?.message!!
                    message.content shouldBe "수정했어요."
                    message.toolCalls shouldBe null
                }
            }
        }
    })
