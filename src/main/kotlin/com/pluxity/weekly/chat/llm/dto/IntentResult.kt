package com.pluxity.weekly.chat.llm.dto

data class IntentResult(
    val actions: List<String>,
    val target: String,
    val id: Long? = null,
    val project: String? = null,
    val epic: String? = null,
    val name: String? = null,
)
