package com.example.darktodo.navigation

import android.content.Context

object ModulePrefs {
    private const val PREFS = "navigation_prefs"
    private const val LAST_MODULE = "last_module"

    fun last(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_MODULE, null)
            ?.takeIf { id -> ModuleNavigation.modules.any { it.id == id } }

    fun save(context: Context, moduleId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LAST_MODULE, moduleId).apply()
    }
}
