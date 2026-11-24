package com.civamel.ultrahdwallpaper

import org.json.JSONObject
import java.net.URL

/**
 * Shared defensive parser for bundled, cached, and remote wallpaper catalogs.
 * Invalid rows are skipped; a catalog with no usable rows is rejected.
 */
internal object CatalogValidator {
    private const val MAX_ITEMS = 10000

    fun parse(body: String): List<JSONObject> {
        val root = JSONObject(body)
        val version = root.optInt("version", 1)
        require(version in 1..2) { "Unsupported catalog version: $version" }

        val source = root.optJSONArray("wallpapers")
            ?: throw IllegalArgumentException("Catalog has no wallpapers array")
        require(source.length() in 1..MAX_ITEMS) { "Catalog item count is invalid" }

        val result = ArrayList<JSONObject>(source.length())
        val seenIds = HashSet<String>()
        val seenContent = HashSet<String>()

        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            val thumbnail = item.optString("thumbnail").trim()
            val image = item.optString("image").trim()
            if (title.isEmpty() || !isHttpsUrl(thumbnail) || !isHttpsUrl(image)) continue

            val id = item.optString("id").trim()
            if (id.isNotEmpty() && !seenIds.add(id)) continue

            val contentKey = title.lowercase() + "|" + image
            if (!seenContent.add(contentKey)) continue

            result.add(item)
        }

        require(result.isNotEmpty()) { "Catalog contains no valid wallpaper entries" }
        return result
    }

    private fun isHttpsUrl(value: String): Boolean {
        if (value.isBlank()) return false
        return try {
            val parsed = URL(value)
            parsed.protocol.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
