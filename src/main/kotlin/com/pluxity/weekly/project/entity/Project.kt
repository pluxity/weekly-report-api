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
        name?.let { this.name = it }
        description?.let { this.description = it }
        startDate?.let { this.startDate = it }
        dueDate?.let { this.dueDate = it }
        pmId?.let { this.pmId = it }
        validateDateRange(this.startDate, this.dueDate)
    }
}
