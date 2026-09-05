package com.sdau.campuskit

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class DormRechargeHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val location: String,
    val campusCode: String,
    val buildingCode: String,
    val roomCode: String,
    val equipmentCode: String,
    val amount: Double,
    val createdAt: Long,
    val beforeKwh: Double?,
    val afterKwh: Double? = null,
    val addedKwh: Double? = null
)

internal class DormRechargeHistoryStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("dorm_recharge_history", Context.MODE_PRIVATE)
    private val fileName = "WeSDAU_dorm_recharge_history.json"
    private val relativePath = "Download/WeSDAU/"

    fun load(): List<DormRechargeHistoryEntry> {
        val publicJson = readPublicCopy()
        val privateJson = preferences.getString("entries", null)
        val source = publicJson?.takeIf { it.isNotBlank() } ?: privateJson.orEmpty()
        val entries = parse(source)
        if (publicJson != null && publicJson != privateJson) {
            preferences.edit().putString("entries", publicJson).apply()
        }
        return entries.sortedByDescending { it.createdAt }
    }

    fun save(entries: List<DormRechargeHistoryEntry>) {
        val json = JSONArray().apply {
            entries.sortedByDescending { it.createdAt }.take(200).forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("location", entry.location)
                    put("campusCode", entry.campusCode)
                    put("buildingCode", entry.buildingCode)
                    put("roomCode", entry.roomCode)
                    put("equipmentCode", entry.equipmentCode)
                    put("amount", entry.amount)
                    put("createdAt", entry.createdAt)
                    put("beforeKwh", entry.beforeKwh ?: JSONObject.NULL)
                    put("afterKwh", entry.afterKwh ?: JSONObject.NULL)
                    put("addedKwh", entry.addedKwh ?: JSONObject.NULL)
                })
            }
        }.toString()
        preferences.edit().putString("entries", json).apply()
        writePublicCopy(json)
    }

    private fun parse(json: String): List<DormRechargeHistoryEntry> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    DormRechargeHistoryEntry(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        location = item.optString("location"),
                        campusCode = item.optString("campusCode"),
                        buildingCode = item.optString("buildingCode"),
                        roomCode = item.optString("roomCode"),
                        equipmentCode = item.optString("equipmentCode"),
                        amount = item.optDouble("amount", 0.0),
                        createdAt = item.optLong("createdAt", 0L),
                        beforeKwh = item.takeUnless { it.isNull("beforeKwh") }?.optDouble("beforeKwh"),
                        afterKwh = item.takeUnless { it.isNull("afterKwh") }?.optDouble("afterKwh"),
                        addedKwh = item.takeUnless { it.isNull("addedKwh") }?.optDouble("addedKwh")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun readPublicCopy(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        return runCatching {
            resolver.query(collection, projection, selection, arrayOf(fileName, relativePath), null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(0)
                resolver.openInputStream(android.content.ContentUris.withAppendedId(collection, id))
                    ?.bufferedReader()?.use { it.readText() }
            }
        }.getOrNull()
    }

    private fun writePublicCopy(json: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        runCatching {
            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                arrayOf(fileName, relativePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
            }
            val uri = existing ?: resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
            ) ?: return
            resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
        }
    }
}
