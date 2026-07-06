package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatActionResponse
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import tools.jackson.databind.json.JsonMapper

class ChatHistoryStoreTest :
    BehaviorSpec({

        val listOps: ListOperations<String, String> = mockk(relaxed = true)
        val valueOps: ValueOperations<String, String> = mockk(relaxed = true)
        val redisTemplate: StringRedisTemplate =
            mockk(relaxed = true) {
                every { opsForList() } returns listOps
                every { opsForValue() } returns valueOps
            }
        val store = ChatHistoryStore(redisTemplate, JsonMapper())

        Given("recordFailedTurn") {
            When("clarify 로 끝난 턴을 기록하면") {
                every { valueOps.increment("chat:turn:1") } returns 3L

                store.recordFailedTurn("1", "용산구청 유지보수 완료", "clarify('태스크를 찾을 수 없습니다')")

                Then("질문과 실패 결과가 담긴 히스토리 라인이 저장된다") {
                    verify {
                        listOps.rightPush(
                            "chat:history:1",
                            match {
                                it.contains("히스토리 #3") &&
                                    it.contains("질문: 용산구청 유지보수 완료") &&
                                    it.contains("결과: clarify('태스크를 찾을 수 없습니다')")
                            },
                        )
                    }
                }
            }
        }

        Given("recordChatTurn 의 answer 요약") {
            When("answer 응답 턴을 기록하면") {
                every { valueOps.increment("chat:turn:2") } returns 1L
                val response =
                    ChatActionResponse(
                        action = "answer",
                        target = "general",
                        message = "태스크는 업무 그룹 아래의 개별 작업 단위예요. 진행률과 상태를 관리할 수 있고, '내 태스크 보여줘'로 조회할 수 있어요.",
                    )

                store.recordChatTurn("2", "태스크가뭔데", "general", "answer", listOf(response))

                Then("답변 앞 50자가 요약으로 저장된다") {
                    verify {
                        listOps.rightPush(
                            "chat:history:2",
                            match {
                                it.contains("answer('태스크는 업무 그룹") && !it.contains("조회할 수 있어요")
                            },
                        )
                    }
                }
            }
        }
    })
