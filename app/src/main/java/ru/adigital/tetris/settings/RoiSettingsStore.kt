package ru.adigital.tetris.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import ru.adigital.tetris.vision.NormalizedRoi

private val Context.roiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "roi_settings")

private object RoiKeys {
    val left = floatPreferencesKey("roi_left")
    val top = floatPreferencesKey("roi_top")
    val right = floatPreferencesKey("roi_right")
    val bottom = floatPreferencesKey("roi_bottom")
}

suspend fun Context.loadRoiSettings(): NormalizedRoi? {
    val prefs = roiSettingsDataStore.data.first()
    val l = prefs[RoiKeys.left] ?: return null
    val t = prefs[RoiKeys.top] ?: return null
    val r = prefs[RoiKeys.right] ?: return null
    val b = prefs[RoiKeys.bottom] ?: return null
    return runCatching { NormalizedRoi.fromRaw(l, t, r, b) }.getOrNull()
}

suspend fun Context.saveRoiSettings(roi: NormalizedRoi) {
    roiSettingsDataStore.edit { prefs ->
        prefs[RoiKeys.left] = roi.left
        prefs[RoiKeys.top] = roi.top
        prefs[RoiKeys.right] = roi.right
        prefs[RoiKeys.bottom] = roi.bottom
    }
}
