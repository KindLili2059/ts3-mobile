package com.example.ts3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ts3.R
import com.example.ts3.viewmodel.Ts3ViewModel

/**
 * 底部常驻语音控制栏：麦克风静音、扬声器静音、按住说话(PTT)、离开频道。
 */
@Composable
fun VoiceControls(viewModel: Ts3ViewModel, modifier: Modifier = Modifier) {
    val micMuted by viewModel.micMuted.collectAsStateWithLifecycle()
    val speakerMuted by viewModel.speakerMuted.collectAsStateWithLifecycle()

    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.toggleMic() }) {
                Icon(
                    if (micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = stringResource(
                        if (micMuted) R.string.voice_mic_mute else R.string.voice_mic_unmute
                    ),
                    tint = if (micMuted) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(4.dp))

            IconButton(onClick = { viewModel.toggleSpeaker() }) {
                Icon(
                    if (speakerMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(
                        if (speakerMuted) R.string.voice_speaker_mute
                        else R.string.voice_speaker_unmute
                    ),
                    tint = if (speakerMuted) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))

            // PTT：按住说话
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                viewModel.setPtt(true)
                                tryAwaitRelease()
                                viewModel.setPtt(false)
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.voice_ptt),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(8.dp))

            IconButton(onClick = { viewModel.leaveChannel() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = stringResource(R.string.channel_leave),
                )
            }
        }
    }
}
