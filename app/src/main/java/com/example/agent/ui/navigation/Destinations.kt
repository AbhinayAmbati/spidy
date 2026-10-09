package com.example.agent.ui.navigation

object Destinations {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val PROVIDER = "provider"
    const val ONBOARDING = "onboarding"
    const val MEMORY = "memory"
    const val ROUTINES = "routines"
    const val TERMS = "terms"

    /** Task detail takes the task id as a path argument. */
    const val TASK_DETAIL = "task_detail"
    const val TASK_DETAIL_ARG = "taskId"
    const val TASK_DETAIL_ROUTE = "task_detail/{taskId}"
    fun taskDetail(id: String) = "task_detail/$id"

    const val HISTORY = "history"
}
