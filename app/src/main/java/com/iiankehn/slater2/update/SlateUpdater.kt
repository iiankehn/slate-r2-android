package com.iiankehn.slater2.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class SlateUpdate(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
)

object SlateUpdater {
    private const val LatestReleaseUrl =
        "https://api.github.com/repos/iiankehn/slate-r2-android/releases/latest"
    private const val UpdateManifestName = "slate-r2-update.json"
    private const val MaxJsonBytes = 64 * 1024
    private const val MaxApkBytes = 128L * 1024L * 1024L

    suspend fun checkForUpdate(context: Context): SlateUpdate? = withContext(Dispatchers.IO) {
        val release = JSONObject(fetchText(LatestReleaseUrl, MaxJsonBytes))
        val assets = release.getJSONArray("assets")
        val manifestUrl = (0 until assets.length())
            .asSequence()
            .map(assets::getJSONObject)
            .firstOrNull { it.optString("name") == UpdateManifestName }
            ?.getString("browser_download_url")
            ?: return@withContext null

        val update = JSONObject(fetchText(manifestUrl, MaxJsonBytes)).let { json ->
            SlateUpdate(
                versionCode = json.getLong("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                sha256 = json.getString("sha256").lowercase(),
            )
        }
        require(update.apkUrl.startsWith("https://github.com/iiankehn/slate-r2-android/releases/download/")) {
            "The update download address is not trusted."
        }
        require(update.sha256.matches(Regex("[a-f0-9]{64}"))) {
            "The update checksum is invalid."
        }

        val installedVersion = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .longVersionCode
        update.takeIf { it.versionCode > installedVersion }
    }

    fun canRequestInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    suspend fun download(context: Context, update: SlateUpdate): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "slate-r2-update.apk.part")
        val complete = File(directory, "slate-r2-update.apk")
        partial.delete()
        complete.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        openConnection(update.apkUrl).useConnection { connection ->
            val expectedSize = connection.contentLengthLong
            require(expectedSize == -1L || expectedSize in 1..MaxApkBytes) {
                "The update download has an invalid size."
            }
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MaxApkBytes) { "The update is larger than expected." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
        }

        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualHash.equals(update.sha256, ignoreCase = true)) {
            partial.delete()
            error("The downloaded update failed verification.")
        }
        require(partial.renameTo(complete)) { "Unable to prepare the downloaded update." }
        complete
    }

    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun fetchText(url: String, maxBytes: Int): String {
        return openConnection(url).useConnection { connection ->
            val declaredSize = connection.contentLengthLong
            require(declaredSize <= maxBytes || declaredSize == -1L) { "The update response is too large." }
            connection.inputStream.buffered().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (output.size() <= maxBytes) {
                    val count = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                require(bytes.size <= maxBytes) { "The update response is too large." }
                bytes.toString(Charsets.UTF_8)
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val parsed = URL(url)
        require(parsed.protocol == "https") { "Updates require a secure connection." }
        return (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Slate-R2-Updater")
            connect()
            require(this.url.protocol == "https") { "The update redirected to an insecure connection." }
            if (responseCode !in 200..299) {
                disconnect()
                error(if (responseCode == 404) "No published Slate update is available yet." else "GitHub returned update error $responseCode.")
            }
        }
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
