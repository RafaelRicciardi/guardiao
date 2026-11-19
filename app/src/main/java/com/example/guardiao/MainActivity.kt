package com.example.guardiao

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private var authed = false
    private var keepAuth = false
    private var dialog: AlertDialog? = null

    override fun onResume() {
        super.onResume()
        keepAuth = false
        when {
            !Prefs.hasPin(this) -> createPin()
            !authed -> login()
            else -> panel()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!keepAuth) authed = false
        dialog?.dismiss()
    }

    private fun pinField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- Criar senha ----------
    private fun createPin(change: Boolean = false) {
        dialog?.dismiss()
        val a = pinField("Nova senha (mín. 4 dígitos)")
        val b = pinField("Repita a senha")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(a); addView(b)
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(if (change) "Alterar senha" else "Crie uma senha")
            .setView(box)
            .setCancelable(change)
            .setPositiveButton("Salvar") { _, _ ->
                val p = a.text.toString()
                if (p.length < 4 || p != b.text.toString()) {
                    toast("Senhas diferentes ou curtas demais"); createPin(change)
                } else {
                    Prefs.setPin(this, p); authed = true; Prefs.unlockFor(this, 5); panel()
                }
            }
            .also { if (change) it.setNegativeButton("Cancelar") { _, _ -> panel() } }
            .show()
    }

    // ---------- Login ----------
    private fun login() {
        dialog?.dismiss()
        setContentView(View(this))
        val wait = Prefs.lockRemainingSec(this)
        val f = pinField(if (wait > 0) "Aguarde $wait s" else "Senha")
        f.isEnabled = wait == 0L
        dialog = AlertDialog.Builder(this)
            .setTitle("Digite a senha")
            .setView(LinearLayout(this).apply { setPadding(48, 16, 48, 0); addView(f, -1, -2) })
            .setCancelable(false)
            .setPositiveButton("Entrar") { _, _ ->
                if (Prefs.lockRemainingSec(this) > 0) { login(); return@setPositiveButton }
                if (Prefs.checkPin(this, f.text.toString())) {
                    Prefs.resetFails(this); authed = true; Prefs.unlockFor(this, 5); panel()
                } else {
                    Prefs.registerFail(this); toast("Senha incorreta"); login()
                }
            }
            .setNegativeButton("Sair") { _, _ -> finish() }
            .show()
    }

    // ---------- Painel ----------
    private fun panel() {
        dialog?.dismiss()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }

        fun label(s: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        }
        fun button(s: String, onClick: () -> Unit) = Button(this).apply { text = s; setOnClickListener { onClick() } }

        root.addView(label("Guardião", 26f, true))

        // Status do serviço
        val enabledList = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val serviceOn = enabledList?.contains("$packageName/") == true
        root.addView(label(if (serviceOn) "✔ Proteção ATIVA" else "✖ Proteção DESATIVADA — ative o serviço de acessibilidade"))
        if (!serviceOn) root.addView(button("Abrir configurações de acessibilidade") {
            keepAuth = true; startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        // Administrador do dispositivo (dificulta desinstalar)
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, AdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) root.addView(button("Ativar proteção contra desinstalação") {
            keepAuth = true
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin))
        }) else root.addView(label("✔ Proteção contra desinstalação ativa"))

        section(root, "Sites permitidos (exceções)", Prefs.domains(this).sorted(), "ex.: wikipedia.org",
            add = { s ->
                val h = Prefs.normalizeHost(s)
                if (h.isEmpty()) toast("Endereço inválido") else Prefs.setDomains(this, Prefs.domains(this) + h)
            },
            remove = { Prefs.setDomains(this, Prefs.domains(this) - it) })

        section(root, "Apps bloqueados (nome do pacote)", Prefs.blocked(this).sorted(), "ex.: com.google.android.youtube",
            add = { s -> if (s.isNotBlank()) Prefs.setBlocked(this, Prefs.blocked(this) + s.trim()) },
            remove = { Prefs.setBlocked(this, Prefs.blocked(this) - it) })

        root.addView(label("Segurança", 18f, true))
        root.addView(button("Alterar senha") { createPin(change = true) })
        root.addView(button("Bloquear agora") {
            authed = false; Prefs.lockNow(this); login()
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun section(
        root: LinearLayout, title: String, items: List<String>, hint: String,
        add: (String) -> Unit, remove: (String) -> Unit
    ) {
        root.addView(TextView(this).apply {
            text = title; textSize = 18f; setPadding(0, 48, 0, 8)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        if (items.isEmpty()) root.addView(TextView(this).apply { text = "(nenhum)" })
        items.forEach { item ->
            root.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply { text = item }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(Button(context).apply { text = "Remover"; setOnClickListener { remove(item); panel() } })
            })
        }
        val field = EditText(this).apply { this.hint = hint; isSingleLine = true }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(field, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(context).apply {
                text = "Adicionar"
                setOnClickListener { add(field.text.toString()); panel() }
            })
        })
    }
}
