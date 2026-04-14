package com.pluxity.weekly.task.entity

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.core.entity.IdentityIdEntity
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
    fun update(
        epic: Epic? = null,
        name: String? = null,
        description: String? = null,
        status: TaskStatus? = null,
        progress: Int? = null,
        startDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        assignee: User? = null,
    ) {
        epic?.let { this.epic = it }
        name?.let { this.name = it }
        description?.let { this.description = it }
        status?.let { this.status = it }
        progress?.let { this.progress = it }
        startDate?.let { this.startDate = it }
        dueDate?.let { this.dueDate = it }
        assignee?.let { this.assignee = it }
    }
}
