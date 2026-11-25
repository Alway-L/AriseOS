package com.droiddeck.launcher.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.frontend.GameMods
import com.droiddeck.launcher.frontend.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GameModsDialog(game: Library.SteamGame, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val pal = LocalPalette.current
    val scope = rememberCoroutineScope()
    val shown = rememberShown(onDismiss)
    val close = { shown.targetState = false }
    var version by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val mods = remember(version) { GameMods.list(app, game) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val problem = withContext(Dispatchers.IO) { runCatching { GameMods.install(app, game, uri) }.getOrElse { it.message ?: "error" } }
            busy = false; version++
            Toast.makeText(app, if (problem == null) app.getString(R.string.mods_installed) else app.getString(R.string.mods_failed, problem), Toast.LENGTH_LONG).show()
        }
    }
    AppDialog(shown, close, "gameMods", wide = false, maxWidth = 520.dp) {
        DialogTitleRow(stringResource(R.string.mods_title, game.name), onClose = close)
        Text(stringResource(R.string.mods_hint), fontSize = 12.5.sp, color = pal.signal.copy(alpha = 0.85f))
        if (mods.isEmpty()) Text(stringResource(R.string.mods_none), fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        mods.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(ctx.resources.getQuantityString(R.plurals.mods_files, m.files, m.files), fontSize = 12.sp)
                }
                SecondaryButton(stringResource(R.string.mods_remove), enabled = !busy, compact = true) {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { GameMods.remove(app, game, m) } }
                        busy = false; version++
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (game.source == Library.SOURCE_STEAM && game.library != Library.ADDED) {
                SecondaryButton(stringResource(R.string.mods_workshop), compact = true) {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/app/${game.appId}/workshop/"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            PrimaryButton(stringResource(if (busy) R.string.mods_working else R.string.mods_add), enabled = !busy) {
                pick.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
        }
    }
}
