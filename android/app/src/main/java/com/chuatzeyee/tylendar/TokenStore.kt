package com.chuatzeyee.tylendar

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/* Lands in files/datastore/, which data_extraction_rules.xml excludes
   from every backup path: the token must never leave the device. */
private val Context.dataStore by preferencesDataStore(name = "tylendar")

object TokenStore {
    private val KEY = stringPreferencesKey("token")

    fun flow(context: Context): Flow<String?> =
        context.dataStore.data.map { it[KEY]?.takeIf { t -> t.isNotBlank() } }

    suspend fun set(context: Context, token: String?) {
        context.dataStore.edit {
            if (token.isNullOrBlank()) it.remove(KEY) else it[KEY] = token
        }
    }
}
