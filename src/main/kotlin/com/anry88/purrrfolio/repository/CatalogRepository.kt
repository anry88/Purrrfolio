package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.Card
import com.anry88.purrrfolio.models.Collection
import com.anry88.purrrfolio.models.Rarity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class CatalogRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rarityRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Rarity(
            id = rs.getLong("id"),
            code = rs.getString("code"),
            probabilityWeight = rs.getInt("probability_weight"),
            frameTheme = rs.getString("frame_theme"),
            sortOrder = rs.getInt("sort_order"),
            active = rs.getBoolean("active"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    private val collectionRowMapper = RowMapper { rs: ResultSet, _: Int ->
        val localizedNameJson = rs.getString("localized_name")
        val localizedName = parseJsonbToMap(localizedNameJson)
        
        Collection(
            id = rs.getLong("id"),
            code = rs.getString("code"),
            localizedName = localizedName,
            sortOrder = rs.getInt("sort_order"),
            active = rs.getBoolean("active"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    private val cardRowMapper = RowMapper { rs: ResultSet, _: Int ->
        val metadataJson = rs.getString("metadata")
        val metadata = if (metadataJson != null) parseJsonbToMap(metadataJson) else null
        
        Card(
            id = rs.getLong("id"),
            collectionId = rs.getLong("collection_id"),
            englishTitle = rs.getString("english_title"),
            rarityId = rs.getLong("rarity_id"),
            imageRef = rs.getString("image_ref"),
            sortOrder = rs.getInt("sort_order"),
            active = rs.getBoolean("active"),
            special = rs.getBoolean("special"),
            limited = rs.getBoolean("limited"),
            event = rs.getString("event"),
            source = rs.getString("source"),
            metadata = metadata,
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    fun findAllActiveRarities(): List<Rarity> {
        val sql = "SELECT * FROM rarities WHERE active = true ORDER BY sort_order"
        return jdbcTemplate.query(sql, rarityRowMapper)
    }

    fun findRarityByCode(code: String): Rarity? {
        val sql = "SELECT * FROM rarities WHERE code = ? AND active = true"
        val results = jdbcTemplate.query(sql, rarityRowMapper, code)
        return results.firstOrNull()
    }

    fun findAllActiveCollections(): List<Collection> {
        val sql = "SELECT * FROM collections WHERE active = true ORDER BY sort_order"
        return jdbcTemplate.query(sql, collectionRowMapper)
    }

    fun findCollectionByCode(code: String): Collection? {
        val sql = "SELECT * FROM collections WHERE code = ? AND active = true"
        val results = jdbcTemplate.query(sql, collectionRowMapper, code)
        return results.firstOrNull()
    }

    fun findActiveCardsByCollection(collectionId: Long): List<Card> {
        val sql = "SELECT * FROM cards WHERE collection_id = ? AND active = true ORDER BY sort_order"
        return jdbcTemplate.query(sql, cardRowMapper, collectionId)
    }

    fun findActiveCardById(cardId: Long): Card? {
        val sql = "SELECT * FROM cards WHERE id = ? AND active = true"
        val results = jdbcTemplate.query(sql, cardRowMapper, cardId)
        return results.firstOrNull()
    }

    fun findAllActiveCards(): List<Card> {
        val sql = "SELECT * FROM cards WHERE active = true ORDER BY collection_id, sort_order"
        return jdbcTemplate.query(sql, cardRowMapper)
    }

    private fun parseJsonbToMap(json: String): Map<String, String> {
        // Simple JSON parser for {"ru": "...", "en": "..."} format
        val result = mutableMapOf<String, String>()
        val cleanJson = json.trim().removeSurrounding("{", "}")
        cleanJson.split(",").forEach { pair ->
            val (key, value) = pair.split(":").map { it.trim().removeSurrounding("\"") }
            result[key] = value
        }
        return result
    }
}
