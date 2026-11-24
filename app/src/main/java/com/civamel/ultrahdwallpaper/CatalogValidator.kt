package com.civamel.ultrahdwallpaper

import org.json.JSONObject
import java.net.URL

/**
 * Shared defensive parser for bundled, cached, and remote wallpaper catalogs.
 * Invalid rows are skipped; a catalog with no usable rows is rejected.
 */
internal object CatalogValidator {
    private const val MAX_ITEMS = 10000
    private const val MAX_TAGS_PER_ITEM = 40
    private const val MAX_TAG_LENGTH = 48
    private val supportedTypes = setOf("wallpaper", "live", "parallax", "double", "charging")

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
            val category = item.optString("category").trim()
            val thumbnail = item.optString("thumbnail").trim()
            val image = item.optString("image").trim()
            val id = item.optString("id").trim()
            val type = item.optString("type", "wallpaper").trim().lowercase()

            if (title.isEmpty() || !isHttpsUrl(thumbnail) || !isHttpsUrl(image)) continue
            if (version >= 2) {
                if (!isValidId(id) || category.isEmpty()) continue
                if (item.optString("provider").trim().isEmpty() ||
                    item.optString("license").trim().isEmpty()) continue
                if (type !in supportedTypes) continue
                val tags = item.optJSONArray("tags") ?: continue
                if (tags.length() > MAX_TAGS_PER_ITEM) continue
                var tagsValid = true
                for (tagIndex in 0 until tags.length()) {
                    val tag = tags.opt(tagIndex)
                    if (tag !is String || tag.trim().isEmpty() || tag.length > MAX_TAG_LENGTH) {
                        tagsValid = false
                        break
                    }
                }
                if (!tagsValid) continue
            }

            if (id.isNotEmpty() && !seenIds.add(id)) continue

            val contentKey = title.lowercase() + "|" + image
            if (!seenContent.add(contentKey)) continue

            result.add(item)
        }

        require(result.isNotEmpty()) { "Catalog contains no valid wallpaper entries" }
        return result
    }

    private fun isValidId(value: String): Boolean =
        value.length in 2..80 && value.matches(Regex("[a-z0-9][a-z0-9-]*"))

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
