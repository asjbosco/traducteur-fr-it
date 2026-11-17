package fr.manu.traducteur

import android.app.Application
import fr.manu.traducteur.data.HistoryDatabase
import fr.manu.traducteur.data.OfflineTranslator
import fr.manu.traducteur.data.TextScanner

class TraducteurApp : Application() {
    val database by lazy { HistoryDatabase.get(this) }
    val translator by lazy { OfflineTranslator() }
    val scanner by lazy { TextScanner() }
}
