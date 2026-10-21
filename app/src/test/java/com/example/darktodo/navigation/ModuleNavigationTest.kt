package com.example.darktodo.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleNavigationTest {
    @Test fun registryContainsPlannedModulesInOrder() {
        assertEquals(
            listOf("Tasks", "Calendar", "Vault", "Info", "Notes", "Wallpapers", "Settings"),
            ModuleNavigation.modules.map { it.label }
        )
    }

    @Test fun everyModuleRoundTripsThroughItsRoute() {
        ModuleNavigation.modules.forEach { module ->
            val route = ModuleNavigation.routeFor(module.id)
            assertEquals(module.id, ModuleNavigation.moduleFor(route.page, route.todoPage))
        }
    }

    @Test fun subScreensKeepTheirParentTabSelected() {
        assertEquals(ModuleNavigation.SETTINGS, ModuleNavigation.moduleFor("Permissions", "Tasks"))
        assertEquals(ModuleNavigation.SETTINGS, ModuleNavigation.moduleFor("SupportClarity", "Tasks"))
        assertEquals(ModuleNavigation.TASKS, ModuleNavigation.moduleFor("TodoSettings", "Tasks"))
        assertEquals(ModuleNavigation.CALENDAR, ModuleNavigation.moduleFor("CalendarSettings", "Tasks"))
    }

    @Test fun backFromSettingsDetailReturnsToSettings() {
        listOf("Permissions", "VaultSecurity", "SupportClarity", "About").forEach {
            assertEquals(BackStep.GoTo(ModuleRoute("Settings")), ModuleNavigation.backStep(it, "Tasks", 0))
        }
    }

    @Test fun backFromWidgetSettingsReturnsToItsModule() {
        assertEquals(BackStep.GoTo(ModuleNavigation.home), ModuleNavigation.backStep("TodoSettings", "Tasks", 0))
        assertEquals(BackStep.GoTo(ModuleRoute("Calendar")), ModuleNavigation.backStep("CalendarSettings", "Tasks", 0))
    }

    @Test fun wallpaperEditorsBelongToTheirModuleSettings() {
        assertEquals(ModuleNavigation.TASKS, ModuleNavigation.moduleFor("TodoWallpaper", "Tasks"))
        assertEquals(ModuleNavigation.CALENDAR, ModuleNavigation.moduleFor("CalendarWallpaper", "Tasks"))
        assertEquals(ModuleNavigation.STICKERS, ModuleNavigation.moduleFor("StickerSettings", "Tasks"))
        assertEquals(BackStep.GoTo(ModuleRoute("TodoSettings")), ModuleNavigation.backStep("TodoWallpaper", "Tasks", 0))
        assertEquals(BackStep.GoTo(ModuleRoute("Stickers")), ModuleNavigation.backStep("StickerSettings", "Tasks", 0))
    }

    @Test fun backInsideHistoryDrillDownPopsBeforeLeavingModule() {
        assertEquals(BackStep.PopRoute, ModuleNavigation.backStep("To-Do", "History", 2))
        assertEquals(BackStep.GoTo(ModuleNavigation.home), ModuleNavigation.backStep("To-Do", "History", 0))
    }

    @Test fun backFromAnyModuleGoesHomeThenArmsExit() {
        listOf("Calendar", "Vault", "Quotes", "Stickers", "Wallpapers", "Settings").forEach {
            assertEquals(BackStep.GoTo(ModuleNavigation.home), ModuleNavigation.backStep(it, "Tasks", 0))
        }
        assertEquals(BackStep.AtHome, ModuleNavigation.backStep("To-Do", "Tasks", 0))
    }

    @Test fun backEventuallyReachesHomeFromEveryPage() {
        val pages = listOf("Permissions", "About", "TodoSettings", "CalendarSettings", "Vault", "Stickers", "Settings")
        pages.forEach { start ->
            var page = start; var todo = "Tasks"; var steps = 0
            while (true) {
                val step = ModuleNavigation.backStep(page, todo, 0)
                if (step is BackStep.AtHome) break
                step as BackStep.GoTo
                page = step.route.page; todo = step.route.todoPage
                assertTrue("back loop from $start", ++steps < 5)
            }
        }
    }

    @Test fun swipeNeighboursStopAtEnds() {
        assertNull(ModuleNavigation.adjacent(ModuleNavigation.TASKS, forward = false))
        assertEquals(ModuleNavigation.CALENDAR, ModuleNavigation.adjacent(ModuleNavigation.TASKS, forward = true))
        assertNull(ModuleNavigation.adjacent(ModuleNavigation.SETTINGS, forward = true))
        assertNull(ModuleNavigation.adjacent("unknown", forward = true))
    }

    @Test fun unknownModuleFallsBackToHome() {
        assertEquals(ModuleNavigation.home, ModuleNavigation.routeFor("habits"))
    }

    @Test fun oldQuotesSelectionRestoresInfoWithoutRemovingLegacyRoute() {
        assertEquals(ModuleNavigation.INFO, ModuleNavigation.restoredModule(ModuleNavigation.QUOTES))
        assertEquals(ModuleRoute("Quotes"), ModuleNavigation.routeFor(ModuleNavigation.QUOTES))
        assertNull(ModuleNavigation.restoredModule("unknown"))
        assertNull(ModuleNavigation.restoredModule(null))
        assertEquals(ModuleNavigation.INFO, ModuleNavigation.restoredModule(ModuleNavigation.INFO))
    }

    @Test fun infoNavigationUsesTheSharedSlotAndReturnsHome() {
        assertEquals(ModuleNavigation.INFO, ModuleNavigation.adjacent(ModuleNavigation.VAULT, true))
        assertEquals(ModuleNavigation.STICKERS, ModuleNavigation.adjacent(ModuleNavigation.INFO, true))
        assertEquals(BackStep.GoTo(ModuleNavigation.home), ModuleNavigation.backStep("Info", "Tasks", 0))
        assertEquals(ModuleNavigation.INFO, ModuleNavigation.intentModule(ModuleNavigation.ACTION_OPEN_INFO))
        assertNull(ModuleNavigation.intentModule("other"))
        assertNull(ModuleNavigation.intentModule(null))
    }

    @Test fun vaultAppearanceBelongsToVaultNotAuthenticationSettings() {
        assertEquals(ModuleNavigation.VAULT, ModuleNavigation.moduleFor("VaultAppearance", "Tasks"))
        assertEquals(BackStep.GoTo(ModuleRoute("Vault")), ModuleNavigation.backStep("VaultAppearance", "Tasks", 0))
    }

    @Test fun systemMonitorAppearanceBelongsToInfo() {
        assertEquals(ModuleNavigation.INFO, ModuleNavigation.moduleFor("SystemMonitorAppearance", "Tasks"))
        assertEquals(ModuleNavigation.INFO, ModuleNavigation.moduleFor("SystemMonitorWallpaper", "Tasks"))
        assertEquals(BackStep.GoTo(ModuleRoute("Info")), ModuleNavigation.backStep("SystemMonitorAppearance", "Tasks", 0))
        assertEquals(BackStep.GoTo(ModuleRoute("SystemMonitorAppearance")), ModuleNavigation.backStep("SystemMonitorWallpaper", "Tasks", 0))
    }
}
