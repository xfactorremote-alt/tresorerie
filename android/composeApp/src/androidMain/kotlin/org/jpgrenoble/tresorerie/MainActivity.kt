package org.jpgrenoble.tresorerie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ContexteApp.contexte = applicationContext
        planifierVerification()   // nouveautés vérifiées toutes les 15 minutes, application fermée
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
