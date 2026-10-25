package dev.campususage.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.campususage.app.core.design.DesignConfig
import dev.campususage.app.ui.CampusApp
import dev.campususage.app.ui.UsageViewModel

class MainActivity : ComponentActivity() {
    private val vm: UsageViewModel by viewModels()
    private var design by mutableStateOf<DesignConfig?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        design = DesignConfig.load(this)
        setContent { design?.let { CampusApp(it, vm) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("reloadDesign",false)) design = DesignConfig.load(this)
    }
    override fun onResume() { super.onResume(); vm.resume() }
}
