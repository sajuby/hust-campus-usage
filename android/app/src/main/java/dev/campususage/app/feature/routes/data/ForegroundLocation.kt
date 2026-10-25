package dev.campususage.app.feature.routes.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.campususage.app.feature.routes.domain.Coordinates
import dev.campususage.app.feature.routes.model.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import kotlin.coroutines.resume

data class LocationFix(val point: GeoPoint,val accuracy: Float,val capturedAt: Instant)

class ForegroundLocation(private val context: Context) {
    suspend fun locate(): LocationFix {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            throw RouteFailure("尚未允许定位，可手动选择城市和起点")
        val manager=context.getSystemService(LocationManager::class.java)
        val providers=manager.getProviders(true).filter { it in setOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER) }
        if(providers.isEmpty())throw RouteFailure("系统定位未开启，可手动选择起点")
        val fix=withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location> { continuation ->
                val listener=object:LocationListener {
                    override fun onLocationChanged(location:Location) {
                        if(!location.accuracy.isFinite() || location.accuracy<0 || location.accuracy>25_000 || kotlin.math.abs(System.currentTimeMillis()-location.time)>120_000)return
                        if(continuation.isActive){manager.removeUpdates(this);continuation.resume(location)}
                    }
                    override fun onProviderEnabled(provider:String) {}
                    override fun onProviderDisabled(provider:String) {}
                    override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) {}
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                try { providers.forEach { manager.requestLocationUpdates(it,0L,0f,listener,Looper.getMainLooper()) } }
                catch(_:SecurityException) { manager.removeUpdates(listener);if(continuation.isActive)continuation.cancel() }
            }
        } ?: throw RouteFailure("定位超时或精度不足，可手动选择城市和起点")
        return LocationFix(Coordinates.gcj(GeoPoint(fix.longitude,fix.latitude,CoordinateSystem.WGS84)),fix.accuracy,Instant.ofEpochMilli(fix.time))
    }
}
