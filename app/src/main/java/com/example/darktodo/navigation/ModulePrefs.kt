package com.example.darktodo.navigation

import android.content.Context

object ModulePrefs {
    private const val PREFS = "navigation_prefs"
    private const val LAST_MODULE = "last_module"

    fun last(context: Context): String? = ModuleNavigation.restoredModule(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_MODULE, null)
    )

    fun save(context: Context, moduleId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LAST_MODULE, moduleId).apply()
    }
}
