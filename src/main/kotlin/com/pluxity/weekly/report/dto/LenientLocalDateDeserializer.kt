package com.pluxity.weekly.report.dto

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * LLM이 추출한 날짜가 불완전/비표준(예: "2027-04-XX", "2026-05")이어도
 * 보고 전체 파싱이 깨지지 않도록, 완전한 ISO `yyyy-MM-dd`만 LocalDate로 파싱하고 그 외는 null.
 * 불완전 날짜의 원본 정보는 항목 text에 그대로 남는다.
 */
class LenientLocalDateDeserializer : ValueDeserializer<LocalDate?>() {
    override fun deserialize(
        
        p: JsonParser,
        ctxt: DeserializationContext,
    ): LocalDate? {
        val text = p.valueAsString?.trim()
        if (text.isNullOrEmpty()) return null
        return try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
