package com.civamel.ultrahdwallpaper

import org.json.JSONObject

/**
 * Validates the lightweight index that points to independently downloadable catalog pages.
 * Page paths are repository-relative; the app constructs URLs from its trusted catalog base.
 */
internal object CatalogManifest {
    data class Shard(val path: String, val itemCount: Int)

    data class Index(
        val catalogVersion: Int,
        val totalCount: Int,
        val pageSize: Int,
        val shards: List<Shard>
    )

    private const val MAX_TOTAL_ITEMS = 10000
    private const val MAX_SHARDS = 200
    private const val MAX_ITEMS_PER_SHARD = 200
    private val safePath = Regex("pages/[a-z0-9-]+\\.json")

    fun parse(body: String): Index {
        val root = JSONObject(body)
        require(root.optInt("version", -1) == 1) { "Unsupported catalog manifest version" }

        val catalogVersion = root.optInt("catalogVersion", -1)
        require(catalogVersion in 1..2) { "Unsupported catalog data version" }

        val totalCount = root.optInt("totalCount", -1)
        require(totalCount in 1..MAX_TOTAL_ITEMS) { "Invalid catalog total count" }

        val pageSize = root.optInt("pageSize", -1)
        require(pageSize in 1..MAX_ITEMS_PER_SHARD) { "Invalid catalog page size" }

        val source = root.optJSONArray("shards")
            ?: throw IllegalArgumentException("Manifest has no shards array")
        require(source.length() in 1..MAX_SHARDS) { "Invalid shard count" }

        val shards = ArrayList<Shard>(source.length())
        val seenPaths = HashSet<String>()
        var declaredTotal = 0

        for (index in 0 until source.length()) {
            val entry = source.optJSONObject(index) ?: continue
            val path = entry.optString("path").trim()
            val count = entry.optInt("count", -1)
            if (!safePath.matches(path)) continue
            if (count !in 1..MAX_ITEMS_PER_SHARD) continue
            if (!seenPaths.add(path)) continue
            shards.add(Shard(path, count))
            declaredTotal += count
        }

        require(shards.isNotEmpty()) { "Manifest contains no valid shards" }
        require(declaredTotal == totalCount) { "Shard counts do not match totalCount" }
        return Index(catalogVersion, totalCount, pageSize, shards)
    }

    fun parseShard(body: String, shard: Shard): List<JSONObject> {
        val items = CatalogValidator.parse(body)
        require(items.size == shard.itemCount) { "Shard item count does not match manifest" }
        return items
    }
}
