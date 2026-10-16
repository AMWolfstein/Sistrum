package com.amwolfstein.sistrum.integrations.net

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

interface NetworkPolicy {
    fun isUnmetered(): Boolean
    fun isOnline(): Boolean
}

class AndroidNetworkPolicy(private val connectivityManager: ConnectivityManager) : NetworkPolicy {
    override fun isOnline(): Boolean = activeCapabilities()?.isOnline() == true

    override fun isUnmetered(): Boolean = activeCapabilities()?.let {
        it.isOnline() && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    } == true

    private fun activeCapabilities(): NetworkCapabilities? = connectivityManager.activeNetwork?.let {
        connectivityManager.getNetworkCapabilities(it)
    }

    private fun NetworkCapabilities.isOnline(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
