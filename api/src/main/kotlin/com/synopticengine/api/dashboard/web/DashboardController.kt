package com.synopticengine.api.dashboard.web

import com.synopticengine.api.dashboard.service.DashboardService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping($$"${api.base-path}/dashboard")
class DashboardController(
    private val dashboardService: DashboardService,
) {
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun getDashboard(): ResponseEntity<DashboardResponse> = ResponseEntity.ok(dashboardService.buildSummary())
}
