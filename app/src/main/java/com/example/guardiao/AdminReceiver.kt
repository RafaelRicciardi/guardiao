package com.example.guardiao

import android.app.admin.DeviceAdminReceiver

/** Ao ser ativado como administrador, o app não pode ser desinstalado sem antes desativá-lo. */
class AdminReceiver : DeviceAdminReceiver()
