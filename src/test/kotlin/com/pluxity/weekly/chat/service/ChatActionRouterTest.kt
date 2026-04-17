package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.Candidate
import com.pluxity.weekly.chat.dto.ChatReadResponse
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.dto.SelectField
import com.pluxity.weekly.chat.dto.TaskChatDto
import com.pluxity.weekly.chat.exception.ChatClarifyException
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.service.TaskService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ChatActionRouterTest :
    BehaviorSpec({

        val chatReadHandler: ChatReadHandler = mockk()
        val chatExecutor: ChatExecutor = mockk()
        val chatDtoMapper: ChatDtoMapper = mockk()
        val selectFieldResolver: SelectFieldResolver = mockk()
        val taskService: TaskService = mockk()
        val epicService: EpicService = mockk()
        val projectService: ProjectService = mockk()

        val router =
            ChatActionRouter(
                chatReadHandler,
                chatExecutor,
                chatDtoMapper,
                selectFieldResolver,
                taskService,
                epicService,
                projectService,
            )

        Given("read 액션") {
            When("태스크 조회 요청이 들어오면") {
                val action = LlmAction(action = "read", target = "task")
                val readResult = ChatReadResponse(tasks = emptyList())
                every { chatReadHandler.handle(action) } returns readResult

                val response = router.route(action)

                Then("readResult 를 담은 응답이 반환된다") {
                    response.action shouldBe "read"
                    response.target shouldBe "task"
                    response.readResult shouldBe readResult
                    response.dto.shouldBeNull()
                    response.selectFields.shouldBeNull()
                }
            }
        }

        Given("clarify 액션") {
            When("clarify 가 라우팅되면") {
                val action = LlmAction(action = "clarify", message = "좀 더 구체적으로 말씀해주세요.")

                Then("ChatClarifyException 이 발생한다") {
                    val ex = shouldThrow<ChatClarifyException> { router.route(action) }
                    ex.message shouldBe "좀 더 구체적으로 말씀해주세요."
                }
            }
        }

        Given("team 대상") {
            When("read 가 아닌 액션이 들어오면") {
                val action = LlmAction(action = "create", target = "team")

                Then("웹페이지 이용 안내 clarify 가 발생한다") {
                    val ex = shouldThrow<ChatClarifyException> { router.route(action) }
                    ex.message shouldBe "팀 관리는 웹페이지에서 이용해주세요."
                }
            }
        }

        Given("create 액션") {
            When("missingFields 없이 태스크 생성 요청이 들어오면") {
                val action = LlmAction(action = "create", target = "task", name = "새 태스크")
                val dto = TaskChatDto("새 태스크", null, null, null, null, null, null, null)
                every { chatDtoMapper.toDto(action) } returns dto
                every { selectFieldResolver.resolve(action) } returns emptyList()

                val response = router.route(action)

                Then("form 이 반환된다 (selectFields 없음)") {
                    response.action shouldBe "create"
                    response.target shouldBe "task"
                    response.dto shouldBe dto
                    response.selectFields.shouldBeNull()
                    response.id.shouldBeNull()
                }
            }

            When("missingFields 가 있어 selectFields 가 채워지면") {
                val action = LlmAction(action = "create", target = "task", missingFields = listOf("epic_id"))
                val dto = TaskChatDto("null", null, null, null, null, null, null, null)
                val selectFields =
                    listOf(SelectField("epicId", listOf(Candidate("1", "에픽A"))))
                every { chatDtoMapper.toDto(action) } returns dto
                every { selectFieldResolver.resolve(action) } returns selectFields

                val response = router.route(action)

                Then("form 과 selectFields 가 함께 반환된다") {
                    response.action shouldBe "create"
                    response.dto shouldBe dto
                    response.selectFields shouldBe selectFields
                }
            }
        }

        Given("update 액션") {
            When("id 가 있으면") {
                val action = LlmAction(action = "update", target = "task", id = 10L, name = "수정됨")
                val existing = TaskChatDto("기존", 1L, "설명", "TODO", 0, null, null, null)
                val changes = TaskChatDto("수정됨", null, null, null, null, null, null, null)
                val merged = TaskChatDto("수정됨", 1L, "설명", "TODO", 0, null, null, null)
                val taskResponse = mockk<com.pluxity.weekly.task.dto.TaskResponse>()
                every { taskService.findById(10L) } returns taskResponse
                every { chatDtoMapper.fromTaskResponse(taskResponse) } returns existing
                every { chatDtoMapper.toDto(action) } returns changes
                every { chatDtoMapper.merge(existing, changes) } returns merged
                every { selectFieldResolver.resolve(action) } returns emptyList()

                val response = router.route(action)

                Then("merged form 이 id 와 함께 반환된다") {
                    response.action shouldBe "update"
                    response.id shouldBe 10L
                    response.dto shouldBe merged
                    response.selectFields.shouldBeNull()
                }
            }

            When("id 가 없으면") {
                val action =
                    LlmAction(
                        action = "update",
                        target = "task",
                        id = null,
                        message = "어느 태스크를 수정할까요?",
                        candidates = listOf(1L, 2L),
                        missingFields = listOf("id"),
                    )
                every {
                    selectFieldResolver.resolveCandidateNames("id", "task", listOf(1L, 2L))
                } returns listOf("태스크A", "태스크B")

                Then("candidates 이름을 담은 clarify 가 발생한다") {
                    val ex = shouldThrow<ChatClarifyException> { router.route(action) }
                    ex.message shouldBe "어느 태스크를 수정할까요?"
                    ex.candidates shouldBe listOf("태스크A", "태스크B")
                }
            }
        }

        Given("delete 액션") {
            When("id 있고 missingFields 가 비어있으면") {
                val action = LlmAction(action = "delete", target = "task", id = 5L)
                every { chatExecutor.execute(action) } returns 5L

                val response = router.route(action)

                Then("executor 가 호출되고 id 가 반환된다") {
                    response.action shouldBe "delete"
                    response.id shouldBe 5L
                    response.dto.shouldBeNull()
                    response.selectFields.shouldBeNull()
                    verify { chatExecutor.execute(action) }
                }
            }

            When("id 가 없으면") {
                val action = LlmAction(action = "delete", target = "task", id = null, message = "대상을 특정할 수 없습니다.")

                Then("clarify 가 발생한다") {
                    shouldThrow<ChatClarifyException> { router.route(action) }
                }
            }

            When("id 는 있지만 missingFields 가 남아있으면") {
                val action =
                    LlmAction(
                        action = "delete",
                        target = "task",
                        id = 5L,
                        missingFields = listOf("id"),
                        message = "대상을 확인해주세요.",
                    )

                Then("회귀 방지 clarify 가 발생한다") {
                    shouldThrow<ChatClarifyException> { router.route(action) }
                    verify(exactly = 0) { chatExecutor.execute(any()) }
                }
            }
        }

        Given("assign 액션") {
            When("id 와 userIds 가 모두 있으면") {
                val action = LlmAction(action = "assign", target = "epic", id = 3L, userIds = listOf(1L, 2L))
                every { chatExecutor.execute(action) } returns 3L

                val response = router.route(action)

                Then("executor 가 호출된다") {
                    response.id shouldBe 3L
                    verify { chatExecutor.execute(action) }
                }
            }

            When("missingFields 가 남아있어 userIds 가 누락된 상태이면") {
                val action =
                    LlmAction(
                        action = "assign",
                        target = "epic",
                        id = 3L,
                        missingFields = listOf("user_ids"),
                        message = "누구를 배정할까요?",
                    )

                Then("silent no-op 을 방지하는 clarify 가 발생한다") {
                    shouldThrow<ChatClarifyException> { router.route(action) }
                    verify(exactly = 0) { chatExecutor.execute(any()) }
                }
            }
        }

        Given("review_request 액션") {
            When("id 가 있으면") {
                val action = LlmAction(action = "review_request", target = "task", id = 7L)
                every { chatExecutor.execute(action) } returns 7L

                val response = router.route(action)

                Then("executor 가 호출된다") {
                    response.action shouldBe "review_request"
                    response.id shouldBe 7L
                }
            }
        }
    })
