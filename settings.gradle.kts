// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum; WaxLabel module attribution: Copyright (c) 2026 Cole Springer (MIT).
rootProject.name = "Sistrum"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":sharedLogic")
include(":codecs")
// WaxLabel read port (MIT, Cole Springer); see metadata/THIRD-PARTY-NOTICES.
include(":metadata")

// Independent Mood Radio PCM feature and mood maths port.
include(":analysis")

include(":dsp")
