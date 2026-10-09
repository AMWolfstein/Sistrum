// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/tables_decorr.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
// Tables derived from FFmpeg (LGPL-2.1-or-later) via WaxFlow; see THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.codec.wmapro

internal val defaultDecorrelation = makedefaultDecorrelation()
private fun makedefaultDecorrelation() = arrayOf(floatArrayOf(),floatArrayOf(1.0f),floatArrayOf(0.707031f,-0.707031f,0.707031f,0.707031f),floatArrayOf(0.578125f,0.707031f,0.410156f,0.578125f,-0.707031f,0.410156f,0.578125f,0.0f,-0.816406f),floatArrayOf(0.5f,0.652344f,0.5f,0.269531f,0.5f,0.269531f,-0.5f,-0.652344f,0.5f,-0.269531f,-0.5f,0.652344f,0.5f,-0.652344f,0.5f,-0.269531f),floatArrayOf(0.445312f,0.601562f,0.511719f,0.371094f,0.195312f,0.445312f,0.371094f,-0.195312f,-0.601562f,-0.511719f,0.445312f,0.0f,-0.632812f,0.0f,0.632812f,0.445312f,-0.371094f,-0.195312f,0.601562f,-0.511719f,0.445312f,-0.601562f,0.511719f,-0.371094f,0.195312f),floatArrayOf(0.410156f,0.558594f,0.5f,0.410156f,0.289062f,0.148438f,0.410156f,0.410156f,0.0f,-0.410156f,-0.578125f,-0.410156f,0.410156f,0.148438f,-0.5f,-0.410156f,0.289062f,0.558594f,0.410156f,-0.148438f,-0.5f,0.410156f,0.289062f,-0.558594f,0.410156f,-0.410156f,0.0f,0.410156f,-0.578125f,0.410156f,0.410156f,-0.558594f,0.5f,-0.410156f,0.289062f,-0.148438f))
