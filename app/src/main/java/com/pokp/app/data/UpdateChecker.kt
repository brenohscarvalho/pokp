package com.pokp.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Checks GitHub Releases for a newer version of the app. */
object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/brenohscarvalho/pokp/releases/latest"

    data class UpdateInfo(val version: String, val pageUrl: String, val apkUrl: String?)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(client: OkHttpClient, currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(LATEST_RELEASE_URL)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
                    val page = obj["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val apk = obj["assets"]?.jsonArray
                        ?.mapNotNull { it.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull }
                        ?.firstOrNull { it.endsWith(".apk") }
                    if (isNewer(tag.removePrefix("v"), currentVersion)) {
                        UpdateInfo(tag, page, apk)
                    } else {
                        null
                    }
                }
            }.getOrNull()
        }

    /** Compares dotted version strings; true if [latest] > [current]. */
    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
