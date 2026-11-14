package com.amwolfstein.sistrum

import java.io.File

/**
 * Creates an install-scoped marker in noBackupFilesDir. A platform restore
 * can bring SharedPreferences back after reinstall, but this marker is never
 * backed up. Package timestamps distinguish that case from the first app
 * update after this marker was introduced, so existing users are not sent
 * through onboarding again.
 */
internal fun MainActivity.ensureInstallMarker(): Map<String, Any> {
    val marker = File(noBackupFilesDir, "installation_state_v1")
    val markerExisted = marker.isFile
    val packageInfo = @Suppress("DEPRECATION")
    packageManager.getPackageInfo(packageName, 0)
    val installTimestampDelta = kotlin.math.abs(
        packageInfo.lastUpdateTime - packageInfo.firstInstallTime
    )
    val looksLikeFreshPackageInstall = installTimestampDelta <= 10_000L

    var markerCreated = markerExisted
    if (!markerExisted) {
        markerCreated = try {
            marker.parentFile?.mkdirs()
            marker.writeText(
                "created_at=${System.currentTimeMillis()}\n" +
                    "version_code=${BuildConfig.VERSION_CODE}\n"
            )
            true
        } catch (e: Exception) {
            android.util.Log.w(
                "Sistrum",
                "Failed to create installation marker: ${e.message}"
            )
            false
        }
    }

    return mapOf(
        "marker_existed" to markerExisted,
        "marker_created" to markerCreated,
        "fresh_package_install" to looksLikeFreshPackageInstall,
    )
}

