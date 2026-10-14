package com.remotedroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.remotedroid.app.ui.navigation.RemoteDroidNavGraph
import com.remotedroid.app.ui.theme.RemoteDroidTheme

/**
 * Unica Activity do app (padrao single-activity + Jetpack Compose Navigation).
 * Toda a navegacao entre as telas de Conexao, Painel, Info, Apps, Arquivos e
 * Logs acontece dentro de RemoteDroidNavGraph.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemoteDroidTheme {
                Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    RemoteDroidNavGraph()
                }
            }
        }
    }
}
