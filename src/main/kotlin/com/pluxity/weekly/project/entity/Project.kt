package com.pluxity.weekly.project.entity

import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.entity.IdentityIdEntity
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.core.validation.validateDateRange
import com.pluxity.weekly.epic.entity.Epic
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.SoftDelete
import java.time.LocalDate

@Entity
@Table(name = "projects")
@SoftDelete(columnName = "deleted")
class Project(
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "description", length = 1000)
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ProjectStatus = ProjectStatus.TODO,
    @Column(name = "start_date")
    var startDate: LocalDate? = null,
    @Column(name = "due_date")
    var dueDate: LocalDate? = null,
    @Column(name = "pm_id")
    var pmId: Long? = null,
) : IdentityIdEntity() {
    @OneToMany(mappedBy = "project", cascade = [CascadeType.REMOVE])
    val epics: MutableList<Epic> = mutableListOf()

    init {
        validateDateRange(startDate, dueDate)
    }

    /**
     * 파생 완료일. Project가 DONE(= 모든 하위 Epic 완료)이면 하위 Epic 완료일 중 가장 늦은 날,
     * 아니면 null. 별도 저장 없이 하위에서 계산한다.
     *
     * @param epicCompletedAts 하위 Epic들의 파생 완료일. 기본값은 lazy 컬렉션에서 계산하며,
     *   list 응답 등 N+1이 우려되는 곳은 배치 로딩으로 미리 계산한 값을 넘긴다.
     */
    fun derivedCompletedAt(epicCompletedAts: List<LocalDate?> = epics.map { it.derivedCompletedAt() }): LocalDate? =
        if (status == ProjectStatus.DONE) epicCompletedAts.filterNotNull().maxOrNull() else null

    fun ensureMutable() {
        if (status == ProjectStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, "update")
        }
    }

    fun changeStatus(
        newStatus: ProjectStatus,
        allEpicsDone: Boolean,
    ) {
        if (status == ProjectStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, "update")
        }
        if (newStatus == ProjectStatus.DONE && !allEpicsDone) {
            throw CustomException(ErrorCode.EPIC_NOT_ALL_DONE)
        }
        status = newStatus
    }

    fun update(
        name: String? = null,
        description: String? = null,
        startDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        pmId: Long? = null,
    ) {
        val nextStartDate = startDate ?: this.startDate
        val nextDueDate = dueDate ?: this.dueDate
        validateDateRange(nextStartDate, nextDueDate)

        name?.let { this.name = it }
        description?.let { this.description = it }
        this.startDate = nextStartDate
        this.dueDate = nextDueDate
        pmId?.let { this.pmId = it }
    }
}
