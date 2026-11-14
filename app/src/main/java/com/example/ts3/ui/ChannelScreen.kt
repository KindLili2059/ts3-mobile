package com.example.ts3.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ts3.R
import com.example.ts3.data.model.Channel
import com.example.ts3.data.model.ClientUser
import com.example.ts3.viewmodel.Ts3ViewModel

/**
 * 已连接后的主界面：左频道树 + 右用户列表 + 底部语音控制栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(viewModel: Ts3ViewModel, modifier: Modifier = Modifier) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val currentChannelId by viewModel.currentChannelId.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()

    var pendingJoin by remember { mutableStateOf<Channel?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // 频道树：DFS 排序 + 深度
    val (orderedChannels, depthMap) = remember(channels) {
        val byParent = channels.groupBy { it.parentId }
        val ordered = mutableListOf<Channel>()
        val depth = mutableMapOf<Int, Int>()
        fun dfs(parentId: Int, d: Int) {
            byParent[parentId]?.sortedBy { it.order }?.forEach { ch ->
                ordered.add(ch)
                depth[ch.id] = d
                dfs(ch.id, d + 1)
            }
        }
        dfs(0, 0)
        ordered to depth
    }

    val currentUsers = clients.filter { it.channelId == currentChannelId }

    LaunchedEffect(lastError) {
        if (lastError != null) {
            snackbarHostState.showSnackbar(lastError!!)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(activeServer?.name ?: stringResource(R.string.app_name), maxLines = 1)
                },
                actions = {
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.connect_disconnect))
                    }
                },
            )
        },
        bottomBar = { VoiceControls(viewModel) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 左：频道树
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    stringResource(R.string.channel_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                if (orderedChannels.isEmpty()) {
                    Text(
                        "…",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn {
                        items(orderedChannels, key = { it.id }) { channel ->
                            ChannelRow(
                                channel = channel,
                                depth = depthMap[channel.id] ?: 0,
                                isCurrent = channel.id == currentChannelId,
                                onClick = {
                                    if (channel.id == currentChannelId) return@ChannelRow
                                    if (channel.hasPassword) {
                                        passwordInput = ""
                                        pendingJoin = channel
                                    } else {
                                        viewModel.joinChannel(channel.id, "")
                                    }
                                },
                            )
                        }
                    }
                }
            }

            VerticalDivider()

            // 右：用户列表（当前频道）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    stringResource(R.string.channel_users),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                LazyColumn {
                    items(currentUsers, key = { it.id }) { user ->
                        UserRow(user)
                    }
                }
            }
        }
    }

    // 加入密码频道对话框
    pendingJoin?.let { channel ->
        AlertDialog(
            onDismissRequest = { pendingJoin = null },
            title = { Text(stringResource(R.string.channel_password_prompt)) },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.joinChannel(channel.id, passwordInput)
                        pendingJoin = null
                    },
                ) {
                    Text(stringResource(R.string.channel_password_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingJoin = null }) {
                    Text(stringResource(R.string.channel_password_cancel))
                }
            },
        )
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    depth: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (16 + depth * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 用背景色块高亮当前频道
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.hasPassword) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            channel.clientCount.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isCurrent) {
            Text(
                stringResource(R.string.channel_current_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun UserRow(user: ClientUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (user.isTalking) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = stringResource(R.string.voice_talking),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Text(
            user.nickname,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = if (user.isTalking) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        if (user.isSelf) {
            Text(
                "我",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
