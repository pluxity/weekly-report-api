package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatResolveRequest
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.exception.ChatResolveInvalidException
import com.pluxity.weekly.chat.exception.ChatSessionExpiredException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import tools.jackson.databind.json.JsonMapper

class ChatResolveServiceTest :
    BehaviorSpec({

        val clarifyStore: ClarifyStore = mockk()
        val chatActionRouter: ChatActionRouter = mockk()
        val chatHistoryStore: ChatHistoryStore = mockk(relaxed = true)
        val authorizationService: AuthorizationService = mockk()
        val objectMapper = JsonMapper()

        val service =
            ChatResolveService(
                clarifyStore,
                chatActionRouter,
                chatHistoryStore,
                authorizationService,
                objectMapper,
            )

        val userId = 42L
        val user = mockk<User> { every { requiredId } returns userId }
        every { authorizationService.currentUser() } returns user

        Given("정상 resolve - 단일 id 필드") {
            val clarifyId = "clarify-xyz"
            val stored =
                LlmAction(
                    action = "update",
                    target = "task",
                    status = "IN_PROGRESS",
                    missingFields = listOf("id"),
                    candidates = listOf(10L, 20L),
                    message = "어떤 태스크?",
                )
            val request = ChatResolveRequest(clarifyId = clarifyId, field = "id", values = listOf(10L))
            val routerResponse = ChatActionResponse(action = "update", target = "task", id = 10L)

            every { clarifyStore.peek(userId, clarifyId) } returns stored
            every { chatActionRouter.route(any()) } returns routerResponse
            every { clarifyStore.delete(userId, clarifyId) } returns Unit

            When("resolve 가 호출되면") {
                val response = service.resolve(request)

                Then("router 응답이 그대로 반환된다") {
                    response shouldBe routerResponse
                }

                Then("merged action 에 id 가 주입되고 clarify 메타(missing_fields/candidates/message)가 제거된다") {
                    verify {
                        chatActionRouter.route(
                            match {
                                it.id == 10L &&
                                    it.status == "IN_PROGRESS" &&
                                    it.missingFields == null &&
                                    it.candidates == null &&
                                    it.message == null
                            },
                        )
                    }
                }

                Then("세션이 삭제되고 히스토리가 기록된다") {
                    verify { clarifyStore.delete(userId, clarifyId) }
                    verify {
                        chatHistoryStore.recordResolvedTurn(
                            userId = userId.toString(),
                            target = "task",
                            action = "update",
                            response = routerResponse,
                        )
                    }
                }
            }
        }

        Given("정상 resolve - 리스트 필드(user_ids)") {
            val clarifyId = "clarify-abc"
            val stored =
                LlmAction(
                    action = "assign",
                    target = "epic",
                    id = 3L,
                    missingFields = listOf("user_ids"),
                    candidates = listOf(1L, 2L, 3L),
                )
            val request =
                ChatResolveRequest(clarifyId = clarifyId, field = "user_ids", values = listOf(1L, 2L))
            val routerResponse = ChatActionResponse(action = "assign", target = "epic", id = 3L)

            every { clarifyStore.peek(userId, clarifyId) } returns stored
            every { chatActionRouter.route(any()) } returns routerResponse
            every { clarifyStore.delete(userId, clarifyId) } returns Unit

            When("복수 값으로 resolve 가 호출되면") {
                service.resolve(request)

                Then("merged action 의 userIds 에 리스트가 그대로 들어간다") {
                    verify {
                        chatActionRouter.route(
                            match { it.userIds == listOf(1L, 2L) && it.missingFields == null },
                        )
                    }
                }
            }
        }

        Given("세션 invariant 파손 - missingFields 가 null") {
            val clarifyId = "clarify-1"
            val stored = LlmAction(action = "update", target = "task", missingFields = null)
            val request = ChatResolveRequest(clarifyId = clarifyId, field = "id", values = listOf(10L))

            every { clarifyStore.peek(userId, clarifyId) } returns stored

            When("resolve 가 호출되면") {
                Then("ChatSessionExpiredException 이 발생하고 세션은 삭제되지 않는다") {
                    shouldThrow<ChatSessionExpiredException> { service.resolve(request) }
                    verify(exactly = 0) { clarifyStore.delete(any(), any()) }
                    verify(exactly = 0) { chatActionRouter.route(any()) }
                }
            }
        }

        Given("세션 invariant 파손 - missingFields 가 복수") {
            val clarifyId = "clarify-2"
            val stored =
                LlmAction(
                    action = "assign",
                    target = "epic",
                    missingFields = listOf("id", "user_ids"),
                )
            val request = ChatResolveRequest(clarifyId = clarifyId, field = "id", values = listOf(1L))

            every { clarifyStore.peek(userId, clarifyId) } returns stored

            When("resolve 가 호출되면") {
                Then("ChatSessionExpiredException 이 발생한다") {
                    shouldThrow<ChatSessionExpiredException> { service.resolve(request) }
                    verify(exactly = 0) { clarifyStore.delete(any(), any()) }
                }
            }
        }

        Given("field 불일치") {
            val clarifyId = "clarify-3"
            val stored =
                LlmAction(
                    action = "update",
                    target = "task",
                    missingFields = listOf("id"),
                    candidates = listOf(10L),
                )
            val request =
                ChatResolveRequest(clarifyId = clarifyId, field = "user_ids", values = listOf(10L))

            every { clarifyStore.peek(userId, clarifyId) } returns stored

            When("clarify 대상이 아닌 필드로 resolve 하면") {
                Then("IllegalArgumentException 이 발생하고 세션은 유지된다") {
                    shouldThrow<ChatResolveInvalidException> { service.resolve(request) }
                    verify(exactly = 0) { clarifyStore.delete(any(), any()) }
                    verify(exactly = 0) { chatActionRouter.route(any()) }
                }
            }
        }

        Given("values 누락") {
            val clarifyId = "clarify-4"
            val stored =
                LlmAction(
                    action = "update",
                    target = "task",
                    missingFields = listOf("id"),
                    candidates = listOf(10L),
                )
            every { clarifyStore.peek(userId, clarifyId) } returns stored

            When("values 가 null 이면") {
                val request = ChatResolveRequest(clarifyId = clarifyId, field = "id", values = null)

                Then("IllegalArgumentException 이 발생한다") {
                    shouldThrow<ChatResolveInvalidException> { service.resolve(request) }
                    verify(exactly = 0) { clarifyStore.delete(any(), any()) }
                }
            }

            When("values 가 빈 리스트이면") {
                val request = ChatResolveRequest(clarifyId = clarifyId, field = "id", values = emptyList())

                Then("IllegalArgumentException 이 발생한다") {
                    shouldThrow<ChatResolveInvalidException> { service.resolve(request) }
                }
            }
        }

        Given("cardinality 위반 - 단일 필드에 복수 값") {
            val clarifyId = "clarify-5"
            val stored =
                LlmAction(
                    action = "update",
                    target = "task",
                    missingFields = listOf("id"),
                    candidates = listOf(1L, 2L, 3L),
                )
            val request =
                ChatResolveRequest(clarifyId = clarifyId, field = "id", values = listOf(1L, 2L))

            every { clarifyStore.peek(userId, clarifyId) } returns stored

            When("단일 필드에 2개 값을 보내면") {
                Then("IllegalArgumentException 이 발생한다") {
                    shouldThrow<ChatResolveInvalidException> { service.resolve(request) }
                    verify(exactly = 0) { clarifyStore.delete(any(), any()) }
                }
            }
        }

        Given("candidates 불일치") {
            val clarifyId = "clarify-6"
            val stored =
                LlmAction(
                    action = "update",
                    target = "task",
                    missingFields = listOf("id"),
                    candidates = listOf(10L, 20L),
                )
            val request =
                ChatResolveRequest(clarifyId = clarifyId, field = "id", values = listOf(99L))

            every { clarifyStore.peek(userId, clarifyId) } returns stored

            When("후보 목록 밖 값을 보내면") {
                Then("IllegalArgumentException 이 발생하고 세션은 유지된다") {
                    shouldThrow<ChatResolveInvalidException> { service.resolve(request) }
                    verify(exactly = 0) { clarifyStore.delete(any(), any()) }
                    verify(exactly = 0) { chatActionRouter.route(any()) }
                }
            }
        }

        Given("candidates 가 비어있는 경우") {
            val clarifyId = "clarify-7"
            val stored =
                LlmAction(
                    action = "update",
                    target = "task",
                    missingFields = listOf("id"),
                    candidates = null,
                )
            val request =
                ChatResolveRequest(clarifyId = clarifyId, field = "id", values = listOf(999L))
            val routerResponse = ChatActionResponse(action = "update", target = "task", id = 999L)

            every { clarifyStore.peek(userId, clarifyId) } returns stored
            every { chatActionRouter.route(any()) } returns routerResponse
            every { clarifyStore.delete(userId, clarifyId) } returns Unit

            When("resolve 가 호출되면") {
                service.resolve(request)

                Then("후보 검증을 건너뛰고 정상 처리된다") {
                    verify { chatActionRouter.route(any()) }
                    verify { clarifyStore.delete(userId, clarifyId) }
                }
            }
        }
    })
