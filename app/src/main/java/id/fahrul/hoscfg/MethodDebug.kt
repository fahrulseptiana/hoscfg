package id.fahrul.hoscfg

import android.util.Log

// Quick reflection debug helper
object MethodDebug {
    private const val TAG = "HOSCfgDebug"

    fun dumpMethods(className: String, cl: ClassLoader) {
        try {
            val clz = Class.forName(className, true, cl)
            val methods = clz.methods.filter { it.name.contains("setOnPref") || it.name.contains("OnPref") || it.name.contains("setChecked") || it.name == "addPreference" || it.name == "setPersistent" }
            for (m in methods) {
                Log.i(TAG, "${clz.simpleName}.${m.name}(${m.parameterTypes.map { it.simpleName }.joinToString(", ")})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "dump failed: $className", e)
        }
    }
}
