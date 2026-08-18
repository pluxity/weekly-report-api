package com.pluxity.weekly.db

import com.pluxity.weekly.test.ContainerConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 빈 DB에 마이그레이션 체인 전체가 적용되고 엔티티와 스키마가 일치하는지 확인한다.
 * 컨텍스트가 뜬다는 것 자체가 Flyway 적용 + ddl-auto=validate 통과를 의미한다.
 */
@SpringBootTest
@Import(ContainerConfig::class)
class MigrationChainTest(
    private val jdbcTemplate: JdbcTemplate,
) : BehaviorSpec({
        extension(SpringExtension)

        Given("빈 컨테이너에 애플리케이션을 기동하면") {
            When("Flyway 마이그레이션이 적용되고 나면") {
                Then("체인이 순서대로 전부 성공한다") {
                    val applied =
                        jdbcTemplate.queryForList(
                            "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                            String::class.java,
                        )
                    applied shouldContainExactly listOf("1", "20260707.001")
                }

                Then("baseline이 만든 테이블이 존재한다") {
                    val tables =
                        jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                            Int::class.java,
                        )
                    // 도메인 13개 + flyway_schema_history
                    tables shouldBe 14
                }
            }
        }
    })
