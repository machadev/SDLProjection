package com.machadev.android.sdlprojection;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.smartdevicelink.managers.SdlManager
import com.smartdevicelink.managers.SdlManagerListener
import com.smartdevicelink.managers.file.filetypes.SdlArtwork
import com.smartdevicelink.managers.lifecycle.LifecycleConfigurationUpdate
import com.smartdevicelink.protocol.enums.FunctionID
import com.smartdevicelink.proxy.RPCNotification
import com.smartdevicelink.proxy.rpc.OnHMIStatus
import com.smartdevicelink.proxy.rpc.SetDisplayLayout
import com.smartdevicelink.proxy.rpc.VideoStreamingCapability
import com.smartdevicelink.proxy.rpc.enums.*
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener
import com.smartdevicelink.streaming.video.SdlRemoteDisplay
import com.smartdevicelink.streaming.video.VideoStreamingParameters
import com.smartdevicelink.transport.BaseTransportConfig
import com.smartdevicelink.transport.MultiplexTransportConfig
import com.smartdevicelink.transport.TCPTransportConfig

import java.util.Vector;

/**
 * SDLサービスクラス
 */
class SdlService : Service() {

	companion object {
		private val TAG = "SDL Service"
		private val APP_NAME = "SDL Display"
		private val APP_ID = "8678309"
		private val ICON_FILENAME = "hello_sdl_icon.png"

		private val FOREGROUND_SERVICE_ID = 111

		// TCP/IP transport config
		// The default port is 12345
		// The IP is of the machine that is running SDL Core
		private val TCP_PORT = 12345
		private val DEV_MACHINE_IP_ADDRESS = "10.0.0.1"
	}

	// variable to create and call functions of the SyncProxy
	private var sdlManager: SdlManager? = null

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onCreate() {
		Log.d(TAG, "onCreate")
		super.onCreate()

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			enterForeground()
		}
	}

	// Helper method to let the service enter foreground mode
	@SuppressLint("NewApi")
	fun enterForeground() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT)
			val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(channel)
				val serviceNotification = Notification.Builder(this, channel.id)
						.setContentTitle("Connected through SDL")
						.setSmallIcon(R.drawable.ic_sdl)
						.build()
				startForeground(FOREGROUND_SERVICE_ID, serviceNotification)
			}
		}
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
		startProxy()
		return Service.START_STICKY
	}

	override fun onDestroy() {

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true)
		}

		if (sdlManager != null) {
			sdlManager!!.dispose()
		}

		super.onDestroy()
	}

	private fun startProxy() {

		if (sdlManager == null) {
			Log.i(TAG, "Starting SDL Proxy")

			var transport: BaseTransportConfig? = null
			if (BuildConfig.TRANSPORT == "MULTI") {
				val securityLevel: Int
				if (BuildConfig.SECURITY == "HIGH") {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH
				} else if (BuildConfig.SECURITY == "MED") {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED
				} else if (BuildConfig.SECURITY == "LOW") {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW
				} else {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF
				}
				transport = MultiplexTransportConfig(this, APP_ID, securityLevel)
			} else if (BuildConfig.TRANSPORT == "TCP") {
				transport = TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true)
			} else if (BuildConfig.TRANSPORT == "MULTI_HB") {
				val mtc = MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF)
				mtc.setRequiresHighBandwidth(true)
				transport = mtc
			}

			// NAVIGATIONにしておく
			val appType = Vector<AppHMIType>()
			appType.add(AppHMIType.NAVIGATION)


			// The manager listener helps you know when certain events that pertain to the SDL Manager happen
			// Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
			val listener = object : SdlManagerListener {
				override fun onStart() {
					// HMI Status Listener
					sdlManager!!.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, object : OnRPCNotificationListener() {
						override fun onNotified(notification: RPCNotification) {

							val status = notification as OnHMIStatus

							//初回起動時の処理
							if (status.hmiLevel == HMILevel.HMI_FULL && notification.firstRun!!) {

								val setDisplayLayoutRequest = SetDisplayLayout()
								setDisplayLayoutRequest.displayLayout = PredefinedLayout.NAV_FULLSCREEN_MAP.toString()
								sdlManager!!.sendRPC(setDisplayLayoutRequest)
								if (sdlManager!!.videoStreamManager != null) {

									//VideoStreamが有効になったら、プロジェクションを開始する
									sdlManager!!.videoStreamManager!!.start { success ->
										if (success) {
											startProjectionMode()
										} else {
											Log.e(TAG, "Failed to start video streaming manager")
										}
									}
								}
							}

							//HMIが終了したらプロジェクションを終了する
							if (status != null && status.hmiLevel == HMILevel.HMI_NONE) {
								stopProjectionMode()
							}
						}
					})
				}

				override fun onDestroy() {
					this@SdlService.stopSelf()
				}

				override fun managerShouldUpdateLifecycle(language: Language?): LifecycleConfigurationUpdate {
					return LifecycleConfigurationUpdate()
				}

				override fun onError(info: String, e: Exception) {}
			}

			// Create App Icon, this is set in the SdlManager builder
			val appIcon = SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true)

			// The manager builder sets options for your session
			val builder = SdlManager.Builder(this, APP_ID, APP_NAME, listener)
			builder.setAppTypes(appType)
			builder.setTransportType(transport!!)
			builder.setAppIcon(appIcon)
			sdlManager = builder.build()
			sdlManager!!.start()
		}
	}

	/**
	 * プロジェクションモードを開始する
	 */
	private fun startProjectionMode() {

		if (sdlManager == null) {
			return
		}

		//画面のサイズをとる
		val obj = sdlManager!!.systemCapabilityManager.getCapability(SystemCapabilityType.VIDEO_STREAMING)
		if (obj is VideoStreamingCapability) {
			val str = String.format("Display size Width:%d Height:%d",
					obj.preferredResolution.resolutionWidth,
					obj.preferredResolution.resolutionHeight)
			Log.i(TAG, str)
		}

		val parameters = VideoStreamingParameters()

		sdlManager?.videoStreamManager?.startRemoteDisplayStream(applicationContext, RemoteDisplay::class.java, parameters, false)
	}

	/**
	 * プロジェクションモードを停止する
	 */
	private fun stopProjectionMode() {

		if (sdlManager == null) {
			return
		}

		sdlManager!!.videoStreamManager!!.stopStreaming()
	}

}

