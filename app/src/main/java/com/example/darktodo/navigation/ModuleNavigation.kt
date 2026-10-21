package com.example.darktodo.navigation

/** A top-level module shown in the scrollable tab bar. Add future modules here only. */
data class ModuleSpec(val id: String, val label: String)

/** Where the app's `page`/`todoPage` state machine must point to show a module. */
data class ModuleRoute(val page: String, val todoPage: String = "Tasks")

sealed interface BackStep {
    /** Pop one level of drill-down inside History. */
    object PopRoute : BackStep
    data class GoTo(val route: ModuleRoute) : BackStep
    /** Already on the home screen: back should arm / perform exit. */
    object AtHome : BackStep
}

object ModuleNavigation {
    const val TASKS = "tasks"
    /** Legacy id: History is now a sub-tab of Tasks, so it is no longer in [modules]. */
    const val HISTORY = "history"
    const val CALENDAR = "calendar"
    const val VAULT = "vault"
    const val QUOTES = "quotes"
    const val INFO = "info"
    const val STICKERS = "stickers"
    const val WALLPAPERS = "wallpapers"
    const val SETTINGS = "settings"

    val modules = listOf(
        ModuleSpec(TASKS, "Tasks"),
        ModuleSpec(CALENDAR, "Calendar"),
        ModuleSpec(VAULT, "Vault"),
        ModuleSpec(INFO, "Info"),
        ModuleSpec(STICKERS, "Notes"),
        ModuleSpec(WALLPAPERS, "Wallpapers"),
        ModuleSpec(SETTINGS, "Settings")
    )

    val home = ModuleRoute("To-Do", "Tasks")

    fun routeFor(moduleId: String): ModuleRoute = when (moduleId) {
        TASKS -> home
        HISTORY -> ModuleRoute("To-Do", "History")
        CALENDAR -> ModuleRoute("Calendar")
        VAULT -> ModuleRoute("Vault")
        QUOTES -> ModuleRoute("Quotes")
        INFO -> ModuleRoute("Info")
        STICKERS -> ModuleRoute("Stickers")
        WALLPAPERS -> ModuleRoute("Wallpapers")
        SETTINGS -> ModuleRoute("Settings")
        else -> home
    }

    /** Which tab is highlighted for the current page, including that module's sub-screens. */
    fun moduleFor(page: String, todoPage: String): String = when (page) {
        "To-Do" -> TASKS
        "TodoSettings", "TodoWallpaper" -> TASKS
        "Calendar", "CalendarSettings", "CalendarWallpaper" -> CALENDAR
        "StickerSettings" -> STICKERS
        "Vault", "VaultAppearance" -> VAULT
        "Info", "SystemMonitorAppearance", "SystemMonitorWallpaper" -> INFO
        "Quotes" -> QUOTES
        "Stickers" -> STICKERS
        "Wallpapers" -> WALLPAPERS
        "Settings", "Permissions", "VaultSecurity", "SupportClarity", "About" -> SETTINGS
        else -> TASKS
    }

    private val settingsSubScreens = setOf("Permissions", "VaultSecurity", "SupportClarity", "About")

    /** Detail screen -> module screen -> home -> (double back) exit. */
    fun backStep(page: String, todoPage: String, routeDepth: Int): BackStep = when {
        page == "To-Do" && todoPage == "History" && routeDepth > 0 -> BackStep.PopRoute
        page in settingsSubScreens -> BackStep.GoTo(ModuleRoute("Settings"))
        page == "TodoSettings" -> BackStep.GoTo(home)
        page == "TodoWallpaper" -> BackStep.GoTo(ModuleRoute("TodoSettings"))
        page == "CalendarWallpaper" -> BackStep.GoTo(ModuleRoute("CalendarSettings"))
        page == "CalendarSettings" -> BackStep.GoTo(ModuleRoute("Calendar"))
        page == "SystemMonitorWallpaper" -> BackStep.GoTo(ModuleRoute("SystemMonitorAppearance"))
        page == "SystemMonitorAppearance" -> BackStep.GoTo(ModuleRoute("Info"))
        page == "StickerSettings" -> BackStep.GoTo(ModuleRoute("Stickers"))
        page == "VaultAppearance" -> BackStep.GoTo(ModuleRoute("Vault"))
        page == "To-Do" && todoPage == "Tasks" -> BackStep.AtHome
        else -> BackStep.GoTo(home)
    }

    /** Horizontal swipe target; null at either end of the module list. */
    fun adjacent(moduleId: String, forward: Boolean): String? {
        val index = modules.indexOfFirst { it.id == moduleId }
        if (index < 0) return null
        return modules.getOrNull(if (forward) index + 1 else index - 1)?.id
    }

    fun restoredModule(id: String?): String? =
        (if (id == QUOTES) INFO else id)?.takeIf { saved -> modules.any { it.id == saved } }

    const val ACTION_OPEN_INFO = "com.example.darktodo.action.OPEN_INFO"

    fun intentModule(action: String?): String? = when (action) {
        ACTION_OPEN_INFO -> INFO
        else -> null
    }
}
