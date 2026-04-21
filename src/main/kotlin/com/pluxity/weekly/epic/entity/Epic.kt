package com.pluxity.weekly.epic.entity

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.entity.IdentityIdEntity
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.core.validation.validateDateRange
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.task.entity.Task
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.SoftDelete
import java.time.LocalDate

@Entity
@Table(name = "epics")
@SoftDelete(columnName = "deleted")
class Epic(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "description", length = 1000)
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: EpicStatus = EpicStatus.TODO,
    @Column(name = "start_date")
    var startDate: LocalDate? = null,
    @Column(name = "due_date")
    var dueDate: LocalDate? = null,
) : IdentityIdEntity() {
    @OneToMany(mappedBy = "epic", cascade = [CascadeType.ALL], orphanRemoval = true)
    val assignments: MutableList<EpicAssignment> = mutableListOf()

    @OneToMany(mappedBy = "epic", cascade = [CascadeType.REMOVE])
    val tasks: MutableList<Task> = mutableListOf()

    init {
        validateDateRange(startDate, dueDate)
    }

    fun assign(user: User) {
        if (assignments.none { it.user == user }) {
            assignments.add(EpicAssignment(epic = this, user = user))
        }
    }

    fun unassign(user: User) {
        assignments.removeIf { it.user == user }
    }

    fun changeStatus(
        newStatus: EpicStatus,
        allTasksDone: Boolean,
    ) {
        if (status == EpicStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, "update")
        }
        if (newStatus == EpicStatus.DONE && !allTasksDone) {
            throw CustomException(ErrorCode.TASK_NOT_ALL_DONE)
        }
        status = newStatus
    }

    fun update(
        name: String? = null,
        description: String? = null,
        startDate: LocalDate? = null,
        dueDate: LocalDate? = null,
    ) {
        if (status == EpicStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, status, "update")
        }
        name?.let { this.name = it }
        description?.let { this.description = it }
        startDate?.let { this.startDate = it }
        dueDate?.let { this.dueDate = it }
        validateDateRange(this.startDate, this.dueDate)
    }
}
