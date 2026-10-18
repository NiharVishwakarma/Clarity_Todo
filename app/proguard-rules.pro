-repackageclasses ''
-allowaccessmodification
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-assumenosideeffects class android.util.Log {
    *;
}

-keep class com.example.darktodo.backup.BackupWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
