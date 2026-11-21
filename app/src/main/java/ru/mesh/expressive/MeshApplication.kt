package ru.mesh.expressive

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

class MeshApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        bindPhysicalNetwork(this)
    }

    companion object {
        fun bindPhysicalNetwork(context: Context) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

                val physicalNetwork = cm.allNetworks.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }

                if (physicalNetwork != null) {
                    cm.bindProcessToNetwork(physicalNetwork)
                    Log.i("MeshApp", "Process bound to direct physical network: $physicalNetwork")
                }

                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build()

                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        try {
                            cm.bindProcessToNetwork(network)
                            Log.i("MeshApp", "Switched process binding to physical network: $network")
                        } catch (e: Exception) {
                            Log.w("MeshApp", "Failed to bind to network $network", e)
                        }
                    }

                    override fun onLost(network: Network) {
                        try {
                            val fallback = cm.allNetworks.firstOrNull { net ->
                                val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
                                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                            }
                            cm.bindProcessToNetwork(fallback)
                        } catch (e: Exception) {
                            Log.w("MeshApp", "Failed fallback network bind", e)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w("MeshApp", "Error binding to physical network", e)
            }
        }
    }
}
