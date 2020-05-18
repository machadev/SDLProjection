package com.machadev.android.sdlprojection

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.OverlayItem
import permissions.dispatcher.*
import java.util.ArrayList

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private var itemsLocation = ArrayList<OverlayItem>()
    private var overlayLocation : ItemizedIconOverlay<OverlayItem>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //If we are connected to a module we want to start our SdlService
        if (BuildConfig.TRANSPORT == "MULTI" || BuildConfig.TRANSPORT == "MULTI_HB") {
            SdlReceiver.queryForConnectedService(this)
        } else if (BuildConfig.TRANSPORT == "TCP") {
            val proxyIntent = Intent(this, SdlService::class.java)
            startService(proxyIntent)
        }

        // MapViewを取得
        mapView = findViewById<MapView>(R.id.mapView)

        // Zoomレベルの設定
        mapView.controller.zoomTo(17.0)

        // ロケーションマネージャ生成
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
    }

    override fun onStart() {
        super.onStart()
        // OpenStreetMap使用開始
        startOpenStreetMapWithPermissionCheck()
    }

    override fun onStop() {
        super.onStop()
        // 位置情報の取得を停止
        stopLocationUpdate()
    }

    //------------------------------
    // Permissionチェック
    //------------------------------
    // OpenStreetMap使用開始
    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION)
    fun startOpenStreetMap() {
        // 位置情報の取得を開始
        startLocationUpdate()
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION)
    fun onContactsDenied() {

    }

    @OnShowRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION)
    fun showRationaleForContacts(request: PermissionRequest) {
        AlertDialog.Builder(this)
                .setPositiveButton(R.string.permission_request_proceed) { _, _ -> request.proceed() }
                .setNegativeButton(R.string.permission_request_cancel) { _, _ -> request.cancel() }
                .setCancelable(false)
                .setMessage(R.string.permission_request_reason)
                .show()
    }

    @OnNeverAskAgain(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION)
    fun onContactsNeverAskAgain() {

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
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
        val icon = resources.getDrawable(R.drawable.bike)
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
                }, this
        )

        // マップへオーバレイ追加
        mapView.overlays.add(overlayLocation)
    }

}