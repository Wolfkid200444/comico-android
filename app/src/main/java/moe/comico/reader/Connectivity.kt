package moe.comico.reader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun internetAvailable(defaultValidated: Boolean, vpn: Boolean, physicalValidated: Boolean) =
    defaultValidated && (!vpn || physicalValidated)

fun hasInternet(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val active = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    val physical = manager.allNetworks.any { network ->
        manager.getNetworkCapabilities(network)?.let {
            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
    }
    return internetAvailable(active.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        active.hasTransport(NetworkCapabilities.TRANSPORT_VPN), physical)
}
