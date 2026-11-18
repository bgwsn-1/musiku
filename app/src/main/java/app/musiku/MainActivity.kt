package app.musiku

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.musiku.ui.AppRoot
import app.musiku.ui.theme.MusikuTheme

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* playback works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars, with icon contrast following the system theme so they stay legible
        // when the app is in light mode (the previous hardcoded `dark` style forced white icons).
        val dark = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
            else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
            else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as MusikuApp
        app.handleIntent(intent)
        app.player // connect to the playback service right away
        setContent { MusikuTheme { AppRoot(app) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // launchMode="singleTask": a share/VIEW while the app is running lands here.
        (application as MusikuApp).handleIntent(intent)
    }
}
