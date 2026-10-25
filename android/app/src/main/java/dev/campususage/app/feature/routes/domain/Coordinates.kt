package dev.campususage.app.feature.routes.domain

import dev.campususage.app.feature.routes.model.*
import kotlin.math.*

/** WGS84 -> GCJ02 mathematical conversion, adapted from wandergis/coordtransform (MIT). */
object Coordinates {
    fun gcj(point: GeoPoint): GeoPoint {
        if (point.system == CoordinateSystem.GCJ02) return point
        val lng = point.longitude; val lat = point.latitude
        if (lng <= 73.66 || lng >= 135.05 || lat <= 3.86 || lat >= 53.55) return GeoPoint(lng,lat)
        val x = lng - 105; val y = lat - 35
        val periodic = (20 * sin(6 * x * PI) + 20 * sin(2 * x * PI)) * 2 / 3
        val a = 6378245.0; val e = 0.00669342162296594323
        val deltaLat = -100 + 2*x + 3*y + .2*y*y + .1*x*y + .2*sqrt(abs(x)) + periodic +
            (20*sin(y*PI) + 40*sin(y*PI/3))*2/3 + (160*sin(y*PI/12) + 320*sin(y*PI/30))*2/3
        val deltaLng = 300 + x + 2*y + .1*x*x + .1*x*y + .1*sqrt(abs(x)) + periodic +
            (20*sin(x*PI) + 40*sin(x*PI/3))*2/3 + (150*sin(x*PI/12) + 300*sin(x*PI/30))*2/3
        val rad = lat * PI / 180
        val magic = 1 - e * sin(rad).pow(2)
        return GeoPoint(lng + deltaLng*180/(a/sqrt(magic)*cos(rad)*PI), lat + deltaLat*180/(a*(1-e)/(magic*sqrt(magic))*PI))
    }
}
