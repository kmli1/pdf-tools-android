package com.pdfatolyesi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pdfatolyesi.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as PdfApplication
            val model: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(app) } })
            val theme by model.theme.collectAsStateWithLifecycle()
            PdfTheme(theme) { PdfApp(app, model) }
        }
    }
}
