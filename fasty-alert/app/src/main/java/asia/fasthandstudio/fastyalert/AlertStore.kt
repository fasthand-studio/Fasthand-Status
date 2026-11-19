package asia.fasthandstudio.fastyalert

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.TimeUnit

data class AlertRecord(
    val id: String,
    val title: String,
    val body: String,
    val entityType: String,
    val entityId: String,
    val event: String,
    val severity: String,
    val receivedAt: Long,
)

class AlertStore(context: Context) : SQLiteOpenHelper(context, "fasty_alerts.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE alert_history (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL DEFAULT '',
                event TEXT NOT NULL,
                severity TEXT NOT NULL,
                received_at INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX alert_history_received_idx ON alert_history(received_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE alert_history ADD COLUMN entity_id TEXT NOT NULL DEFAULT ''")
    }

    @Synchronized
    fun save(record: AlertRecord, ttlDays: Int): Boolean {
        purgeExpired(ttlDays)
        val values = ContentValues().apply {
            put("id", record.id)
            put("title", record.title)
            put("body", record.body)
            put("entity_type", record.entityType)
            put("entity_id", record.entityId)
            put("event", record.event)
            put("severity", record.severity)
            put("received_at", record.receivedAt)
        }
        return writableDatabase.insertWithOnConflict("alert_history", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    @Synchronized
    fun getAlerts(ttlDays: Int): List<AlertRecord> {
        purgeExpired(ttlDays)
        val alerts = mutableListOf<AlertRecord>()
        readableDatabase.query(
            "alert_history",
            arrayOf("id", "title", "body", "entity_type", "entity_id", "event", "severity", "received_at"),
            null,
            null,
            null,
            null,
            "received_at DESC",
            "250",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                alerts += AlertRecord(
                    id = cursor.getString(0),
                    title = cursor.getString(1),
                    body = cursor.getString(2),
                    entityType = cursor.getString(3),
                    entityId = cursor.getString(4),
                    event = cursor.getString(5),
                    severity = cursor.getString(6),
                    receivedAt = cursor.getLong(7),
                )
            }
        }
        return alerts
    }

    @Synchronized
    fun purgeExpired(ttlDays: Int) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays.toLong())
        writableDatabase.delete("alert_history", "received_at < ?", arrayOf(cutoff.toString()))
    }
}

object AlertRetention {
    val choices = listOf(7, 30, 90, 365)
    private const val preferences = "fasty_alert"
    private const val key = "ttl_days"

    fun get(context: Context): Int = context.getSharedPreferences(preferences, Context.MODE_PRIVATE)
        .getInt(key, 30).coerceIn(choices.first(), choices.last())

    fun set(context: Context, days: Int) {
        if (days in choices) context.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit().putInt(key, days).apply()
        AlertStore(context).use { it.purgeExpired(get(context)) }
    }
}
