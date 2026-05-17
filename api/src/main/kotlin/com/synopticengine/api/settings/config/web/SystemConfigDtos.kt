package com.synopticengine.api.settings.config.web

data class SystemConfigResponse(
    val code: String,
    val value: String?,
    val groupName: String,
    val label: String,
    val type: String,
    val isSecret: Boolean,
    val sortOrder: Int,
)

data class SystemConfigGroupResponse(
    val group: String,
    val items: List<SystemConfigResponse>,
)

data class UpdateSystemConfigRequest(
    val value: String?,
)
