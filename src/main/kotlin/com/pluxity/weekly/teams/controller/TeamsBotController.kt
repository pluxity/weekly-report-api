package com.pluxity.weekly.teams.controller

import com.pluxity.weekly.teams.dto.Activity
import com.pluxity.weekly.teams.service.TeamsMessageHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/teams/messages")
class TeamsBotController(
    private val messageHandler: TeamsMessageHandler,
) {
    @PostMapping
    fun receiveActivity(
        @RequestBody activity: Activity,
    ): ResponseEntity<Void> {
        log.debug { "Activity 수신 - type: ${activity.type}, from: ${activity.from?.name}" }
        messageHandler.handleActivity(activity)
        return ResponseEntity.ok().build()
    }
}
