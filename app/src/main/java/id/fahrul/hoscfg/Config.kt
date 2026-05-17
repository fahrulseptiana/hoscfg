package id.fahrul.hoscfg

import android.content.Context
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.io.File

object Config {
    private const val TAG = "HOSCfgConfig"
    const val KEY_HIDE_SEARCH = "hide_search_bar"
    const val KEY_BG_COLOR = "drawer_bg_color"
    const val KEY_BG_ALPHA = "drawer_bg_alpha"
    const val KEY_LABEL_COLOR = "icon_label_color"
    const val KEY_HIDE_NO_SIM = "hide_no_sim"

    private val configFile = File("/data/data/id.fahrul.hoscfg/files/config.json")

    private fun readAll(): JSONObject? = try {
        if (configFile.exists()) JSONObject(configFile.readText()) else null
    } catch (_: Exception) { null }

    fun getBool(key: String, default: Boolean): Boolean =
        try { readAll()?.optBoolean(key, default) ?: default } catch (_: Exception) { default }

    fun getInt(key: String, default: Int): Int =
        try { readAll()?.optInt(key, default) ?: default } catch (_: Exception) { default }

    fun setBool(key: String, value: Boolean) { writeAll(key, value) }
    fun setInt(key: String, value: Int) { writeAll(key, value) }

    private fun writeAll(key: String, value: Any) {
        val j = try {
            if (configFile.exists()) JSONObject(configFile.readText()) else JSONObject()
        } catch (_: Exception) { JSONObject() }
        when (value) { is Boolean -> j.put(key, value); is Int -> j.put(key, value) }
        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(j.toString())
            configFile.setReadable(true, false)
        } catch (e: Exception) { Log.e(TAG, "file write failed", e) }

        // Sync to remote prefs so hooks see the change
        try {
            val svc = App.xposedService ?: return
            val remote = svc.getRemotePreferences("hoscfg_config")
            when (value) { is Boolean -> remote.edit().putBoolean(key, value).apply(); is Int -> remote.edit().putInt(key, value).apply() }
            Log.i(TAG, "synced: $key=$value")
        } catch (e: Exception) { Log.e(TAG, "remote sync failed", e) }
    }

    /** Sync file config to remote prefs on XposedService connect */
    fun syncToRemote() {
        try {
            val svc = App.xposedService ?: run { Log.i(TAG, "no service"); return }
            val remote = svc.getRemotePreferences("hoscfg_config")
            if (configFile.exists()) {
                val j = JSONObject(configFile.readText())
                for (k in j.keys()) {
                    val v = j.get(k)
                    when (v) { is Boolean -> remote.edit().putBoolean(k, v).apply(); is Int -> remote.edit().putInt(k, v).apply(); is String -> remote.edit().putString(k, v).apply() }
                }
                Log.i(TAG, "synced to remote prefs")
            }
        } catch (e: Exception) { Log.e(TAG, "sync failed", e) }
    }

    fun restartLauncher(context: Context) {
        Toast.makeText(context, "Restarting MIUI Home...", Toast.LENGTH_SHORT).show()
        Thread { try { Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop com.miui.home")) } catch (e: Exception) { Log.e(TAG, "restart failed", e) } }.start()
    }

    fun restartSystemUi(context: Context) {
        Toast.makeText(context, "Restarting SystemUI...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val pid = Runtime.getRuntime().exec(arrayOf("su", "-c", "pidof com.android.systemui")).inputStream.bufferedReader().readText().trim()
                Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -9 $pid"))
            } catch (e: Exception) { Log.e(TAG, "restart SystemUI failed", e) }
        }.start()
    }
}
