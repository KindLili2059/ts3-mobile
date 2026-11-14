package com.example.ts3.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ts3.R
import com.example.ts3.data.model.ConnectionState
import com.example.ts3.data.model.ServerConfig
import com.example.ts3.viewmodel.Ts3ViewModel
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(viewModel: Ts3ViewModel, modifier: Modifier = Modifier) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val reconnecting by viewModel.reconnecting.collectAsStateWithLifecycle()

    var serverName by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("9987") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var serverPassword by rememberSaveable { mutableStateOf("") }
    var saveBookmark by rememberSaveable { mutableStateOf(false) }
    var savePassword by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val connecting = connectionState != ConnectionState.DISCONNECTED
    val busy = connecting || reconnecting

    fun buildConfig(): ServerConfig? {
        val hostValue = host.trim()
        val portValue = port.trim().toIntOrNull() ?: 9987
        val nick = nickname.trim().ifEmpty { "用户${(1000..9999).random()}" }
        val config = ServerConfig(
            id = UUID.randomUUID().toString(),
            name = serverName.trim().ifEmpty { hostValue },
            host = hostValue,
            port = portValue,
            nickname = nick,
            savePassword = saveBookmark && savePassword,
        )
        val error = config.validationError()
        if (error != null) {
            scope.launch { snackbarHostState.showSnackbar(error) }
            return null
        }
        return config
    }

    fun doConnect(config: ServerConfig) {
        if (saveBookmark) viewModel.saveBookmark(config, serverPassword)
        viewModel.connect(config, serverPassword)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        val notifGranted = Build.VERSION.SDK_INT < 33 ||
            grants[Manifest.permission.POST_NOTIFICATIONS] == true
        if (micGranted && notifGranted) {
            buildConfig()?.let { doConnect(it) }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.permission_mic_rationale)
                )
            }
        }
    }

    fun onConnectClick() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isEmpty()) {
            buildConfig()?.let { doConnect(it) }
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // 展示错误
    LaunchedEffect(lastError) {
        if (lastError != null) {
            snackbarHostState.showSnackbar(lastError!!)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.connect_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text(stringResource(R.string.connect_server_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.connect_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.connect_port)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text(stringResource(R.string.connect_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = serverPassword,
                onValueChange = { serverPassword = it },
                label = { Text(stringResource(R.string.connect_server_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saveBookmark, onCheckedChange = { saveBookmark = it })
                Text(stringResource(R.string.connect_save), modifier = Modifier.width(120.dp))
                Checkbox(
                    checked = savePassword,
                    onCheckedChange = { savePassword = it },
                    enabled = saveBookmark,
                )
                Text(stringResource(R.string.connect_save_password))
            }

            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                    Text(
                        stringResource(
                            if (reconnecting) R.string.connect_reconnecting
                            else R.string.connect_connecting
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connect_disconnect))
                }
            } else {
                Button(
                    onClick = { onConnectClick() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connect_action))
                }
            }

            // 已保存的服务器
            if (servers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.connect_saved_servers),
                    style = MaterialTheme.typography.titleMedium,
                )
                servers.forEach { server ->
                    BookmarkRow(
                        server = server,
                        onConnect = {
                            serverName = server.name
                            host = server.host
                            port = server.port.toString()
                            nickname = server.nickname
                            saveBookmark = true
                            savePassword = server.savePassword
                            if (server.savePassword) {
                                viewModel.loadPassword(server.id) { pwd ->
                                    viewModel.connect(server, pwd.orEmpty())
                                }
                            } else {
                                viewModel.connect(server, "")
                            }
                        },
                        onDelete = { viewModel.deleteBookmark(server.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    server: ServerConfig,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${server.host}:${server.port} · ${server.nickname}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.connect_delete))
            }
            Button(onClick = onConnect) {
                Text(stringResource(R.string.connect_action))
            }
        }
    }
}
