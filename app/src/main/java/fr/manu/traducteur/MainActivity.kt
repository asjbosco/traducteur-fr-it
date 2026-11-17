package fr.manu.traducteur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.manu.traducteur.ui.AppScreen
import fr.manu.traducteur.ui.TraducteurTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TraducteurTheme { AppScreen() }
        }
    }
}
