package com.remotedroid.app.ui.screens.mirror

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remotedroid.app.core.adb.MirrorStreamer
import com.remotedroid.app.core.model.MirrorQuality
import com.remotedroid.app.core.model.MirrorState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MirrorViewModel(application: Application) : AndroidViewModel(application) {

    private val streamer = MirrorStreamer(context = application.applicationContext)

    val state: StateFlow<MirrorState> = streamer.state

    var quality: MirrorQuality = MirrorQuality.MEDIA
        private set

    private var currentSurface: Surface? = null

    fun setQuality(newQuality: MirrorQuality) {
        quality = newQuality
        // se ja estiver rodando com outra qualidade, reinicia a sessao com a nova
        val surface = currentSurface
        if (surface != null && state.value !is MirrorState.Idle) {
            streamer.stop()
            viewModelScope.launch { streamer.start(surface, quality) }
        }
    }

    fun onSurfaceAvailable(surface: Surface) {
        currentSurface = surface
        viewModelScope.launch { streamer.start(surface, quality) }
    }

    fun onSurfaceDestroyed() {
        currentSurface = null
        streamer.stop()
    }

    override fun onCleared() {
        super.onCleared()
        streamer.stop()
    }
}
