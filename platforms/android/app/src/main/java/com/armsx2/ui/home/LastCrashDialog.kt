package com.armsx2.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.i18n.str
import com.armsx2.runtime.MainActivityRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Nexus2: after the app closed on an error, show what the crash handler wrote (logs/crash-*.txt,
 * Pasx2Application) once, with a Copy button, so it can be sent without digging in Android/data.
 */
@Composable
fun LastCrashDialog() {
    val context = LocalContext.current
    val crash by produceState<File?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
                val seen = MainActivityRuntime.prefs.getLong(PREF_SEEN, 0L)
                dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".txt") }
                    ?.filter { it.lastModified() > seen && System.currentTimeMillis() - it.lastModified() < 3L * 24 * 3600 * 1000 }
                    ?.maxByOrNull { it.lastModified() }
            }.getOrNull()
        }
    }
    var dismissed by remember { mutableStateOf(false) }
    val file = crash ?: return
    if (dismissed) return
    val text = remember(file) { runCatching { file.readText().take(6000) }.getOrDefault("") }
    fun close() {
        dismissed = true
        runCatching { MainActivityRuntime.prefs.edit().putLong(PREF_SEEN, file.lastModified()).apply() }
    }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text(str("nexus.crash.title")) },
        text = {
            Text(
                text,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Nexus2 crash", text))
                }
                close()
            }) { Text(str("nexus.crash.copy")) }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text(str("nexus.crash.close")) } },
    )
}

private const val PREF_SEEN = "nexus.crash.lastSeen"
