package com.synopticengine.api.crm.activity.domain

enum class ActivityType {
    CALL,
    MEETING,
    LUNCH,
    NOTE,
    FILE,
    // Below are kept for backwards compatibility; the UI no longer offers them
    // for new activities but existing rows referencing them keep working.
    TASK,
    EMAIL,
    MESSAGE,
}
