package asia.fasthandstudio.fastyalert

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val STATUS_BASE_URL = "https://status.fasthandstudio.asia"

data class IncidentUpdateDetail(
    val status: String,
    val body: String,
    val author: String,
    val createdAt: String,
)

data class IncidentDetails(
    val id: String,
    val title: String,
    val status: String,
    val impact: String,
    val startedAt: String,
    val resolvedAt: String?,
    val updates: List<IncidentUpdateDetail>,
    val services: List<String>,
)

object IncidentApi {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    fun load(incidentId: String): IncidentDetails {
        val request = Request.Builder()
            .url("$STATUS_BASE_URL/api/alerts/incidents/$incidentId")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IOException(message?.takeIf(String::isNotBlank) ?: "Could not load the incident (${response.code}).")
            }
            val root = JSONObject(body)
            val incident = root.getJSONObject("incident")
            val updatesJson = root.optJSONArray("updates")
            val updates = buildList {
                if (updatesJson != null) for (i in 0 until updatesJson.length()) {
                    val row = updatesJson.getJSONObject(i)
                    add(
                        IncidentUpdateDetail(
                            status = row.optString("status", "update"),
                            body = row.optString("body"),
                            author = row.optString("author", "FastHand Team"),
                            createdAt = row.optString("created_at"),
                        ),
                    )
                }
            }
            val servicesJson = root.optJSONArray("services")
            val services = buildList {
                if (servicesJson != null) for (i in 0 until servicesJson.length()) {
                    servicesJson.getJSONObject(i).optString("name").takeIf(String::isNotBlank)?.let(::add)
                }
            }
            IncidentDetails(
                id = incident.getString("id"),
                title = incident.getString("title"),
                status = incident.getString("status"),
                impact = incident.getString("impact"),
                startedAt = incident.getString("started_at"),
                resolvedAt = incident.optString("resolved_at").takeIf(String::isNotBlank),
                updates = updates,
                services = services,
            )
        }
    }
}
