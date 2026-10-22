-repackageclasses ''
-allowaccessmodification
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

-keep class com.example.darktodo.backup.BackupWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keep class com.example.darktodo.widget.SystemMonitorWidgetWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keep class com.example.darktodo.widget.WallpaperApplyWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keep class com.example.darktodo.widget.StickerWidgetConfigActivity { *; }

-keep class com.example.darktodo.widget.QuotesWidgetWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
