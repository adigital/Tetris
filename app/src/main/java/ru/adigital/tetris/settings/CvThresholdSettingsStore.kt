package ru.adigital.tetris.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.cvThresholdDataStore: DataStore<Preferences> by preferencesDataStore(name = "cv_threshold")

private object CvThresholdKeys {
    /** Смещение порога Оцу в единицах яркости 0..255 (отрицательное — «чувствительнее» к тёмным фигурам). */
    val otsuBias = floatPreferencesKey("otsu_threshold_bias")
}

/** Значение по умолчанию: только авто Оцу. */
const val DefaultCvOtsuThresholdBias = 0f

/** Допустимый диапазон смещения (симметрично). */
const val CvOtsuThresholdBiasRange = 48f

suspend fun Context.loadCvOtsuThresholdBias(): Float {
    val v = cvThresholdDataStore.data.first()[CvThresholdKeys.otsuBias] ?: return DefaultCvOtsuThresholdBias
    return v.coerceIn(-CvOtsuThresholdBiasRange, CvOtsuThresholdBiasRange)
}

suspend fun Context.saveCvOtsuThresholdBias(bias: Float) {
    val clamped = bias.coerceIn(-CvOtsuThresholdBiasRange, CvOtsuThresholdBiasRange)
    cvThresholdDataStore.edit { it[CvThresholdKeys.otsuBias] = clamped }
}
