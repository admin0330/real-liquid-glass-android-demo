package io.github.admin0330.liquidmusic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.admin0330.liquidmusic.core.preferences.AppPreferences
import io.github.admin0330.liquidmusic.core.preferences.AppearanceMode
import io.github.admin0330.liquidmusic.core.preferences.DefaultRepeatMode
import io.github.admin0330.liquidmusic.core.preferences.UserPreferences
import io.github.admin0330.liquidmusic.core.audio.UsbExclusiveController
import android.content.Intent
import io.github.admin0330.liquidmusic.update.model.UpdateDownloadResult
import io.github.admin0330.liquidmusic.update.model.UpdateInstallResult
import io.github.admin0330.liquidmusic.update.model.UpdateManifest
import io.github.admin0330.liquidmusic.update.repository.UpdateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val usbExclusiveController: UsbExclusiveController,
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    val preferences = appPreferences.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences(),
    )
    val usbAudioState = usbExclusiveController.state
    val updateState = updateRepository.state
    private val _launchIntent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val launchIntent = _launchIntent.asSharedFlow()

    init {
        viewModelScope.launch {
            appPreferences.preferences
                .map { preferences -> preferences.usbExclusiveRequested }
                .distinctUntilChanged()
                .collect { requested -> usbExclusiveController.setExclusive(requested) }
        }
    }

    fun setAppearance(value: AppearanceMode) = viewModelScope.launch { appPreferences.setAppearance(value) }
    fun setShuffle(value: Boolean) = viewModelScope.launch { appPreferences.setDefaultShuffle(value) }
    fun setRepeat(value: DefaultRepeatMode) = viewModelScope.launch { appPreferences.setDefaultRepeat(value) }
    fun setUsbExclusive(value: Boolean) {
        usbExclusiveController.setExclusive(value)
        viewModelScope.launch { appPreferences.setUsbExclusiveRequested(value) }
    }
    fun refreshUsbAudio() = usbExclusiveController.refresh()

    fun checkForUpdate() {
        viewModelScope.launch { updateRepository.checkForUpdate() }
    }

    fun downloadAndInstall(manifest: UpdateManifest) {
        viewModelScope.launch {
            when (val download = updateRepository.download(manifest)) {
                is UpdateDownloadResult.Ready -> launchInstaller(download.manifest)
                is UpdateDownloadResult.Cancelled, is UpdateDownloadResult.Failure -> Unit
            }
        }
    }

    fun installCached(manifest: UpdateManifest) {
        viewModelScope.launch { launchInstaller(manifest) }
    }

    fun cancelUpdateDownload() = updateRepository.cancelDownload()

    private suspend fun launchInstaller(manifest: UpdateManifest) {
        when (val result = updateRepository.prepareInstall(manifest)) {
            is UpdateInstallResult.Ready -> _launchIntent.emit(result.intent)
            is UpdateInstallResult.PermissionRequired -> _launchIntent.emit(result.settingsIntent)
            is UpdateInstallResult.Failure -> Unit
        }
    }
}
