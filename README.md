# TS3 Mobile

基于 **ts3j（TeamSpeak 3 协议实现）** 的 Android 轻量语音客户端（v1）。

- 技术栈：Kotlin + C++/JNI（Opus）+ Jetpack Compose
- 架构：MVVM + Repository + StateFlow + Coroutines
- 语音引擎：ts3j（纯 JVM TS3 协议）+ Opus 1.6.1 编解码
- 最低系统：Android 8.0（API 26），目标 API 34

## 功能（v1）

- 连接私人服务器（IP/域名、端口、昵称、服务器密码）
- 服务器书签列表（保存/删除，含最近连接记录）
- 客户端身份静默自动生成、保存与复用
- 频道树（层级、密码标识、在线人数）
- 加入 / 离开频道（支持频道密码）
- 显示频道内用户与说话状态
- 实时双向语音互通
- 麦克风静音、扬声器静音、按住说话（PTT）
- 后台语音 + 锁屏保活（前台服务 + 常驻通知）

## 构建

```bash
# 需要 JDK 17 与 Android SDK（compileSdk 34 / build-tools）
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> 本仓库已内置 Gradle Wrapper（`gradlew` / `gradlew.bat` / `gradle-wrapper.jar`，Gradle 8.9），
> 可直接用 `./gradlew assembleDebug` 构建；也可用 **Android Studio** 打开本项目构建。
>
> 首次构建会通过 CMake `FetchContent` 自动下载并编译 Opus 1.6.1 源码。

## 架构分层

```
Presentation   MainActivity / ConnectScreen / ChannelScreen / VoiceControls
Application    Ts3ViewModel ── Ts3Repository
               ├─ ConnectionRepository ── ServerStore / SecureStore / Ts3VoiceService
               └─ VoiceRepository
Session        SessionManager（ts3j LocalTeamspeakClientSocket + TS3Listener）
Audio          Ts3AudioEngine（AudioRecord/AudioTrack + Opus 编解码）
Protocol       ts3j（com.github.monet4070:ts3j，纯 JVM TS3 协议，UDP）
Native         libts3opus_jni.so（opus_jni.cpp，Opus 1.6.1）
```

- `SessionManager`（单例）是连接的**唯一所有者**，持有唯一的 `LocalTeamspeakClientSocket`，
  并实现 `TS3Listener` 将事件映射为频道/用户状态。
- `Ts3VoiceService` 是前台服务，负责保活与常驻通知；连接生命周期由 `SessionManager` 统一管理。

## 目录结构

```
TS3Mobile/
├── app/src/main/
│   ├── java/com/example/ts3/
│   │   ├── MainActivity.kt
│   │   ├── Ts3Application.kt
│   │   ├── data/model/Models.kt
│   │   ├── data/store/ServerStore.kt        # 书签（DataStore）
│   │   ├── data/store/SecureStore.kt        # 身份/密码（Keystore AES/GCM）
│   │   ├── manager/SessionManager.kt        # ts3j 会话唯一所有者
│   │   ├── audio/NativeOpusEncoder.kt       # Opus 编码（JNI）
│   │   ├── audio/NativeOpusDecoder.kt       # Opus 解码（JNI）
│   │   ├── audio/Ts3AudioEngine.kt          # 采集/播放 + Opus 编解码 + ts3j Microphone
│   │   ├── repository/{Ts3,Connection,Voice}Repository.kt
│   │   ├── service/Ts3VoiceService.kt       # 前台语音服务
│   │   ├── viewmodel/Ts3ViewModel.kt
│   │   └── ui/{ConnectScreen,ChannelScreen,VoiceScreen}.kt
│   ├── cpp/
│   │   ├── CMakeLists.txt
│   │   ├── opus-source.cmake                # FetchContent 下载 Opus 1.6.1
│   │   ├── opus-options.cmake               # Opus 编译选项
│   │   └── opus_jni.cpp                     # Opus 编解码 JNI
│   ├── res/ ...
│   └── AndroidManifest.xml
└── gradle/libs.versions.toml                 # 含 ts3j 依赖（JitPack）
```

## 权限

`INTERNET`、`RECORD_AUDIO`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MICROPHONE`、
`POST_NOTIFICATIONS`（13+）、`MODIFY_AUDIO_SETTINGS`、`BLUETOOTH_CONNECT`（12+）。

## 第一版不实现

文件传输、完整文字聊天、服务器管理、ServerQuery、插件系统、音效、多服务器同时在线。
