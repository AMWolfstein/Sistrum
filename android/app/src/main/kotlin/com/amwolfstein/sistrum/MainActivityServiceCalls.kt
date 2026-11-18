package com.amwolfstein.sistrum

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun MainActivity.handleStartBackgroundWork(call: MethodCall, result: MethodChannel.Result) {
    result.success(
        BackgroundWorkService.start(
            this@handleStartBackgroundWork,
            call.argument<String>("kind") ?: "",
            call.argument<String>("title") ?: "",
            call.argument<String>("text") ?: "",
        ),
    )
}

internal fun MainActivity.handleUpdateBackgroundWork(call: MethodCall, result: MethodChannel.Result) {
    BackgroundWorkService.update(
        call.argument<String>("kind") ?: "",
        call.argument<String>("title") ?: "",
        call.argument<String>("text") ?: "",
        call.argument<Int>("progress") ?: -1,
    )
    result.success(null)
}
