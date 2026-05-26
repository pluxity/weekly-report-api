package com.pluxity.weekly.report.converter

import com.pluxity.weekly.report.dto.FormattedReport
import com.pluxity.weekly.report.dto.MatchedAgainstPrev
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * jsonb를 Hibernate 내장 Jackson(2, Kotlin 모듈 없음)으로 매핑하면 Kotlin data class 생성자를
 * 역직렬화하지 못한다. 컬럼을 text로 두고 앱의 Jackson 3 ObjectMapper(Kotlin 모듈 O)로 변환한다.
 */
@Component
@Converter
class FormattedReportConverter(
    private val objectMapper: ObjectMapper,
) : AttributeConverter<FormattedReport, String> {
    override fun convertToDatabaseColumn(attribute: FormattedReport?): String =
        objectMapper.writeValueAsString(attribute ?: FormattedReport())

    override fun convertToEntityAttribute(dbData: String?): FormattedReport =
        if (dbData.isNullOrBlank()) FormattedReport() else objectMapper.readValue(dbData, FormattedReport::class.java)
}

@Component
@Converter
class MatchedAgainstPrevConverter(
    private val objectMapper: ObjectMapper,
) : AttributeConverter<MatchedAgainstPrev?, String?> {
    override fun convertToDatabaseColumn(attribute: MatchedAgainstPrev?): String? = attribute?.let { objectMapper.writeValueAsString(it) }

    override fun convertToEntityAttribute(dbData: String?): MatchedAgainstPrev? =
        if (dbData.isNullOrBlank()) null else objectMapper.readValue(dbData, MatchedAgainstPrev::class.java)
}
