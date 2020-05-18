package com.machadev.android.sdlprojection

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Display
import com.smartdevicelink.streaming.video.SdlRemoteDisplay
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.OverlayItem
import java.util.ArrayList

/**
 * プロジェクション用の画面
 */
class RemoteDisplay : SdlRemoteDisplay {

    constructor(context: Context?, display: Display?) : super(context, display) {

    }

    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private var itemsLocation = ArrayList<OverlayItem>()
    private var overlayLocation : ItemizedIconOverlay<OverlayItem>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.remote_display)

        // MapViewを取得
        mapView = findViewById<MapView>(R.id.mapView)

        // Zoomレベルの設定
        mapView.controller.zoomTo(17.0)

        // ロケーションマネージャ生成
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        Configuration.getInstance().load(context.applicationContext, PreferenceManager.getDefaultSharedPreferences(context.applicationContext))
    }

    override fun onStart() {
        super.onStart()
        startLocationUpdate()
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdate()
    }

    //------------------------------
    // 位置情報Listener
    //------------------------------
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location?) {
            location?.let {
                // 現在位置のアイコンをオーバーレイ表示
                val geo = GeoPoint(location.latitude, location.longitude)
                setOverlayLocation(geo)
                // 現在位置をマップ中心に設定
                mapView.controller.setCenter(geo)
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {

        }

        override fun onProviderEnabled(provider: String?) {

        }

        override fun onProviderDisabled(provider: String?) {

        }
    }

    // 位置情報の取得を開始
    private fun startLocationUpdate() {
        // 位置情報取得Permissionが許可済を確認して位置情報取得を登録
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 2f, locationListener)
        }
    }

    // 位置情報の取得を停止
    private fun stopLocationUpdate() {
        locationManager.removeUpdates(locationListener)
    }

    // 現在位置のアイコンをオーバーレイ表示
    private fun setOverlayLocation(geo:GeoPoint) {
        overlayLocation?.let {
            itemsLocation.clear()
            mapView.overlays.remove(overlayLocation)
        }

        // 現在位置のアイコン生成
        val icon = resources.getDrawable(R.drawable.car)
        val item = OverlayItem("", "", geo)
        item.setMarker(icon)
        itemsLocation.add(item)

        // オーバーレイ生成
        overlayLocation = ItemizedIconOverlay(
                itemsLocation,
                object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                    override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                        return true
                    }

                    override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                        return false
                    }
                }, context
        )

        // マップへオーバレイ追加
        mapView.overlays.add(overlayLocation)
    }
}
