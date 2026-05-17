package com.tracediary.app.domain.model

enum class SyncState {
    LocalOnly,
    Pending,
    Synced,
    Failed,
    Conflict,
}
