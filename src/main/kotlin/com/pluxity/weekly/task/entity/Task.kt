package com.pluxity.weekly.task.entity

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.entity.IdentityIdEntity
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.core.validation.validateDateRange
import com.pluxity.weekly.epic.entity.Epic
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SoftDelete
import java.time.LocalDate

@Entity
@Table(name = "tasks")
@SoftDelete(columnName = "deleted")
class Task(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "epic_id", nullable = false)
    var epic: Epic,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "description", length = 1000)
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: TaskStatus = TaskStatus.TODO,
    @Column(name = "progress", nullable = false)
    var progress: Int = 0,
    @Column(name = "start_date")
    var startDate: LocalDate? = null,
    @Column(name = "due_date")
    var dueDate: LocalDate? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    var assignee: User? = null,
) : IdentityIdEntity() {
    init {
        validateDateRange(startDate, dueDate)
    }

    fun ensureMutable() {
        if (status == TaskStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, "update")
        }
    }

    fun changeStatus(newStatus: TaskStatus) {
        if (status == TaskStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, "update")
        }
        if (status == TaskStatus.IN_REVIEW || newStatus == TaskStatus.IN_REVIEW || newStatus == TaskStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, newStatus)
        }
        status = newStatus
    }

    fun requestReview() {
        if (status != TaskStatus.TODO && status != TaskStatus.IN_PROGRESS) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, TaskApprovalAction.REVIEW_REQUEST)
        }
        status = TaskStatus.IN_REVIEW
        progress = 100
    }

    fun approve() {
        if (status != TaskStatus.IN_REVIEW) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, TaskApprovalAction.APPROVE)
        }
        status = TaskStatus.DONE
    }

    fun reject() {
        if (status != TaskStatus.IN_REVIEW) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, TaskApprovalAction.REJECT)
        }
        status = TaskStatus.IN_PROGRESS
    }

    fun update(
        name: String? = null,
        description: String? = null,
        progress: Int? = null,
        startDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        assignee: User? = null,
    ) {
        val nextStartDate = startDate ?: this.startDate
        val nextDueDate = dueDate ?: this.dueDate
        validateDateRange(nextStartDate, nextDueDate)

        name?.let { this.name = it }
        description?.let { this.description = it }
        progress?.let { this.progress = it }
        this.startDate = nextStartDate
        this.dueDate = nextDueDate
        assignee?.let { this.assignee = it }
    }
}
