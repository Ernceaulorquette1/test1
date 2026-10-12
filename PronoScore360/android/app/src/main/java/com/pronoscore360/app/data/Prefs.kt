package com.pronoscore360.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("ps360_prefs")

/** Preferencias locales (DataStore): tema y idioma. */
class Prefs(private val context: Context) {
    private val THEME = stringPreferencesKey("theme")
    private val LANG = stringPreferencesKey("lang")
    val theme: Flow<String> = context.store.data.map { it[THEME] ?: "system" }
    val language: Flow<String> = context.store.data.map { it[LANG] ?: "es" }
    suspend fun setTheme(v: String) { context.store.edit { it[THEME] = v } }
    suspend fun setLanguage(v: String) { context.store.edit { it[LANG] = v } }
}
