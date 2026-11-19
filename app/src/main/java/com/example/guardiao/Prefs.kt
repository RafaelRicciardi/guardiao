package com.example.guardiao

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

object Prefs {
    val DEFAULT_BLOCKED = setOf(
        "com.google.android.youtube",
        "com.google.android.gm",
        "com.android.chrome"
    )

    private fun sp(c: Context) = c.getSharedPreferences("guard", Context.MODE_PRIVATE)

    // ---------- Senha ----------
    fun hasPin(c: Context) = sp(c).contains("pin_hash")

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun setPin(c: Context, pin: String) {
        val salt = UUID.randomUUID().toString()
        sp(c).edit().putString("salt", salt).putString("pin_hash", hash(pin, salt)).apply()
    }

    fun checkPin(c: Context, pin: String): Boolean {
        val s = sp(c)
        val salt = s.getString("salt", null) ?: return false
        return hash(pin, salt) == s.getString("pin_hash", null)
    }

    // Bloqueio temporário após tentativas erradas
    fun lockRemainingSec(c: Context): Long =
        ((sp(c).getLong("lock_until", 0) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    fun registerFail(c: Context) {
        val s = sp(c)
        val n = s.getInt("fails", 0) + 1
        if (n >= 5) s.edit().putInt("fails", 0).putLong("lock_until", System.currentTimeMillis() + 60_000).apply()
        else s.edit().putInt("fails", n).apply()
    }

    fun resetFails(c: Context) = sp(c).edit().putInt("fails", 0).apply()

    // Janela em que as telas de Configurações do sistema ficam liberadas
    fun unlockFor(c: Context, minutes: Int) =
        sp(c).edit().putLong("unlocked_until", System.currentTimeMillis() + minutes * 60_000L).apply()

    fun lockNow(c: Context) = sp(c).edit().putLong("unlocked_until", 0).apply()

    fun isUnlocked(c: Context) = System.currentTimeMillis() < sp(c).getLong("unlocked_until", 0)

    // ---------- Apps bloqueados ----------
    fun blocked(c: Context): Set<String> =
        HashSet(sp(c).getStringSet("blocked", DEFAULT_BLOCKED)!!)

    fun setBlocked(c: Context, s: Set<String>) = sp(c).edit().putStringSet("blocked", s).apply()

    // ---------- Sites permitidos ----------
    fun domains(c: Context): Set<String> =
        HashSet(sp(c).getStringSet("domains", emptySet())!!)

    fun setDomains(c: Context, s: Set<String>) = sp(c).edit().putStringSet("domains", s).apply()

    /** "https://www.Exemplo.com/a?b" -> "exemplo.com". Retorna "" se não parecer um endereço. */
    fun normalizeHost(text: String): String {
        var s = text.trim().lowercase()
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        s = s.removePrefix("www.")
        return if (s.isEmpty() || s.contains(' ') || !s.contains('.')) "" else s
    }

    fun isAllowed(c: Context, urlText: String): Boolean {
        val host = normalizeHost(urlText)
        if (host.isEmpty()) return false
        return domains(c).any { host == it || host.endsWith(".$it") }
    }

    fun setLast(c: Context, s: String) = sp(c).edit().putString("last", s).apply()
    fun last(c: Context): String = sp(c).getString("last", "(nenhum ainda)")!!
}
