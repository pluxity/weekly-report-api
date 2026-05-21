package com.pluxity.weekly.report.entity

import com.pluxity.weekly.core.entity.IdentityIdEntity
import com.pluxity.weekly.report.dto.FormattedReport
import com.pluxity.weekly.report.dto.MatchedAgainstPrev
import com.pluxity.weekly.team.entity.Team
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate

@Entity
@Table(
    name = "weekly_reports",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_weekly_reports_team_week", columnNames = ["team_id", "week_start"]),
    ],
)
class WeeklyReport(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    var team: Team,
    @Column(name = "team_name_raw", nullable = false, length = 255)
    var teamNameRaw: String,
    @Column(name = "week_start", nullable = false)
    var weekStart: LocalDate,
    @Column(name = "week_label", length = 100)
    var weekLabel: String?,
    @Column(name = "raw_content", columnDefinition = "TEXT", nullable = false)
    var rawContent: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formatted", columnDefinition = "jsonb", nullable = false)
    var formatted: FormattedReport = FormattedReport(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_against_prev", columnDefinition = "jsonb")
    var matchedAgainstPrev: MatchedAgainstPrev? = null,
) : IdentityIdEntity()
