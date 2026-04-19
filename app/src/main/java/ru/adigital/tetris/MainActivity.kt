package ru.adigital.tetris

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.adigital.tetris.ble.BleTetrisClient
import ru.adigital.tetris.ble.BleTetrisConfig
import ru.adigital.tetris.ble.bleConnectPermissions
import ru.adigital.tetris.ble.hasBleConnectPermissions
import ru.adigital.tetris.ui.theme.TetrisTheme

private fun tapQueuedMessage(activity: ComponentActivity, ok: Boolean): String =
    if (ok) activity.getString(R.string.ble_tap_queued)
    else activity.getString(R.string.ble_blink_no_connection)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TetrisTheme {
                BleConnectScreen()
            }
        }
    }
}

@Composable
private fun BleConnectScreen() {
    val activity = LocalContext.current as ComponentActivity
    val client = remember { BleTetrisClient(activity.applicationContext) }

    DisposableEffect(Unit) {
        onDispose { client.release() }
    }

    var statusText by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingConnect by remember { mutableStateOf(false) }
    var bleSessionReady by remember { mutableStateOf(false) }

    fun runBleConnect() {
        bleSessionReady = false
        statusText = activity.getString(R.string.ble_scanning)
        client.beginConnect(
            onUserMessage = { code ->
                statusText = when (code) {
                    "BT_OFF" -> activity.getString(R.string.ble_bt_disabled)
                    "TIMEOUT" -> activity.getString(R.string.ble_timeout)
                    "NO_ADAPTER" -> activity.getString(R.string.ble_no_adapter)
                    "CONNECTED" -> activity.getString(R.string.ble_connected)
                    "SERVICE_MISSING" -> activity.getString(R.string.ble_service_missing)
                    "DISCOVER_FAILED" -> activity.getString(R.string.ble_discover_failed)
                    else -> statusText
                }
                bleSessionReady = code == "CONNECTED"
            },
            onBusy = { b -> busy = b },
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val ok = result.values.all { it }
        if (!ok) {
            Log.e(BleTetrisConfig.LOG_TAG, "Permissions denied: $result")
            statusText = activity.getString(R.string.ble_permissions_denied)
            pendingConnect = false
            return@rememberLauncherForActivityResult
        }
        if (pendingConnect) {
            pendingConnect = false
            runBleConnect()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        BleConnectContent(
            busy = busy,
            bleSessionReady = bleSessionReady,
            statusText = statusText,
            onConnectClick = {
                if (!activity.hasBleConnectPermissions()) {
                    pendingConnect = true
                    permLauncher.launch(bleConnectPermissions())
                } else {
                    runBleConnect()
                }
            },
            onBlinkClick = {
                val ok = client.requestBlink()
                statusText = if (ok) {
                    activity.getString(R.string.ble_blink_queued)
                } else {
                    activity.getString(R.string.ble_blink_no_connection)
                }
            },
            onTapLeft = {
                val ok = client.requestTapLeft()
                statusText = tapQueuedMessage(activity, ok)
            },
            onTapRight = {
                val ok = client.requestTapRight()
                statusText = tapQueuedMessage(activity, ok)
            },
            onTapDown = {
                val ok = client.requestTapDown()
                statusText = tapQueuedMessage(activity, ok)
            },
            onTapRotate = {
                val ok = client.requestTapRotate()
                statusText = tapQueuedMessage(activity, ok)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        )
    }
}

@Composable
private fun BleConnectContent(
    busy: Boolean,
    bleSessionReady: Boolean,
    statusText: String?,
    onConnectClick: () -> Unit,
    onBlinkClick: () -> Unit,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onTapDown: () -> Unit,
    onTapRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onConnectClick,
            enabled = !busy,
        ) {
            Text(text = stringResource(R.string.connect_ble))
        }
        Spacer(modifier = Modifier.padding(top = 12.dp))
        Button(
            onClick = onBlinkClick,
            enabled = !busy && bleSessionReady,
        ) {
            Text(text = stringResource(R.string.blink_led))
        }
        Spacer(modifier = Modifier.padding(top = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onTapLeft,
                        enabled = !busy && bleSessionReady,
                    ) {
                        Text(text = stringResource(R.string.tap_left))
                    }
                    Button(
                        onClick = onTapRight,
                        enabled = !busy && bleSessionReady,
                    ) {
                        Text(text = stringResource(R.string.tap_right))
                    }
                }
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Button(
                    onClick = onTapDown,
                    enabled = !busy && bleSessionReady,
                ) {
                    Text(text = stringResource(R.string.tap_down))
                }
            }
            Button(
                onClick = onTapRotate,
                enabled = !busy && bleSessionReady,
            ) {
                Text(text = stringResource(R.string.tap_rotate))
            }
        }
        statusText?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Preview(name = "Контроллер подключён", showBackground = true)
@Composable
private fun BleConnectContentConnectedPreview() {
    TetrisTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            BleConnectContent(
                busy = false,
                bleSessionReady = true,
                statusText = stringResource(R.string.ble_connected),
                onConnectClick = {},
                onBlinkClick = {},
                onTapLeft = {},
                onTapRight = {},
                onTapDown = {},
                onTapRotate = {},
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        }
    }
}

@Preview(name = "Ожидание подключения", showBackground = true)
@Composable
private fun BleConnectContentDisconnectedPreview() {
    TetrisTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            BleConnectContent(
                busy = false,
                bleSessionReady = false,
                statusText = null,
                onConnectClick = {},
                onBlinkClick = {},
                onTapLeft = {},
                onTapRight = {},
                onTapDown = {},
                onTapRotate = {},
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        }
    }
}

@Preview(name = "Поиск…", showBackground = true)
@Composable
private fun BleConnectContentBusyPreview() {
    TetrisTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            BleConnectContent(
                busy = true,
                bleSessionReady = false,
                statusText = stringResource(R.string.ble_scanning),
                onConnectClick = {},
                onBlinkClick = {},
                onTapLeft = {},
                onTapRight = {},
                onTapDown = {},
                onTapRotate = {},
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        }
    }
}
