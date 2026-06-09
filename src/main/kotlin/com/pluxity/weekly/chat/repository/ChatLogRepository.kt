package com.pluxity.weekly.chat.repository

import com.pluxity.weekly.chat.entity.ChatLog
import org.springframework.data.jpa.repository.JpaRepository

interface ChatLogRepository : JpaRepository<ChatLog, Long>
