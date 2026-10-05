package uk.tankbear.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.store by preferencesDataStore(name = "tank_bear")

data class Saved(
    val fuel: FuelType = FuelType.E10,
    val mpg: String = "",
    val litres: String = "30",
    val baseUrl: String = "http://10.0.2.2:8787",
    val appKey: String = "",
    val places: List<SavedPlace> = emptyList(),
)

/** Local-only preferences. Excluded from backup and cloud transfer by the app's backup rules. */
class Prefs(private val context: Context) {
    private val fuelKey = stringPreferencesKey("fuel")
    private val mpgKey = stringPreferencesKey("mpg")
    private val litresKey = stringPreferencesKey("litres")
    private val urlKey = stringPreferencesKey("base_url")
    private val keyKey = stringPreferencesKey("app_key")
    private val placesKey = stringPreferencesKey("places")

    suspend fun load(): Saved {
        val p = context.store.data.first()
        val d = Saved()
        return Saved(
            fuel = FuelType.entries.firstOrNull { it.api == p[fuelKey] } ?: d.fuel,
            mpg = p[mpgKey] ?: d.mpg,
            litres = p[litresKey] ?: d.litres,
            baseUrl = p[urlKey] ?: d.baseUrl,
            appKey = p[keyKey] ?: d.appKey,
            places = p[placesKey]?.let { Places.decode(it) } ?: d.places,
        )
    }

    suspend fun save(s: Saved) {
        context.store.edit {
            it[fuelKey] = s.fuel.api
            it[mpgKey] = s.mpg
            it[litresKey] = s.litres
            it[urlKey] = s.baseUrl
            it[keyKey] = s.appKey
            it[placesKey] = Places.encode(s.places)
        }
    }
}
