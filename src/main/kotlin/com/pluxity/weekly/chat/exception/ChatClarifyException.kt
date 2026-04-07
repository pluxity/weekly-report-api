package com.pluxity.weekly.chat.exception

import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException

class ChatClarifyException(
    message: String,
    val candidates: List<String>? = null,
) : CustomException(ErrorCode.LLM_AMBIGUOUS_REQUEST, message)
