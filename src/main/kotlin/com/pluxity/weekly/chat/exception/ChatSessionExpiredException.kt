package com.pluxity.weekly.chat.exception

import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException

class ChatSessionExpiredException : CustomException(ErrorCode.CHAT_SESSION_EXPIRED)
