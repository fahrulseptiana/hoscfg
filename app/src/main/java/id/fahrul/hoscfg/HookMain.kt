package id.fahrul.hoscfg

import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.*

class HookMain : XposedModule() {
    companion object {
        private const val TAG = "HOSCfg"
        private const val PKG_HOME = "com.miui.home"
        private const val PREFS_NAME = "hoscfg_config"
        private var globalPrefs: SharedPreferences? = null
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        Log.i(TAG, "loaded in ${param.processName}")
        globalPrefs = getRemotePreferences(PREFS_NAME)
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {}

    override fun onPackageReady(param: PackageReadyParam) {
        val cl = param.classLoader
        when (param.packageName) {
            PKG_HOME -> hookMiuiHome(cl)
            "com.android.systemui" -> hookSystemUi(cl)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {}

    private fun hookSystemUi(cl: ClassLoader) {
        try {
            // Check if no-SIM hiding is enabled (from remote prefs, accessible across processes)
            try {
                val prefs = getRemotePreferences(PREFS_NAME)
                if (!prefs.getBoolean(Config.KEY_HIDE_NO_SIM, false)) {
                    Log.i(TAG, "no_sim disabled"); return
                }
            } catch (_: Exception) { Log.i(TAG, "no_sim disabled (no prefs)"); return }
            Log.i(TAG, "no_sim enabled")
            // Block no_sim resource from being loaded via Resources
            try {
                val resClass = android.content.res.Resources::class.java
                for (m in resClass.methods) {
                    if (m.name == "getDrawable" && m.parameterTypes.size >= 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType) {
                        hook(m).intercept { chain ->
                            val id = chain.getArgs()[0] as Int
                            try {
                                val ctx = chain.getThisObject() as? android.content.res.Resources
                                if (ctx != null) {
                                    val name = ctx.getResourceEntryName(id)
                                    if (name.contains("no_sim")) {
                                        Log.i(TAG, "block no_sim: $name")
                                        return@intercept null
                                    }
                                }
                            } catch (_: Exception) {}
                            chain.proceed()
                        }
                        Log.i(TAG, "hooked ${m.name}(${m.parameterTypes.size} params)")
                    }
                }
            } catch (e: Exception) { Log.i(TAG, "res hook: ${e.message}") }

            // Try NetworkController methods
            for (pair in listOf(
                "com.android.systemui.statusbar.policy.NetworkControllerImpl" to "isNoSims",
                "com.android.systemui.statusbar.policy.MobileSignalController" to "isNoSims",
                "com.android.systemui.statusbar.policy.MobileSignalController" to "getNoSimIcon"
            )) {
                try {
                    val c = cl.loadClass(pair.first); val m = c.getDeclaredMethod(pair.second); m.isAccessible = true
                    hook(m).intercept { Log.i(TAG, "block ${pair.first}.${pair.second}"); false }
                } catch (_: Exception) {}
            }

            // Hook TextView.setText to hide "Emergency call only" text
            try {
                hook(android.widget.TextView::class.java.getMethod("setText", java.lang.CharSequence::class.java)).intercept { chain ->
                    val text = chain.getArgs()[0]?.toString() ?: ""
                    if (text.contains("emergency", true) || text.contains("no service", true) || text.contains("no_service", true)) {
                        Log.i(TAG, "block emergency text: $text")
                        val tv = chain.getThisObject() as? android.widget.TextView
                        if (tv != null) { tv.visibility = 8 } // GONE
                        return@intercept null
                    }
                    chain.proceed()
                }
                Log.i(TAG, "emergency text hook set")
            } catch (e: Exception) { Log.i(TAG, "text hook: ${e.message}") }
        } catch (e: Exception) { Log.e(TAG, "systemui failed", e) }
    }

    private fun hookMiuiHome(cl: ClassLoader) {
        val p = globalPrefs ?: return
        var hideSearch = p.getBoolean(Config.KEY_HIDE_SEARCH, true)
        var bgColor = p.getInt(Config.KEY_BG_COLOR, Color.BLACK)
        var bgAlpha = p.getInt(Config.KEY_BG_ALPHA, 255)
        var labelColor = p.getInt(Config.KEY_LABEL_COLOR, Color.TRANSPARENT)

        try {
            val f = java.io.File("/data/data/com.miui.home/files/hoscfg_config.json")
            if (f.exists()) {
                val j = org.json.JSONObject(f.readText())
                if (j.has("hide_search_bar")) hideSearch = j.getBoolean("hide_search_bar")
                if (j.has("drawer_bg_color")) bgColor = j.getInt("drawer_bg_color")
                if (j.has("drawer_bg_alpha")) bgAlpha = j.getInt("drawer_bg_alpha")
                if (j.has("icon_label_color")) labelColor = j.getInt("icon_label_color")
            }
        } catch (_: Exception) {}

        if (!java.io.File("/data/data/com.miui.home/files/hoscfg_config.json").exists()) {
            try {
                val f = java.io.File("/data/data/id.fahrul.hoscfg/files/config.json")
                if (f.exists()) {
                    val j = org.json.JSONObject(f.readText())
                    if (j.has("hide_search_bar")) hideSearch = j.getBoolean("hide_search_bar")
                    if (j.has("drawer_bg_color")) bgColor = j.getInt("drawer_bg_color")
                    if (j.has("drawer_bg_alpha")) bgAlpha = j.getInt("drawer_bg_alpha")
                    if (j.has("icon_label_color")) labelColor = j.getInt("icon_label_color")
                }
            } catch (_: Exception) {}
        }

        Log.i(TAG, "hide=$hideSearch bg=${Integer.toHexString(bgColor)} a=$bgAlpha")

        try {
            injectSettingsToggle(cl, p)

            if (hideSearch) {
                val cc = Class.forName("com.miui.home.launcher.allapps.BaseAllAppsContainerView", true, cl)
                hook(cc.getDeclaredMethod("onFinishInflate")).intercept { chain ->
                    chain.proceed(); val v = chain.getThisObject() as View; val r = v.resources
                    fun id(n: String) = r.getIdentifier(n, "id", PKG_HOME)
                    v.findViewById<View>(id("all_apps_search_bar_holder"))?.let { it.visibility = View.GONE; it.layoutParams?.let { lp -> lp.height = 0; it.layoutParams = lp } }
                    v.findViewById<View>(id("all_apps_search_bar_divider"))?.let { it.visibility = View.GONE; it.layoutParams?.let { lp -> lp.height = 0; it.layoutParams = lp } }
                    v.findViewById<View>(id("all_apps_category_container"))?.let { val lp = it.layoutParams as android.widget.RelativeLayout.LayoutParams; lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM); it.layoutParams = lp }
                    v.requestLayout()
                }
                val sb = Class.forName("com.miui.home.launcher.SearchBar", true, cl)
                hook(sb.getDeclaredMethod("onFinishInflate")).intercept { chain ->
                    chain.proceed(); val s = chain.getThisObject() as View; val r = s.resources
                    s.findViewById<View>(r.getIdentifier("search_bar_drawer_layout", "id", PKG_HOME))?.let { it.visibility = View.GONE; it.layoutParams?.let { lp -> lp.height = 0; it.layoutParams = lp } }
                    s.findViewById<View>(r.getIdentifier("search_bar_desktop_layout", "id", PKG_HOME))?.let { it.visibility = View.GONE }
                    s.visibility = View.GONE; s.layoutParams?.let { lp -> lp.height = 0; s.layoutParams = lp }
                }
            }

            val scrim = Class.forName("com.miui.home.launcher.view.ScrimView", true, cl)
            hook(scrim.getDeclaredMethod("setColorValue", android.content.Context::class.java)).intercept { chain ->
                chain.proceed(); val s = chain.getThisObject()
                try { scrim.getDeclaredField("mEndFlatColor").apply { isAccessible = true; set(s, bgColor) }; scrim.getDeclaredField("mEndFlatColorAlpha").apply { isAccessible = true; setInt(s, bgAlpha) } } catch (e: Exception) { Log.e(TAG, "scrim", e) }
            }

            if (labelColor != Color.TRANSPARENT) {
                val cm = Class.forName("com.miui.home.launcher.allapps.AllAppsColorMode", true, cl)
                hook(cm.getDeclaredMethod("getAppTextColor", android.content.Context::class.java, java.lang.Integer.TYPE)).intercept { labelColor }
            }
        } catch (e: Throwable) { Log.e(TAG, "hook failed", e) }
    }

    private fun injectSettingsToggle(cl: ClassLoader, p: SharedPreferences) {
        try {
            val fc = Class.forName("com.miui.home.settings.BaseAllAppsSettingsFragment", true, cl)
            val m = try { fc.getDeclaredMethod("onCreatePreferences", android.os.Bundle::class.java, String::class.java) } catch (_: NoSuchMethodException) { fc.getMethod("onCreate", android.os.Bundle::class.java) }
            hook(m).intercept { chain ->
                chain.proceed()
                try {
                    val frag = chain.getThisObject(); val ctx = frag.javaClass.getMethod("getContext").invoke(frag) as android.content.Context
                    android.os.Handler(android.os.Looper.getMainLooper()).post { try { injectCheckBox(frag, ctx, p, cl) } catch (e: Exception) { Log.e(TAG, "delayed", e) } }
                } catch (e: Exception) { Log.e(TAG, "inject", e) }
            }
        } catch (e: Exception) { Log.e(TAG, "inject setup", e) }
    }

    private fun injectCheckBox(frag: Any, ctx: android.content.Context, p: SharedPreferences, cl: ClassLoader) {
        try {
            var screen: Any? = null; for (m in frag.javaClass.methods) { if (m.name == "getPreferenceScreen") { screen = m.invoke(frag); break } }; if (screen == null) return
            val gp = screen.javaClass.getMethod("getPreference", Int::class.javaPrimitiveType)
            val cnt = screen.javaClass.getMethod("getPreferenceCount").invoke(screen) as Int
            for (i in 0 until cnt) {
                val cat = gp.invoke(screen, i); if (cat.javaClass.simpleName != "PreferenceCategory") continue
                val cg = cat.javaClass.getMethod("getPreference", Int::class.javaPrimitiveType)
                val cc = cat.javaClass.getMethod("getPreferenceCount").invoke(cat) as Int
                for (j in 0 until cc) {
                    val child = cg.invoke(cat, j)
                    if (child.javaClass.getMethod("getTitle").invoke(child)?.toString() != "App suggestions") continue
                    val pc = cl.loadClass(child.javaClass.name)
                    val cb = pc.getConstructor(android.content.Context::class.java).newInstance(ctx)
                    pc.getMethod("setTitle", java.lang.CharSequence::class.java).invoke(cb, "Hide Search Bar")
                    pc.getMethod("setSummary", java.lang.CharSequence::class.java).invoke(cb, "Remove search bar from the drawer (HOSCfg)")
                    val chk = try { val f = java.io.File("/data/data/com.miui.home/files/hoscfg_config.json"); if (f.exists()) org.json.JSONObject(f.readText()).optBoolean("hide_search_bar", true) else p.getBoolean("hide_search_bar", true) } catch (_: Exception) { p.getBoolean("hide_search_bar", true) }
                    pc.getMethod("setChecked", java.lang.Boolean.TYPE).invoke(cb, chk)
                    // Hook callChangeListener
                    try {
                        var mth: java.lang.reflect.Method? = null; var c2: Class<*>? = pc
                        while (c2 != null && mth == null) { try { mth = c2.getDeclaredMethod("callChangeListener", Any::class.java) } catch (_: NoSuchMethodException) { c2 = c2.superclass } }
                        if (mth != null) {
                            mth.isAccessible = true
                            hook(mth).intercept { ch ->
                                val pref = ch.getThisObject()
                                if (pref.javaClass.getMethod("getTitle").invoke(pref)?.toString() == "Hide Search Bar") {
                                    val nv = ch.getArgs()[0] as Boolean; Log.i(TAG, "toggled: $nv")
                                    java.io.File("/data/data/com.miui.home/files/hoscfg_config.json").apply { parentFile?.mkdirs(); writeText("{\"hide_search_bar\":$nv}") }
                                    Thread { Thread.sleep(200); Runtime.getRuntime().exec(arrayOf("am", "force-stop", "com.miui.home")) }.start()
                                }
                                ch.proceed()
                            }
                        }
                    } catch (_: Exception) {}
                    // Insert
                    try {
                        var am: java.lang.reflect.Method? = null; for (m in cat.javaClass.methods) { if (m.name == "addPreference" && m.parameterTypes.size == 1) { am = m; break } }
                        if (am != null) am.invoke(cat, cb) else throw Exception()
                        Log.i(TAG, "added")
                    } catch (e: Exception) {
                        var c2: Class<*>? = cat.javaClass; var lf: java.lang.reflect.Field? = null
                        while (c2 != null && lf == null) { try { lf = c2.getDeclaredField("mPreferences") } catch (_: NoSuchFieldException) { c2 = c2.superclass } }
                        if (lf != null) {
                            lf.isAccessible = true; @Suppress("UNCHECKED_CAST") (lf.get(cat) as java.util.List<Any>).add(j + 1, cb)
                            c2 = cat.javaClass; var nm: java.lang.reflect.Method? = null
                            while (c2 != null && nm == null) { try { nm = c2.getDeclaredMethod("notifyHierarchyChanged"); nm.isAccessible = true } catch (_: NoSuchMethodException) { c2 = c2.superclass } }; nm?.invoke(cat)
                        }
                    }
                    return
                }
            }
        } catch (e: Exception) { Log.e(TAG, "injectCheckBox", e) }
    }
}
