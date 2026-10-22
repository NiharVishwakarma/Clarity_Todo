package com.example.darktodo

import android.app.Application
import com.example.darktodo.backup.BackupScheduler

class ClarityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BackupScheduler.install(this)
        com.example.darktodo.widget.QuotesWidgetRotation.reconcile(this)
    }
}
