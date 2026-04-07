package com.pluxity.weekly.core.response

import com.pluxity.weekly.core.constant.SuccessCode

class DataResponseBody<T>(
    val data: T?,
) : ResponseBody(SuccessCode.SUCCESS.getHttpStatus().value(), SuccessCode.SUCCESS.getMessage())
