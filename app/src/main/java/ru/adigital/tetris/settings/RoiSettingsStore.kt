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
    val nextLeft = floatPreferencesKey("roi_next_left")
    val nextTop = floatPreferencesKey("roi_next_top")
    val nextRight = floatPreferencesKey("roi_next_right")
    val nextBottom = floatPreferencesKey("roi_next_bottom")
}

/**
 * Две области: основное стакан + окно «следующая фигура».
 */
data class DualRoiSettings(
    val playfield: NormalizedRoi,
    val nextPiece: NormalizedRoi,
) {
    companion object {
        val Default: DualRoiSettings = DualRoiSettings(
            playfield = NormalizedRoi.Default,
            nextPiece = NormalizedRoi.fromRaw(0.58f, 0.12f, 0.94f, 0.40f),
        )
    }
}

suspend fun Context.loadDualRoiSettings(): DualRoiSettings {
    val prefs = roiSettingsDataStore.data.first()
    val l = prefs[RoiKeys.left]
    val t = prefs[RoiKeys.top]
    val r = prefs[RoiKeys.right]
    val b = prefs[RoiKeys.bottom]
    val playfield = if (l != null && t != null && r != null && b != null) {
        runCatching { NormalizedRoi.fromRaw(l, t, r, b) }.getOrNull()
    } else {
        null
    } ?: DualRoiSettings.Default.playfield

    val nl = prefs[RoiKeys.nextLeft]
    val nt = prefs[RoiKeys.nextTop]
    val nr = prefs[RoiKeys.nextRight]
    val nb = prefs[RoiKeys.nextBottom]
    val nextPiece = if (nl != null && nt != null && nr != null && nb != null) {
        runCatching { NormalizedRoi.fromRaw(nl, nt, nr, nb) }.getOrNull()
    } else {
        null
    } ?: DualRoiSettings.Default.nextPiece

    return DualRoiSettings(playfield = playfield, nextPiece = nextPiece)
}

suspend fun Context.saveDualRoiSettings(settings: DualRoiSettings) {
    roiSettingsDataStore.edit { prefs ->
        prefs[RoiKeys.left] = settings.playfield.left
        prefs[RoiKeys.top] = settings.playfield.top
        prefs[RoiKeys.right] = settings.playfield.right
        prefs[RoiKeys.bottom] = settings.playfield.bottom
        prefs[RoiKeys.nextLeft] = settings.nextPiece.left
        prefs[RoiKeys.nextTop] = settings.nextPiece.top
        prefs[RoiKeys.nextRight] = settings.nextPiece.right
        prefs[RoiKeys.nextBottom] = settings.nextPiece.bottom
    }
}
