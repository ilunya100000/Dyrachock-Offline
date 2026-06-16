package com.example.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.desktop.ui.DurakApp
import com.example.desktop.ui.theme.MyApplicationTheme
import com.example.desktop.viewmodel.DurakViewModel
import javax.imageio.ImageIO

fun main() = application {
    val viewModel = remember { DurakViewModel() }

    val windowState: WindowState = rememberWindowState(
        width = 480.dp,
        height = 880.dp
    )

    val iconPainter = remember {
        runCatching {
            val stream = object {}.javaClass.getResourceAsStream("/icon.png")
                ?: return@runCatching null
            stream.use { BitmapPainter(ImageIO.read(it).toComposeImageBitmap()) }
        }.getOrNull()
    }

    Window(
        onCloseRequest = {
            viewModel.onClosed()
            exitApplication()
        },
        state = windowState,
        title = "Dyrachok — Russian Card Game",
        icon = iconPainter
    ) {
        MyApplicationTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                DurakApp(viewModel = viewModel)
            }
        }
    }
}
