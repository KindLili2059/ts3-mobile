package com.example.ts3.repository

import com.example.ts3.manager.SessionManager

/**
 * 语音控制业务：麦克风静音、扬声器静音、按住说话(PTT)。
 * 实际操作由 [SessionManager] 转发到 SDK。
 */
class VoiceRepository {

    fun setMicMuted(muted: Boolean) = SessionManager.setMicMuted(muted)

    fun setSpeakerMuted(muted: Boolean) = SessionManager.setSpeakerMuted(muted)

    fun setPtt(pressed: Boolean) = SessionManager.setPtt(pressed)
}
