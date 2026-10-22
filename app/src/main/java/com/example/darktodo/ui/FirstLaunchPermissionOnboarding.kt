package com.example.darktodo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.darktodo.reminders.ReminderScheduler

@Composable
fun FirstLaunchPermissionOnboarding(
    remindersEnabled: Boolean,
    permissionRevision: Int,
    onPermissionStateChanged: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("permission_onboarding", 0) }
    var step by remember {
        mutableIntStateOf(if (preferences.getBoolean("completed", false)) 0 else 1)
    }
    var error by remember { mutableStateOf<String?>(null) }

    fun continueAfterNotifications() {
        val exactNeeded = remindersEnabled && Build.VERSION.SDK_INT >= 31 && !ReminderScheduler.canScheduleExactly(context)
        if (exactNeeded) step = 2 else {
            preferences.edit().putBoolean("completed", true).apply()
            step = 0
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPermissionStateChanged()
        continueAfterNotifications()
    }
    LaunchedEffect(step, permissionRevision) {
        if (step == 2 && ReminderScheduler.canScheduleExactly(context)) {
            preferences.edit().putBoolean("completed", true).apply()
            step = 0
        }
    }

    if (step == 1) AlertDialog(
        onDismissRequest = {
            preferences.edit().putBoolean("notification_explained", true).apply()
            continueAfterNotifications()
        },
        containerColor = UiTokens.Surface,
        title = { Text("Stay on top of tasks", color = UiTokens.Ink) },
        text = { Text("Clarity can send notifications for task reminders and scheduled alerts. You can keep using the app if you choose not to allow them.", color = UiTokens.Muted) },
        confirmButton = {
            TextButton(onClick = {
                preferences.edit().putBoolean("notification_explained", true).apply()
                when {
                    Build.VERSION.SDK_INT < 33 -> continueAfterNotifications()
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> continueAfterNotifications()
                    preferences.getBoolean("notification_requested", false) -> continueAfterNotifications()
                    else -> {
                        preferences.edit().putBoolean("notification_requested", true).apply()
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }) { Text(if (Build.VERSION.SDK_INT >= 33) "Continue" else "Got it", color = UiTokens.Accent) }
        },
        dismissButton = {
            TextButton(onClick = {
                preferences.edit().putBoolean("notification_explained", true).apply()
                continueAfterNotifications()
            }) { Text("Not now", color = UiTokens.Muted) }
        }
    )

    if (step == 2) AlertDialog(
        onDismissRequest = {
            preferences.edit().putBoolean("completed", true).apply()
            step = 0
        },
        containerColor = UiTokens.Surface,
        title = { Text("More precise reminders", color = UiTokens.Ink) },
        text = {
            Text(
                error ?: "Clarity has scheduled task reminders. Exact-alarm access helps them arrive close to the time you chose. Without it, Android may delay them; reminders still work where possible.",
                color = UiTokens.Muted
            )
        },
        confirmButton = {
            TextButton(onClick = {
                error = null
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                    )
                } catch (failure: Exception) {
                    Log.e("PermissionOnboarding", "Unable to open exact-alarm settings", failure)
                    try {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
                    } catch (fallbackFailure: Exception) {
                        Log.e("PermissionOnboarding", "Unable to open app settings", fallbackFailure)
                        error = "Android settings could not be opened. You can continue without exact-alarm access and revisit Settings > Permissions later."
                    }
                }
            }) { Text("Enable exact alarms", color = UiTokens.Accent) }
        },
        dismissButton = {
            TextButton(onClick = {
                preferences.edit().putBoolean("completed", true).apply()
                step = 0
            }) { Text("Continue without it", color = UiTokens.Muted) }
        }
    )
}
