package app.foqos.android.data.db

import androidx.room.TypeConverter
import app.foqos.android.data.model.PhysicalUnblockItem
import app.foqos.android.data.model.ScheduleConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Converters {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    @JvmStatic
    fun stringListToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    @JvmStatic
    fun jsonToStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    @JvmStatic
    fun unblockItemsToJson(value: List<PhysicalUnblockItem>): String = json.encodeToString(value)

    @TypeConverter
    @JvmStatic
    fun jsonToUnblockItems(value: String): List<PhysicalUnblockItem> =
        runCatching { json.decodeFromString<List<PhysicalUnblockItem>>(value) }
            .getOrDefault(emptyList())

    @TypeConverter
    @JvmStatic
    fun scheduleToJson(value: ScheduleConfig?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    @JvmStatic
    fun jsonToSchedule(value: String?): ScheduleConfig? =
        value?.let { runCatching { json.decodeFromString<ScheduleConfig>(it) }.getOrNull() }
}
