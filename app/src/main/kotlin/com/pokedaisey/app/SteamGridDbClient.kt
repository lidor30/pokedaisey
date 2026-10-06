package com.pokedaisey.app

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal SteamGridDB REST client for the Library's fetched-cover-art
 * feature — no HTTP/JSON library dependency, just `HttpURLConnection` +
 * `org.json` (both already part of the Android platform). Every call here
 * is blocking network I/O; callers must run this off the main thread.
 *
 * Fetches SteamGridDB's **icons** (square, app-icon-style images), not its
 * "grids" (portrait box-art posters) — a square icon fits this app's
 * list/grid tiles directly with no stretching or cropping, unlike a
 * portrait poster forced into a compact row.
 *
 * The API key always comes from [Prefs.steamGridDbApiKey] (Settings > Cover
 * Art) — this client never hardcodes or bundles one. By design, no images
 * ship in the APK either; everything here is fetched live, once per ROM,
 * and cached under `files/covers/` (see LibraryActivity's coverFile()).
 */
object SteamGridDbClient {
    private const val TAG = "pokedaisey/steamgriddb"
    private const val API = "https://www.steamgriddb.com/api/v2"

    /** Icons by this SteamGridDB uploader win over higher-voted ones from anyone
     * else - the user's pick of a consistent style (steamgriddb.com/profile/<id>/icons). */
    const val PREFERRED_AUTHOR_STEAM64 = "76561198034412095"

    /** One icon on SteamGridDB; [thumb] is its small preview. */
    data class Icon(val id: Int, val url: String, val thumb: String, val authorSteam64: String?, val authorName: String?) {
        val preferred get() = authorSteam64 == PREFERRED_AUTHOR_STEAM64
    }

    /** A SteamGridDB game entry (search result). */
    data class GameHit(val id: Int, val name: String)

    /** Fetches [game]'s default icon ([pickDefault]) and writes it to [outFile].
     * Returns true on success; on failure [outFile] is left as it was. */
    fun fetchCover(apiKey: String, game: SteamGridDbGames.Game, outFile: File): Boolean {
        val icon = runCatching { pickDefault(listIcons(apiKey, game.id)) }.getOrElse { t ->
            Log.w(TAG, "icon lookup failed for ${game.displayName} (${game.id})", t)
            null
        } ?: return false
        return download(icon.url, outFile)
    }

    /** The preferred uploader's best-voted icon, else the best-voted of all. */
    fun pickDefault(icons: List<Icon>): Icon? = icons.firstOrNull { it.preferred } ?: icons.firstOrNull()

    /**
     * Every icon of [gameId], highest-voted first (the API's own order), with
     * the preferred uploader's moved to the front. Pages through the results
     * (the API returns them a page at a time) up to [maxPages], so a preferred
     * icon that isn't among the top-voted is still found. Throws on I/O errors.
     */
    fun listIcons(apiKey: String, gameId: Int, maxPages: Int = 6): List<Icon> {
        val all = ArrayList<Icon>()
        for (page in 0 until maxPages) {
            val json = getJson("$API/icons/game/$gameId?page=$page", apiKey)
            if (!json.optBoolean("success", false)) break
            val data = json.optJSONArray("data") ?: break
            if (data.length() == 0) break
            val before = all.size
            for (i in 0 until data.length()) {
                val o = data.getJSONObject(i)
                val url = o.optString("url").takeIf { it.isNotBlank() } ?: continue
                if (all.any { it.id == o.optInt("id") }) continue
                val author = o.optJSONObject("author")
                all += Icon(
                    id = o.optInt("id"),
                    url = url,
                    thumb = o.optString("thumb").ifBlank { url },
                    authorSteam64 = author?.optString("steam64")?.takeIf { it.isNotBlank() },
                    authorName = author?.optString("name")?.takeIf { it.isNotBlank() },
                )
            }
            // Done once everything's in - or a page added nothing new (an
            // endpoint that ignores `page` would repeat page 0 forever).
            val total = json.optInt("total", -1)
            if (total in 0..all.size || all.size == before) break
        }
        return all.filter { it.preferred } + all.filterNot { it.preferred }
    }

    /** SteamGridDB's game search, best match first. Throws on I/O errors. */
    fun searchGames(apiKey: String, term: String): List<GameHit> {
        val q = URLEncoder.encode(term.trim(), "UTF-8").replace("+", "%20")
        if (q.isEmpty()) return emptyList()
        val json = getJson("$API/search/autocomplete/$q", apiKey)
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).map { data.getJSONObject(it) }
            .map { GameHit(it.optInt("id"), it.optString("name")) }
            .filter { it.id != 0 }
    }

    /** Downloads [url] to [outFile] through a temp file + rename, so a failed
     * or half-finished download never replaces what's there. */
    fun download(url: String, outFile: File): Boolean = runCatching {
        outFile.parentFile?.mkdirs()
        val tmp = File(outFile.parentFile, "${outFile.name}.tmp")
        openConnection(url, bearerToken = null).inputStream.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        check(tmp.length() > 0 && tmp.renameTo(outFile))
    }.onFailure { t -> Log.w(TAG, "icon download failed: $url", t) }.isSuccess

    /** Raw bytes of [url] (a thumbnail for the cover picker), or null. */
    fun fetchBytes(url: String): ByteArray? = runCatching {
        openConnection(url, bearerToken = null).inputStream.use { it.readBytes() }
    }.getOrNull()

    private fun getJson(url: String, apiKey: String): JSONObject =
        JSONObject(openConnection(url, apiKey).inputStream.use { it.readBytes().toString(Charsets.UTF_8) })

    private fun openConnection(url: String, bearerToken: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        if (bearerToken != null) conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        return conn
    }
}
