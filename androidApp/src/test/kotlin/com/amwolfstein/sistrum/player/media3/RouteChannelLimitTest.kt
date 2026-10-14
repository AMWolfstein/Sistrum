package com.amwolfstein.sistrum.player.media3

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteChannelLimitTest {
    @Test fun usesTheSmallestDeviceMaximum() = assertEquals(2, routeChannelLimit(listOf(intArrayOf(2), intArrayOf(8)), 33))
    @Test fun emptyCountsRetainSourceLayout() = assertEquals(8, routeChannelLimit(listOf(intArrayOf()), 33))
    @Test fun noDevicesRetainSourceLayout() = assertEquals(8, routeChannelLimit(emptyList(), 33))
    @Test fun oldSdkRetainsSourceLayout() = assertEquals(8, routeChannelLimit(listOf(intArrayOf(2)), 32))
    @Test fun capsAtEightChannels() = assertEquals(8, routeChannelLimit(listOf(intArrayOf(16)), 33))
}
