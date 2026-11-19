package com.example.guardiao

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class GuardService : AccessibilityService() {

    // Navegadores cuja barra de endereço sabemos ler (pacote -> id da view).
    // Se um navegador não funcionar, descubra o id com o "Layout Inspector" do Android Studio.
    private val urlBarIds = mapOf(
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.opera.browser" to "com.opera.browser:id/url_field"
    )

    // Qualquer outro navegador instalado (cuja URL não conseguimos verificar) é bloqueado por inteiro.
    private val unverifiableBrowsers: Set<String> by lazy {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet() - urlBarIds.keys - packageName
    }

    private var lastDeny = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val event = e ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val stateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        when {
            pkg in Prefs.blocked(this) -> if (stateChanged) deny("Aplicativo bloqueado")
            pkg in unverifiableBrowsers -> if (stateChanged) deny("Navegador não permitido")
            pkg in urlBarIds -> checkBrowser(pkg)
            pkg == "com.android.settings" ||
                pkg.endsWith(".packageinstaller") -> checkSettings()
        }
    }

      private fun checkBrowser(pkg: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val bar = root.findAccessibilityNodeInfosByViewId(urlBarIds[pkg]!!).firstOrNull() ?: return
        if (bar.isFocused || bar.isShowingHintText) return
        val host = Prefs.normalizeHost(bar.text?.toString().orEmpty())
        if (host.isEmpty()) return   // não parece um endereço (página inicial, dica, etc.)
        if (!Prefs.isAllowed(this, host)) deny("Bloqueado: $host")
    }

    /** Impede desativar o serviço / desinstalar o app pelas Configurações sem a senha. */
    private fun checkSettings() {
        if (Prefs.isUnlocked(this)) return
        val root = rootInActiveWindow ?: return
        val name = getString(R.string.app_name)
        if (root.findAccessibilityNodeInfosByText(name).isNotEmpty()) deny("Digite a senha no app $name")
    }

    private fun deny(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastDeny < 700) return
        lastDeny = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
}
