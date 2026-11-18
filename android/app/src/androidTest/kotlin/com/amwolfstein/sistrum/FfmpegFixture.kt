package com.amwolfstein.sistrum

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode

/** Generates audio fixtures for device tests. Returns success and the FFmpeg log. */
internal fun runFfmpegFixture(arguments: Array<String>): Pair<Boolean, String> {
    val session = FFmpegKit.executeWithArguments(arguments)
    return ReturnCode.isSuccess(session.returnCode) to (session.allLogsAsString ?: "")
}
