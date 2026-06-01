package com.pluxity.weekly.chat.llm.dto

data class IntentResult(
    val action: String? = null,
    val target: String? = null,
    val id: Long? = null,
    val project: String? = null,
    val epic: String? = null,
    val name: String? = null,
    val week: String? = null,
)
