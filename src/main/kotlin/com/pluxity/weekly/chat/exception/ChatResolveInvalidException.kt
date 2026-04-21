package com.pluxity.weekly.chat.exception

import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException

class ChatResolveInvalidException(
    message: String,
) : CustomException(ErrorCode.CHAT_RESOLVE_INVALID, message)
