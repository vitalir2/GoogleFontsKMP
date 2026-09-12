package vitalir.me.googlefonts.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import vitalir.me.googlefonts.GoogleFont
import vitalir.me.googlefonts.rememberGoogleFontFamily

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "GoogleFontsKMP Sample") {
        MaterialTheme {
            SampleContent()
        }
    }
}

@Composable
fun SampleContent() {
    val robotoBold = rememberGoogleFontFamily(GoogleFont("Roboto"), weight = FontWeight.Bold)
    val openSans = rememberGoogleFontFamily(GoogleFont("Open Sans"))
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Roboto Bold", fontFamily = robotoBold, fontSize = 32.sp)
        Text("Open Sans Regular", fontFamily = openSans, fontSize = 32.sp)
        Text("System font (fallback while loading)", fontSize = 32.sp)
    }
}