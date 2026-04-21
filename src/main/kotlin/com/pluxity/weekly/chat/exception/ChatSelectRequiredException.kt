package com.pluxity.weekly.chat.exception

import com.pluxity.weekly.chat.dto.Candidate
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException

class ChatSelectRequiredException(
    message: String,
    val clarifyId: String,
    val field: String,
    val candidates: List<Candidate>,
) : CustomException(ErrorCode.CHAT_SELECT_REQUIRED, message)
