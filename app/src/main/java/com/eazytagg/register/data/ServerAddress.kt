package com.eazytagg.register.data

import java.net.URI

/** Staff enter the computer's IPv4 address; deployment paths stay inside the app. */
object ServerAddress {
    fun ip(input: String): String? {
        val text = input.trim()
        if (!Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$").matches(text)) return null
        val parts = text.split('.').map { it.toInt() }
        if (parts.any { it !in 0..255 } || parts.first() !in 1..223) return null
        return parts.joinToString(".")
    }

    fun displayIp(savedBase: String): String = try {
        ip(URI(savedBase).host.orEmpty()).orEmpty()
    } catch (_: Exception) { "" }

    fun candidates(input: String, savedBase: String = ""): List<String> {
        val host = ip(input) ?: return emptyList()
        val origin = "http://$host/"
        val bases = listOf(origin + "eazytagg_test/", origin, origin + "PDynamics/")
        // Reuse a previously verified deployment path only on the same computer.
        val preferred = bases.firstOrNull { it == savedBase }
        return if (preferred == null) bases else listOf(preferred) + bases.filter { it != preferred }
    }
}
