package ourmeal.prices

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/** Discovered metadata must never turn the browser into a client of local network services. */
internal fun publicWebsite(raw: String): String? {
  val trimmed = raw.trim()
  if (trimmed.contains("://") && !trimmed.startsWith("https://")) return null
  val url = (if (trimmed.startsWith("https://")) trimmed else "https://$trimmed").toHttpUrlOrNull() ?: return null
  if (url.scheme != "https" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
  val host = url.host.lowercase()
  if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || !host.contains('.')) return null
  if (host.any { it == ':' } || host.all { it.isDigit() || it == '.' }) return null
  return url.toString()
}

internal class PublicWebPolicy {
  private data class Decision(val allowed: Boolean, val expiresAt: Long)
  private val hosts = ConcurrentHashMap<String, Decision>()

  fun allows(raw: String): Boolean {
    val url = publicWebsite(raw)?.toHttpUrlOrNull() ?: return false
    val cached = hosts[url.host]
    if (cached != null && cached.expiresAt > System.currentTimeMillis()) return cached.allowed
    val allowed = try {
      val addresses = InetAddress.getAllByName(url.host)
      addresses.isNotEmpty() && addresses.all(::isPublicAddress)
    } catch (_: Exception) {
      false
    }
    hosts[url.host] = Decision(allowed, System.currentTimeMillis() + 60_000)
    return allowed
  }
}

internal fun isPublicAddress(address: InetAddress): Boolean {
  if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
    address.isSiteLocalAddress || address.isMulticastAddress) return false
  val bytes = address.address.map { it.toInt() and 0xff }
  return if (bytes.size == 4) {
    bytes[0] !in setOf(0, 10, 127) && bytes[0] < 224 &&
      !(bytes[0] == 100 && bytes[1] in 64..127) &&
      !(bytes[0] == 169 && bytes[1] == 254) &&
      !(bytes[0] == 172 && bytes[1] in 16..31) &&
      !(bytes[0] == 192 && bytes[1] in setOf(0, 168)) &&
      !(bytes[0] == 198 && bytes[1] in 18..19)
  } else {
    bytes[0] in 0x20..0x3f // Global unicast only; excludes unique-local IPv6.
  }
}
