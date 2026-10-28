package com.example.pelletswaterheater.utils

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to compute a fingerprint of the network gateway.
 * Used to detect if we're on the same WiFi network as the heater.
 */
object NetworkFingerprint {
    private const val TAG = "NetworkFingerprint"
    private const val TIMEOUT_MS = 2000

    /**
     * Returns a fingerprint string of the gateway (e.g., MAC address or IP).
     * Returns null if unable to determine.
     */
    suspend fun getGatewayFingerprint(context: Context, client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        try {
            // Try to get gateway IP from DHCP info or route table
            val gatewayIp = getGatewayIp(context)
            gatewayIp?.let { ip ->
                // Try to fetch MAC via ARP or HTTP
                fetchMacFromArp(ip) ?: ip
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to get gateway fingerprint: $e")
            null
        }
    }

    private fun getGatewayIp(context: Context): String? {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return null
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
            val transportInfo = networkCapabilities.transportInfo
            if (transportInfo is android.net.ip.IpConfiguration) {
                // Not directly available, fall back to route
            }
            // Fallback: read from /proc/net/route
            return readGatewayFromProc()
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to get gateway IP: $e")
            return null
        }
    }

    private fun readGatewayFromProc(): String? {
        return try {
            java.io.File("/proc/net/route").readLines().drop(1).firstOrNull { line ->
                val parts = line.split("\\s+")
                parts.size >= 3 && parts[1] != "00000000" && parts[7] == "00000000"
            }?.let { line ->
                val parts = line.split("\\s+")
                val gatewayHex = parts[2]
                // Convert hex to IP (little endian)
                val bytes = hexStringToByteArray(gatewayHex)
                val ip = java.net.InetAddress.getByAddress(bytes.reversedArray())
                ip.hostAddress
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to read /proc/net/route: $e")
            null
        }
    }

    private fun fetchMacFromArp(ip: String): String? {
        // Try to read from /proc/net/arp
        return try {
            java.io.File("/proc/net/arp").readLines().drop(1).firstOrNull { line ->
                val parts = line.split("\\s+")
                parts.size >= 4 && parts[0] == ip
            }?.let { line ->
                val parts = line.split("\\s+")
                parts[3]
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to read ARP table: $e")
            null
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return data
    }
}