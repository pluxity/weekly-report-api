package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.chat.dto.Candidate
import com.pluxity.weekly.chat.dto.ChatReadResponse
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.dto.SelectField
import com.pluxity.weekly.chat.dto.TaskChatDto
import com.pluxity.weekly.chat.exception.ChatClarifyException
import com.pluxity.weekly.chat.exception.ChatSelectRequiredException
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
        val clarifyStore: ClarifyStore = mockk()
        val authorizationService: AuthorizationService = mockk()
        val taskService: TaskService = mockk()
        val epicService: EpicService = mockk()
        val projectService: ProjectService = mockk()

        val router =
            ChatActionRouter(
                chatReadHandler,
                chatExecutor,
                chatDtoMapper,
                selectFieldResolver,
                clarifyStore,
                authorizationService,
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

        Given("지원하지 않는 action") {
            When("enum 에 정의되지 않은 key 가 들어오면") {
                val action = LlmAction(action = "foo", target = "task")

                Then("ChatClarifyException 이 발생하고 executor 는 호출되지 않는다") {
                    val ex = shouldThrow<ChatClarifyException> { router.route(action) }
                    ex.message shouldBe "지원하지 않는 요청입니다."
                    verify(exactly = 0) { chatExecutor.execute(any()) }
                }
            }
        }

        Given("create 액션") {
            When("missingFields 없이 태스크 생성 요청이 들어오면") {
                val action = LlmAction(action = "create", target = "task", name = "새 태스크")
                val dto = TaskChatDto("새 태스크", null, null, null, null, null, null, null)
                val user = mockk<User>()
                every { authorizationService.currentUser() } returns user
                every { authorizationService.visibleEpicIds(user) } returns listOf(1L)
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
                val user = mockk<User>()
                every { authorizationService.currentUser() } returns user
                every { authorizationService.visibleEpicIds(user) } returns listOf(1L)
                every { chatDtoMapper.toDto(action) } returns dto
                every { selectFieldResolver.resolve(action) } returns selectFields

                val response = router.route(action)

                Then("form 과 selectFields 가 함께 반환된다") {
                    response.action shouldBe "create"
                    response.dto shouldBe dto
                    response.selectFields shouldBe selectFields
                }
            }

            When("할당된 에픽이 없는 사용자가 태스크 생성을 시도하면") {
                val action = LlmAction(action = "create", target = "task", name = "새 태스크")
                val user = mockk<User>()
                every { authorizationService.currentUser() } returns user
                every { authorizationService.visibleEpicIds(user) } returns emptyList()

                Then("ChatClarifyException 이 발생하고 form 이 만들어지지 않는다") {
                    val ex = shouldThrow<ChatClarifyException> { router.route(action) }
                    ex.message shouldBe "태스크를 생성할 수 있는 에픽이 없습니다. 먼저 에픽에 참여해주세요."
                    verify(exactly = 0) { chatDtoMapper.toDto(any()) }
                }
            }

            When("ADMIN 이 태스크 생성을 시도하면 (visibleEpicIds=null)") {
                val action = LlmAction(action = "create", target = "task", name = "새 태스크")
                val dto = TaskChatDto("새 태스크", null, null, null, null, null, null, null)
                val user = mockk<User>()
                every { authorizationService.currentUser() } returns user
                every { authorizationService.visibleEpicIds(user) } returns null
                every { chatDtoMapper.toDto(action) } returns dto
                every { selectFieldResolver.resolve(action) } returns emptyList()

                val response = router.route(action)

                Then("자격 검증을 통과해 form 이 반환된다") {
                    response.action shouldBe "create"
                    response.dto shouldBe dto
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

            When("id 가 없고 candidates 가 있으면") {
                val action =
                    LlmAction(
                        action = "update",
                        target = "task",
                        id = null,
                        message = "어느 태스크를 수정할까요?",
                        candidates = listOf(1L, 2L),
                        missingFields = listOf("id"),
                    )
                val resolvedCandidates =
                    listOf(Candidate("1", "태스크A"), Candidate("2", "태스크B"))
                val user = mockk<User> { every { requiredId } returns 42L }
                every { selectFieldResolver.resolveCandidates("id", action) } returns resolvedCandidates
                every { authorizationService.currentUser() } returns user
                every { clarifyStore.save(42L, action) } returns "turn-id-xyz"

                Then("ChatSelectRequiredException 이 clarifyId/field/candidates 와 함께 발생한다") {
                    val ex = shouldThrow<ChatSelectRequiredException> { router.route(action) }
                    ex.message shouldBe "어느 태스크를 수정할까요?"
                    ex.clarifyId shouldBe "turn-id-xyz"
                    ex.field shouldBe "id"
                    ex.candidates shouldBe resolvedCandidates
                    verify { clarifyStore.save(42L, action) }
                }
            }

            When("id 가 없고 candidates 가 없으면") {
                val action =
                    LlmAction(
                        action = "update",
                        target = "task",
                        id = null,
                        message = "어느 태스크를 수정할까요?",
                        missingFields = listOf("id"),
                    )
                every { selectFieldResolver.resolveCandidates("id", action) } returns emptyList()

                Then("ChatClarifyException 이 발생한다") {
                    val ex = shouldThrow<ChatClarifyException> { router.route(action) }
                    ex.message shouldBe "어느 태스크를 수정할까요?"
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
                every { selectFieldResolver.resolveCandidates("id", action) } returns emptyList()

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
                every { selectFieldResolver.resolveCandidates("id", action) } returns emptyList()

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

            When("missingFields 로 user_ids 가 지정돼 있으면") {
                val action =
                    LlmAction(
                        action = "assign",
                        target = "epic",
                        id = 3L,
                        missingFields = listOf("user_ids"),
                        message = "누구를 배정할까요?",
                    )
                val resolvedCandidates = listOf(Candidate("10", "홍길동"), Candidate("20", "김영희"))
                val normalized = action.copy(missingFields = listOf("user_ids"), candidates = listOf(10L, 20L))
                val user = mockk<User> { every { requiredId } returns 42L }
                every { selectFieldResolver.resolveCandidates("user_ids", action) } returns resolvedCandidates
                every { authorizationService.currentUser() } returns user
                every { clarifyStore.save(42L, normalized) } returns "turn-id-xyz"

                Then("user_ids 후보가 담긴 ChatSelectRequiredException 이 발생한다") {
                    val ex = shouldThrow<ChatSelectRequiredException> { router.route(action) }
                    ex.field shouldBe "user_ids"
                    ex.candidates shouldBe resolvedCandidates
                    ex.clarifyId shouldBe "turn-id-xyz"
                    verify(exactly = 0) { chatExecutor.execute(any()) }
                }
            }

            When("missingFields 없이 userIds 만 누락된 상태이면") {
                val action = LlmAction(action = "assign", target = "epic", id = 3L)
                val resolvedCandidates = listOf(Candidate("10", "홍길동"))
                val normalized = action.copy(missingFields = listOf("user_ids"), candidates = listOf(10L))
                val user = mockk<User> { every { requiredId } returns 42L }
                every { selectFieldResolver.resolveCandidates("user_ids", action) } returns resolvedCandidates
                every { authorizationService.currentUser() } returns user
                every { clarifyStore.save(42L, normalized) } returns "turn-id-xyz"

                Then("nextMissingField 가 user_ids 를 감지해 clarify 로 이어진다") {
                    val ex = shouldThrow<ChatSelectRequiredException> { router.route(action) }
                    ex.field shouldBe "user_ids"
                    verify(exactly = 0) { chatExecutor.execute(any()) }
                }
            }

            When("missingFields 없이 userIds 가 빈 리스트이면") {
                val action = LlmAction(action = "assign", target = "epic", id = 3L, userIds = emptyList())
                val resolvedCandidates = listOf(Candidate("10", "홍길동"))
                val normalized = action.copy(missingFields = listOf("user_ids"), candidates = listOf(10L))
                val user = mockk<User> { every { requiredId } returns 42L }
                every { selectFieldResolver.resolveCandidates("user_ids", action) } returns resolvedCandidates
                every { authorizationService.currentUser() } returns user
                every { clarifyStore.save(42L, normalized) } returns "turn-id-xyz"

                Then("빈 리스트도 누락으로 간주해 clarify 가 발생한다") {
                    val ex = shouldThrow<ChatSelectRequiredException> { router.route(action) }
                    ex.field shouldBe "user_ids"
                    verify(exactly = 0) { chatExecutor.execute(any()) }
                }
            }

            When("userIds 누락인데 후보도 없으면") {
                val action = LlmAction(action = "assign", target = "epic", id = 3L, message = "배정할 유저를 찾을 수 없습니다.")
                every { selectFieldResolver.resolveCandidates("user_ids", action) } returns emptyList()

                Then("ChatClarifyException 으로 폴백한다") {
                    val ex = shouldThrow<ChatClarifyException> { router.route(action) }
                    ex.message shouldBe "배정할 유저를 찾을 수 없습니다."
                    verify(exactly = 0) { chatExecutor.execute(any()) }
                }
            }
        }

        Given("unassign 액션") {
            When("id 와 removeUserIds 가 모두 있으면") {
                val action =
                    LlmAction(action = "unassign", target = "epic", id = 3L, removeUserIds = listOf(1L))
                every { chatExecutor.execute(action) } returns 3L

                val response = router.route(action)

                Then("executor 가 호출된다") {
                    response.id shouldBe 3L
                    verify { chatExecutor.execute(action) }
                }
            }

            When("missingFields 없이 removeUserIds 만 누락된 상태이면") {
                val action = LlmAction(action = "unassign", target = "epic", id = 3L)
                val resolvedCandidates = listOf(Candidate("10", "홍길동"))
                val normalized = action.copy(missingFields = listOf("remove_user_ids"), candidates = listOf(10L))
                val user = mockk<User> { every { requiredId } returns 42L }
                every { selectFieldResolver.resolveCandidates("remove_user_ids", action) } returns resolvedCandidates
                every { authorizationService.currentUser() } returns user
                every { clarifyStore.save(42L, normalized) } returns "turn-id-xyz"

                Then("nextMissingField 가 remove_user_ids 를 감지해 clarify 로 이어진다") {
                    val ex = shouldThrow<ChatSelectRequiredException> { router.route(action) }
                    ex.field shouldBe "remove_user_ids"
                    ex.candidates shouldBe resolvedCandidates
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
