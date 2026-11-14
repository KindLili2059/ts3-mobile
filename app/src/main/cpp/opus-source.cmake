# 通过 FetchContent 下载并编译 Opus 1.6.1（SHA-256 锁定，防篡改）。
include(FetchContent)
include(${CMAKE_CURRENT_LIST_DIR}/opus-options.cmake)

set(OPUS_SOURCE_URL "https://downloads.xiph.org/releases/opus/opus-1.6.1.tar.gz")
set(OPUS_SOURCE_MIRROR "https://ftp.osuosl.org/pub/xiph/releases/opus/opus-1.6.1.tar.gz")
set(OPUS_SOURCE_SHA256 "6ffcb593207be92584df15b32466ed64bbec99109f007c82205f0194572411a1")

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

FetchContent_Declare(
    ts3_opus
    URL ${OPUS_SOURCE_URL} ${OPUS_SOURCE_MIRROR}
    URL_HASH SHA256=${OPUS_SOURCE_SHA256}
    TLS_VERIFY TRUE
    TIMEOUT 90
)
FetchContent_MakeAvailable(ts3_opus)
target_compile_options(opus PRIVATE -O3)
