package com.example.sendbirdfluttertest

import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import com.sendbird.live.*
import com.sendbird.live.handler.InitResultHandler
import com.sendbird.webrtc.AudioDevice
import com.sendbird.webrtc.SendbirdException
import com.sendbird.webrtc.VideoDevice
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import android.media.AudioManager

class MainActivity: FlutterActivity() {
    private val CHANNEL = "samples.flutter.dev/battery"

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
            // This method is invoked on the main thread.
                call, result ->
            if (call.method == "liveinit") {
                val params = InitParams("EE662E70-97B5-412A-86A9-7A5F304F59CD", applicationContext)
                SendbirdLive.init(params, object : InitResultHandler {
                    override fun onInitFailed(e: SendbirdException) {
                        print("onInitFailed...")
                    }

                    override fun onInitSucceed() {
                        print("onInitSuccess...")
                    }

                    override fun onMigrationStarted() {
                        print("onMigrationStarted...")
                    }
                })
            } else if (call.method == "authenticate") {
                val params = AuthenticateParams("jahum","")
                SendbirdLive.authenticate(params) { user, e ->
                    if (e != null) {
                        //handle error
                        print("authenticate error..")
                        return@authenticate
                    }
                    // The user has been authenticated successfully and is connected to Sendbird server.
                    print("authenticate success..")
                }
            } else if (call.method == "createlive") {
                val names = mutableListOf<String>()
                names.add("jahum")

                val params = LiveEventCreateParams(names).apply {
                    title = "테스트타이틀"
                    coverUrl = ""

                }
                SendbirdLive.createLiveEvent(params) { liveEvent, e ->
                    if (e != null) {
                        //handle error
                        print("createlive error..")
                        return@createLiveEvent
                    }
                    print("createlive success..")

                    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

                    var currentVideoDevice: VideoDevice? = null
                    var availableVideoDevices = mutableListOf<VideoDevice>()

                    availableVideoDevices.addAll(cameraManager.getAvailableVideoDevices(this))

                    currentVideoDevice = availableVideoDevices.filter { it.position == VideoDevice.Position.FRONT }.getOrElse(0) { availableVideoDevices.firstOrNull() }

                    liveEvent?.enterAsHost(MediaOptions(videoDevice = currentVideoDevice, audioDevice = null)) {
                        if (it != null) {
                            print("enterAsHost fail..")
                            return@enterAsHost
                        }
                        print("enterAsHost success..")

                        liveEvent.startEvent {
                            if (it != null) {
                                // handle error
                                print("startEvent fail..")
                                return@startEvent
                            }
                            // The live event has started. Participants can now view the host's video.
                            print("startEvent success..")
                        }
                    }
                }
            }


        }
    }

    private fun getBatteryLevel(): Int {
        val batteryLevel: Int
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            val intent = ContextWrapper(applicationContext).registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryLevel = intent!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        }

        return batteryLevel
    }
}

@RequiresApi(VERSION_CODES.LOLLIPOP)
internal fun CameraManager.getAvailableVideoDevices(context: Context): List<VideoDevice>{
    val videoDevices = mutableListOf<VideoDevice>()
    val cameraEnumerator = if (Camera2Enumerator.isSupported(context)) {
        Camera2Enumerator(context)
    } else {
        Camera1Enumerator(true)
    }

    cameraEnumerator.deviceNames.forEach {
        val position = when {
            cameraEnumerator.isBackFacing(it) -> VideoDevice.Position.BACK
            cameraEnumerator.isFrontFacing(it) -> VideoDevice.Position.FRONT
            else -> VideoDevice.Position.UNSPECIFIED
        }

        val device = VideoDevice.createVideoDevice(it, position, this.getCameraCharacteristics(it))
        if (!canCauseCrash(device)) {
            videoDevices.add(device)
        }
    }
    return videoDevices
}

@RequiresApi(VERSION_CODES.LOLLIPOP)
private fun canCauseCrash(videoDevice: VideoDevice?): Boolean {
    videoDevice?.cameraCharacteristics ?: return false
    val characteristics = videoDevice.cameraCharacteristics
    val streamMap = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return true
    val nativeSizes = streamMap.getOutputSizes(SurfaceTexture::class.java)
    return nativeSizes == null
}