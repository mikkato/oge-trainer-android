package ru.tolmachev.ogetrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.tolmachev.ogetrainer.ui.OgeRoot
import ru.tolmachev.ogetrainer.ui.theme.OgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Режим оформления читается здесь: смена темы перекрашивает всё дерево,
            // включая системные полосы (см. SideEffect в OgeTheme).
            val themeMode by Graph.settings.themeMode.collectAsState()
            OgeTheme(mode = themeMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    OgeRoot()
                }
            }
        }
    }
}
