package io.github.admin0330.liquidmusic.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.admin0330.liquidmusic.player.MusicService

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openPlayerRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        consumePlayerIntent(intent)
        setContent { LiquidMusicApp(openPlayerRequest = openPlayerRequest) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePlayerIntent(intent)
    }

    private fun consumePlayerIntent(intent: Intent?) {
        if (intent?.action == MusicService.ACTION_OPEN_PLAYER) {
            openPlayerRequest += 1
            intent.action = null
        }
    }
}
