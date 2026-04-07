package com.pluxity.weekly.chat.llm.dto

data class Message(
    val role: String,
    val content: String,
)

// Ollama
data class OllamaChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean,
    val options: OllamaOptions,
)

data class OllamaOptions(
    val temperature: Double,
)

data class OllamaChatResponse(
    val message: OllamaMessage? = null,
)

data class OllamaMessage(
    val content: String? = null,
    val role: String? = null,
)

// OpenAI 호환
data class OpenAiChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double,
)

data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>? = null,
)

data class OpenAiChoice(
    val message: OpenAiMessage? = null,
)

data class OpenAiMessage(
    val content: String? = null,
    val role: String? = null,
)

// Gemini
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String,
)

data class GeminiGenerationConfig(
    val temperature: Double,
    val responseMimeType: String = "application/json",
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
)
