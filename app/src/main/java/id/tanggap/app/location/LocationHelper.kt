package id.tanggap.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlin.math.*

/**
 * LocationHelper — fully offline.
 * GPS → koordinat → cocokkan ke provinsi terdekat dari data hardcode.
 * Tidak butuh internet, tidak butuh Geocoder API.
 */
object LocationHelper {

    data class ProvinceLocation(
        val name: String,
        val lat: Double,
        val lon: Double
    )

    // Koordinat pusat setiap provinsi (offline, hardcode)
    private val PROVINCE_CENTERS = listOf(
        ProvinceLocation("Aceh",                      4.695135,  96.749397),
        ProvinceLocation("Sumatera Utara",             2.115201,  99.543053),
        ProvinceLocation("Sumatera Barat",            -0.739875,  100.800514),
        ProvinceLocation("Riau",                       0.293491,  101.706956),
        ProvinceLocation("Kepulauan Riau",             3.945651,  108.142661),
        ProvinceLocation("Jambi",                     -1.610129,  103.611802),
        ProvinceLocation("Bengkulu",                  -3.793889,  102.265961),
        ProvinceLocation("Sumatera Selatan",           -3.319493,  103.914399),
        ProvinceLocation("Kepulauan Bangka Belitung", -2.741051,  106.440849),
        ProvinceLocation("Lampung",                   -4.558480,  105.405296),
        ProvinceLocation("Banten",                    -6.405614,  106.064241),
        ProvinceLocation("DKI Jakarta",               -6.211544,  106.845172),
        ProvinceLocation("Jawa Barat",                -6.889385,  107.640524),
        ProvinceLocation("Jawa Tengah",               -7.150975,  110.140259),
        ProvinceLocation("DI Yogyakarta",             -7.873931,  110.418114),
        ProvinceLocation("Jawa Timur",                -7.536061,  112.238570),
        ProvinceLocation("Bali",                      -8.340539,  115.091980),
        ProvinceLocation("Nusa Tenggara Barat",       -8.652879,  117.361617),
        ProvinceLocation("Nusa Tenggara Timur",       -8.657383,  121.079388),
        ProvinceLocation("Kalimantan Barat",           0.130367,  111.086846),
        ProvinceLocation("Kalimantan Tengah",         -1.681488,  113.382355),
        ProvinceLocation("Kalimantan Selatan",        -3.093276,  115.282875),
        ProvinceLocation("Kalimantan Timur",           0.538680,  116.419389),
        ProvinceLocation("Kalimantan Utara",           3.073050,  116.041749),
        ProvinceLocation("Sulawesi Utara",             0.627021,  123.975048),
        ProvinceLocation("Gorontalo",                  0.699430,  122.446476),
        ProvinceLocation("Sulawesi Tengah",           -1.430791,  121.445617),
        ProvinceLocation("Sulawesi Barat",            -2.840706,  119.232184),
        ProvinceLocation("Sulawesi Selatan",          -3.666685,  119.974036),
        ProvinceLocation("Sulawesi Tenggara",         -4.144943,  122.174605),
        ProvinceLocation("Maluku",                    -3.238796,  130.145288),
        ProvinceLocation("Maluku Utara",               1.571324,  127.808015),
        ProvinceLocation("Papua Barat",               -1.336110,  133.174698),
        ProvinceLocation("Papua",                     -4.269928,  138.080353)
    )

    /**
     * Hitung jarak Haversine dalam km antara dua koordinat.
     */
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Cari nama provinsi terdekat dari koordinat GPS. Fully offline.
     */
    fun nearestProvince(lat: Double, lon: Double): String {
        return PROVINCE_CENTERS.minByOrNull { haversineKm(lat, lon, it.lat, it.lon) }?.name
            ?: "Indonesia"
    }

    /**
     * Ambil lokasi terakhir yang diketahui dari GPS atau Network provider.
     * Tidak memerlukan internet — menggunakan LocationManager bawaan Android.
     * Return null jika permission belum diberikan atau GPS belum pernah fix.
     */
    fun getLastKnownLocation(context: Context): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Coba GPS dulu (lebih akurat), lalu network (lebih cepat)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull { provider ->
            try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null }
        }.maxByOrNull { it.time } // Ambil yang paling baru
    }

    /**
     * Satu fungsi: ambil GPS → kembalikan nama provinsi. Offline sepenuhnya.
     * Return null jika tidak ada lokasi tersedia.
     */
    fun detectProvince(context: Context): String? {
        val loc = getLastKnownLocation(context) ?: return null
        return nearestProvince(loc.latitude, loc.longitude)
    }
}
