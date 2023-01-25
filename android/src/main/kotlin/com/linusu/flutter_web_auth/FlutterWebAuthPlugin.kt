package com.linusu.flutter_web_auth

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient

import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class FlutterWebAuthPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private var context: Context? = null
    private var channel: MethodChannel? = null
    private var customTabsSession: CustomTabsSession? = null
    private lateinit var customTabsClient: CustomTabsClient
    private var activity: Activity? = null
    private var binaryMessenger: BinaryMessenger? = null
    private var callCount = 0

    companion object {
        val callbacks = mutableMapOf<String, Result>()
    }

    private fun initInstance() {
        channel = MethodChannel(binaryMessenger!!, "flutter_web_auth")
        channel?.setMethodCallHandler(this)
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        binaryMessenger = binding.binaryMessenger
        initInstance()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = null
        channel = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        callCount++
        activity = binding.activity
        val connection: CustomTabsServiceConnection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
                customTabsClient = client
                client.warmup(0)
                customTabsSession = client.newSession(null)
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }
        CustomTabsClient.bindCustomTabsService(context!!, activity!!.packageName, connection)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onMethodCall(call: MethodCall, resultCallback: Result) {
        val activity = this.activity
        if (activity == null) {
            resultCallback.error(CODE_LAUNCH_ERROR, "Launching a CustomTabs requires a foreground activity.", null)
            return
        }
        when (call.method) {
            "warmupUrl" -> {
                val url = Uri.parse(call.argument("url"))
                customTabsSession?.mayLaunchUrl(url, null, null)
            }
            "logout" -> {
                val url = Uri.parse(call.argument("url"))
                val callbackUrlScheme = call.argument<String>("callbackUrlScheme")!!
                val intent = CustomTabsIntent.Builder(customTabsSession).build()
                callbacks[callbackUrlScheme] = resultCallback
                val keepAliveIntent = Intent(context, KeepAliveService::class.java)
                intent.intent.putExtra("android.support.customtabs.extra.KEEP_ALIVE", keepAliveIntent)
                intent.launchUrl(activity, url)
            }
            "authenticate" -> {
                val url = Uri.parse(call.argument("url"))
                val callbackUrlScheme = call.argument<String>("callbackUrlScheme")!!
                val preferEphemeral = call.argument<Boolean>("preferEphemeral")!!

                callbacks[callbackUrlScheme] = resultCallback
                val intent = CustomTabsIntent.Builder(customTabsSession).build()
                val keepAliveIntent = Intent(context, KeepAliveService::class.java)
                if (preferEphemeral) {
                    intent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                }
                intent.intent.putExtra("android.support.customtabs.extra.KEEP_ALIVE", keepAliveIntent)

                intent.launchUrl(activity, url)
            }
            "cleanUpDanglingCalls" -> {
                callbacks.forEach { (_, danglingResultCallback) ->
                    danglingResultCallback.error("CANCELED", "User canceled login", null)
                }
                callbacks.clear()
                resultCallback.success(null)
            }
            else -> resultCallback.notImplemented()
        }
    }
}

private const val CODE_LAUNCH_ERROR = "LAUNCH_ERROR"
