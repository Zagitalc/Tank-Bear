package uk.tankbear.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import uk.tankbear.app.data.LatLon

/** Asks the phone for its position once, when the person taps for it. Nothing is tracked or kept. */
object LocationFetcher {
    sealed interface Result {
        data class Found(val point: LatLon) : Result
        /** Location is switched off, or no provider is available. */
        data object Unavailable : Result
        /** The phone gave no position, for example indoors with no signal. */
        data object NoFix : Result
    }

    // The caller has checked the permission (fine or approximate) before calling this.
    @SuppressLint("MissingPermission")
    fun getOnce(context: Context, onResult: (Result) -> Unit): CancellationSignal? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) }
        if (provider == null) {
            onResult(Result.Unavailable)
            return null
        }
        val signal = CancellationSignal()
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
            onResult(if (location == null) Result.NoFix else Result.Found(LatLon(location.latitude, location.longitude)))
        }
        return signal
    }
}
