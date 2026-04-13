package com.pluxity.weekly.task.entity

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.core.entity.IdentityIdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "task_approval_logs")
class TaskApprovalLog(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    var task: Task,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    var actor: User,
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    var action: TaskApprovalAction,
    @Column(name = "reason", length = 1000)
    var reason: String? = null,
) : IdentityIdEntity()
