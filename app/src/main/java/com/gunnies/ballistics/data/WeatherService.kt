package com.gunnies.ballistics.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import com.gunnies.ballistics.model.Atmosphere
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class WeatherResult(
    val atmosphere: Atmosphere,
    val summary: String
)

/**
 * Fetches live local temperature, station pressure, relative humidity, elevation, wind speed,
 * wind gusts, and meteorological wind direction using Open-Meteo from the phone's GPS coordinates.
 */
object WeatherService {

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun compassCardinal(deg: Double): String {
        val dirs = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val normalized = ((deg % 360.0) + 360.0) % 360.0
        val idx = ((normalized + 11.25) / 22.5).toInt() % 16
        return dirs[idx]
    }

    /**
     * Converts meteorological wind direction (compass deg wind blows FROM) and shooter's
     * target bearing (compass deg shooter is facing) into relative shooter wind clock degrees
     * (0 = headwind / 12 o'clock, 90 = from right / 3 o'clock, etc.).
     */
    fun relativeWindClockDeg(meteoWindFromDeg: Double, shootingBearingDeg: Double): Double {
        return ((meteoWindFromDeg - shootingBearingDeg) % 360.0 + 360.0) % 360.0
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchLocalAtmosphere(
        context: Context,
        shootingBearingDeg: Double
    ): Result<WeatherResult> = withContext(Dispatchers.IO) {
        runCatching {
            val location = getFreshOrLastKnownLocation(context)
                ?: error("No GPS fix available yet. Enable phone Location/GPS and try again.")

            val lat = location.latitude
            val lon = location.longitude
            val urlStr = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                "&temperature_unit=fahrenheit&wind_speed_unit=mph"

            val conn = (URI(urlStr).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }

            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("Weather service returned HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val current = root.getJSONObject("current")
                val elevationMeters = root.optDouble("elevation", 0.0)

                val tempF = current.getDouble("temperature_2m")
                val humidity = current.getDouble("relative_humidity_2m")
                val surfacePressureHpa = current.getDouble("surface_pressure")
                val windMph = current.getDouble("wind_speed_10m")
                val windDirFromDeg = current.optDouble("wind_direction_10m", 0.0)
                val windGustMph = current.optDouble("wind_gusts_10m", windMph)

                val pressureInHg = surfacePressureHpa / UnitFormatters.INHG_TO_HPA
                val altFeet = if (location.hasAltitude() && location.altitude != 0.0) {
                    location.altitude * 3.28084
                } else {
                    elevationMeters * 3.28084
                }

                val relWindDeg = relativeWindClockDeg(windDirFromDeg, shootingBearingDeg)
                val updatedStr = LocalTime.now().format(timeFmt)

                val atmo = Atmosphere(
                    temperatureF = tempF,
                    pressureInHg = pressureInHg,
                    pressureIsStation = true,
                    altitudeFeet = altFeet,
                    humidityPercent = humidity,
                    windSpeedMph = windMph,
                    windGustMph = windGustMph,
                    windDirectionDeg = relWindDeg,
                    meteoWindFromDeg = windDirFromDeg,
                    shootingBearingDeg = shootingBearingDeg,
                    gpsLatitude = lat,
                    gpsLongitude = lon,
                    weatherUpdatedAt = updatedStr
                )

                val card = compassCardinal(windDirFromDeg)
                WeatherResult(
                    atmosphere = atmo,
                    summary = "Synced $updatedStr • ${tempF.roundToInt()}°F • Wind ${windMph.roundToInt()} mph $card"
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshOrLastKnownLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) {
                val fresh = withTimeoutOrNull(4000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        runCatching {
                            lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        }.onFailure {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (fresh != null) return fresh
            }
        }

        val providers = lm.getProviders(true)
        var best: Location? = null
        for (provider in providers) {
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || loc.time > best.time) {
                best = loc
            }
        }
        return best
    }
}
