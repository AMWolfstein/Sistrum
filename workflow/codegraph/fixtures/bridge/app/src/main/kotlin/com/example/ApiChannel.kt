package com.example

import io.flutter.plugin.common.MethodChannel

class ApiChannel(messenger: Any) {
    private val api = MethodChannel(messenger, "dev.example/api")
}
