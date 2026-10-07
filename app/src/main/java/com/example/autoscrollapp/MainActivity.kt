package com.example.autoscrollapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.autoscrollapp.ui.theme.AutoScrollAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScrollController.load(this)

        setContent {
            AutoScrollAppTheme {
                AutoScrollUI(
                    initialInterval = ScrollController.intervalSec,
                    onIntervalChange = {
                        ScrollController.setInterval(this, it)
                        AutoScrollService.instance?.applyNewInterval()
                    },
                    onStart = {
                        if (AutoScrollService.instance == null) {
                            Toast.makeText(this, "Please enable Accessibility Service", Toast.LENGTH_LONG).show()
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else {
                            AutoScrollService.instance?.startScrolling()
                            Toast.makeText(
                                this,
                                "Auto-scroll dimulai. Kontrol ada di bubble.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onStop = {
                        AutoScrollService.instance?.stopAndHide()
                        Toast.makeText(this, "Auto-scroll stopped", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun AutoScrollUI(
    initialInterval: Float,
    onIntervalChange: (Float) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var text by remember { mutableStateOf(ScrollController.format(initialInterval)) }

    val parsed = text.replace(',', '.').toFloatOrNull()
    val valid = parsed != null &&
            parsed >= ScrollController.MIN_INTERVAL_SEC &&
            parsed <= ScrollController.MAX_INTERVAL_SEC

    // Terapkan nilai baru (simpan + jadwalkan ulang) lalu sinkronkan teks
    fun commit(value: Float) {
        val v = value.coerceIn(ScrollController.MIN_INTERVAL_SEC, ScrollController.MAX_INTERVAL_SEC)
        text = ScrollController.format(v)
        onIntervalChange(v)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Jeda antar scroll")

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { commit((parsed ?: initialInterval) - 1f) }) { Text("−") }

            OutlinedTextField(
                value = text,
                onValueChange = {
                    // Hanya angka, titik, atau koma
                    if (it.all { c -> c.isDigit() || c == '.' || c == ',' }) {
                        text = it
                        val p = it.replace(',', '.').toFloatOrNull()
                        if (p != null &&
                            p >= ScrollController.MIN_INTERVAL_SEC &&
                            p <= ScrollController.MAX_INTERVAL_SEC
                        ) {
                            onIntervalChange(p)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = !valid,
                suffix = { Text("detik") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    Text(
                        if (valid) "Boleh desimal, mis. 2.5"
                        else "Isi ${ScrollController.format(ScrollController.MIN_INTERVAL_SEC)}–" +
                                "${ScrollController.format(ScrollController.MAX_INTERVAL_SEC)} detik"
                    )
                }
            )

            OutlinedButton(onClick = { commit((parsed ?: initialInterval) + 1f) }) { Text("+") }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                // Pastikan nilai yang tampil di kolom = nilai yang dipakai
                if (valid) onIntervalChange(parsed!!) else commit(ScrollController.intervalSec)
                onStart()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Auto-Scroll")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text("Stop Auto-Scroll")
        }
    }
}
