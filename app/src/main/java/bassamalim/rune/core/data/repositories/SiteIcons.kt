package bassamalim.rune.core.data.repositories

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import bassamalim.rune.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** A logo and the tile colour that suits it, as ARGB. */
class SiteIcon(val bitmap: Bitmap, val background: Int)

/** An installed app that can be linked to a site. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * How each site looks: a logo and a name to show. Both come from an app linked to the site, or
 * else from the site itself (`/favicon.ico` and the homepage), downloaded once and kept. Only the
 * site is asked, never a third-party service. Display only: the stored name, which the password
 * is made from, never changes.
 */
@Singleton
class SiteIcons @Inject constructor(
    private val application: Application,
    private val sitesRepository: SitesRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val dir = File(application.cacheDir, "icons")
    private val namesDir = File(application.cacheDir, "names")

    suspend fun forSite(site: String): SiteIcon? = withContext(dispatcher) {
        val apps = sitesRepository.sites().orEmpty().filter { it.site == site }.flatMap { it.apps }

        (apps.firstNotNullOfOrNull(::appIcon) ?: favicon(site))?.let(::withTile)
    }

    /** "GitHub" for github.com, "WhatsApp" for a site linked to it, "My Bank" for "my bank". */
    suspend fun name(site: String): String = withContext(dispatcher) {
        val apps = sitesRepository.sites().orEmpty().filter { it.site == site }.flatMap { it.apps }

        apps.firstNotNullOfOrNull(::appLabel) ?: fetchedName(site) ?: titleCase(site)
    }

    suspend fun forApp(packageName: String): SiteIcon? =
        withContext(dispatcher) { appIcon(packageName)?.let(::withTile) }

    private fun withTile(bitmap: Bitmap): SiteIcon {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return SiteIcon(bitmap, tileColor(pixels, bitmap.width, bitmap.height))
    }

    /** Apps with a launcher entry, the ones a person would think of as "an app". */
    suspend fun installedApps(): List<InstalledApp> = withContext(dispatcher) {
        val pm = application.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != application.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun appLabel(packageName: String): String? = runCatching {
        val pm = application.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    /** Null when the app isn't installed (any more) or isn't visible to Rune. */
    private fun appIcon(packageName: String): Bitmap? = runCatching {
        application.packageManager.getApplicationIcon(packageName).toBitmap(SIZE, SIZE)
    }.getOrNull()

    private fun favicon(site: String): Bitmap? =
        cached(site, dir, "favicon.ico", MAX_BYTES) { bytes ->
            decode(bytes)?.let { bitmap ->
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    .toByteArray()
            }
        }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    private fun fetchedName(site: String): String? =
        cached(site, namesDir, "", MAX_PAGE_BYTES) { siteTitle(it.decodeToString())?.encodeToByteArray() }
            ?.decodeToString()

    /**
     * What `https://<site>/<path>` gave, reduced by [keep] and cached in [folder]. Null when the
     * site is a plain name, or has nothing usable (remembered as an empty file), or the network
     * failed (not remembered, so it is tried again next time).
     */
    private fun cached(
        site: String,
        folder: File,
        path: String,
        limit: Int,
        keep: (ByteArray) -> ByteArray?
    ): ByteArray? {
        val host = site.substringBefore('/')
        // "bank" or "my laptop" is a name, not an address: nothing to ask.
        if ('.' !in host || ' ' in host) return null

        val file = File(folder, URLEncoder.encode(host, "UTF-8"))
        if (!file.exists()) {
            val body = download("https://$host/$path", limit) ?: return null
            folder.mkdirs()
            val partial = File(folder, "${file.name}.part")
            partial.writeBytes(body.takeIf { it.isNotEmpty() }?.let(keep) ?: ByteArray(0))
            partial.renameTo(file)
        }
        return file.readBytes().takeIf { it.isNotEmpty() }
    }

    /** Empty when the site answered with an error; null when the network failed. */
    private fun download(url: String, limit: Int): ByteArray? {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return null
        }

        return try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { it.readAtMost(limit) }
            } else ByteArray(0)
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** A small square bitmap, or null for anything that isn't a sane image. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_SIDE || bounds.outHeight !in 1..MAX_SIDE) return null

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.scale(SIZE, SIZE)
    }

    private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val n = read(buffer, read, limit - read)
            if (n < 0) break
            read += n
        }
        return buffer.copyOf(read)
    }

    companion object {
        /** Light, for a logo too dark to see on the dark surfaces. */
        private const val LIGHT_TILE = 0xFFF6F1E9.toInt()

        /**
         * The tile behind a logo, from its ARGB pixels. A logo with its own solid background
         * (most app icons, Wikipedia's white square) gets that colour, so the two read as one.
         * A transparent one gets a light tile when it is dark, otherwise a faint wash of its own
         * colour, like the letter tiles' amber wash.
         */
        fun tileColor(pixels: IntArray, width: Int, height: Int): Int {
            fun alpha(p: Int) = p ushr 24
            fun average(of: List<Int>): Triple<Int, Int, Int> = Triple(
                of.sumOf { (it shr 16) and 0xFF } / of.size,
                of.sumOf { (it shr 8) and 0xFF } / of.size,
                of.sumOf { it and 0xFF } / of.size
            )
            fun argb(a: Int, rgb: Triple<Int, Int, Int>) =
                (a shl 24) or (rgb.first shl 16) or (rgb.second shl 8) or rgb.third

            val edge = pixels.filterIndexed { i, _ ->
                val x = i % width
                val y = i / width
                x == 0 || y == 0 || x == width - 1 || y == height - 1
            }
            val solidEdge = edge.filter { alpha(it) > 200 }
            if (solidEdge.size >= edge.size * 0.6) return argb(0xFF, average(solidEdge))

            val solid = pixels.filter { alpha(it) > 128 }
            if (solid.isEmpty()) return LIGHT_TILE
            val colour = average(solid)
            val luminance =
                (0.2126 * colour.first + 0.7152 * colour.second + 0.0722 * colour.third) / 255
            return if (luminance < 0.4) LIGHT_TILE else argb(0x29, colour)
        }

        /**
         * The name a homepage gives itself: `og:site_name`, else `application-name`, else the
         * title up to its first separator ("GitHub · Build and ship software" is "GitHub").
         */
        fun siteTitle(html: String): String? {
            val ignoreCase = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            fun meta(key: String): String? =
                Regex("<meta\\b[^>]*\\b(?:property|name)=[\"']$key[\"'][^>]*>", ignoreCase)
                    .find(html)?.value
                    ?.let { Regex("\\bcontent=(?:\"([^\"]*)\"|'([^']*)')", ignoreCase).find(it) }
                    ?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
                    ?.let(::decodeEntities)

            val title = Regex("<title[^>]*>(.*?)</title>", ignoreCase)
                .find(html)?.groupValues?.get(1)
                ?.let { decodeEntities(it).split(" | ", " - ", " · ", " — ", " – ", ": ", ". ").first() }
                // A bot check or error page names itself, not the site.
                ?.takeIf { t -> BOT_WALLS.none { t.trim().startsWith(it, ignoreCase = true) } }

            return (meta("og:site_name") ?: meta("application-name") ?: title)
                ?.replace(Regex("\\s+"), " ")?.trim()
                ?.takeIf { it.length in 1..40 }
        }

        private fun decodeEntities(text: String): String = text
            .replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
                m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
                    ?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")

        /** "my bank" is "My Bank"; an address like "github.com" stays as it is. */
        fun titleCase(site: String): String =
            if ('.' in site) site
            else site.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

        private val BOT_WALLS =
            listOf("Just a moment", "Attention Required", "Access Denied", "Error", "403", "404")

        private const val SIZE = 96
        private const val MAX_SIDE = 512
        private const val MAX_BYTES = 512 * 1024
        private const val MAX_PAGE_BYTES = 256 * 1024
        private const val TIMEOUT_MILLIS = 5_000
    }
}
