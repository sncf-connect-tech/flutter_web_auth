package com.linusu.flutter_web_auth

import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle

class CallbackActivity: Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val url = intent?.data
    val scheme = url?.scheme

    if (scheme != null) {
      FlutterWebAuthPlugin.callbacks.remove(scheme)?.success(url.toString())
    }

    val context = applicationContext
    val pm: PackageManager = context.packageManager
    val intent = pm.getLaunchIntentForPackage(context.packageName)
    startActivity(intent)
    finish()
  }
}
