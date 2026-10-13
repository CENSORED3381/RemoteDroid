package com.remotedroid.app.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.remotedroid.app.core.model.ConnectionHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "remotedroid_connections")

/**
 * Guarda o historico de conexoes (host:porta:apelido) localmente no aparelho
 * que roda o RemoteDroid (aparelho A), para reconectar rapido ao alvo (C).
 */
class ConnectionHistoryStore(private val context: Context) {

    private val key = stringSetPreferencesKey("history_entries")

    val history: Flow<List<ConnectionHistoryItem>> = context.dataStore.data.map { prefs ->
        (prefs[key] ?: emptySet())
            .mapNotNull { ConnectionHistoryItem.decode(it) }
            .sortedByDescending { it.lastConnectedAt }
    }

    suspend fun upsert(item: ConnectionHistoryItem) {
        context.dataStore.edit { prefs ->
            val current = (prefs[key] ?: emptySet())
                .mapNotNull { ConnectionHistoryItem.decode(it) }
                .filterNot { it.host == item.host && it.port == item.port }
                .toMutableList()
            current.add(item)
            prefs[key] = current.map { it.encode() }.toSet()
        }
    }

    suspend fun remove(item: ConnectionHistoryItem) {
        context.dataStore.edit { prefs ->
            val current = (prefs[key] ?: emptySet())
                .mapNotNull { ConnectionHistoryItem.decode(it) }
                .filterNot { it.host == item.host && it.port == item.port }
            prefs[key] = current.map { it.encode() }.toSet()
        }
    }
}
