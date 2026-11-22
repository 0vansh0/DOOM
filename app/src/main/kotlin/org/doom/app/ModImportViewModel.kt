package org.doom.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.doom.data.ImportCandidate
import org.doom.data.ModImportCoordinator
import org.doom.domain.ModResult
import java.io.IOException
import javax.inject.Inject

internal sealed interface ImportState {
    data object Idle : ImportState
    data object Validating : ImportState
    data object Closing : ImportState
    data class Review(val candidate: ImportCandidate) : ImportState
    data object Installing : ImportState
    data class Installed(val name: String) : ImportState
    data class Failed(val message: String) : ImportState
}

@HiltViewModel
internal class ModImportViewModel @Inject constructor(private val coordinator: ModImportCoordinator) : ViewModel() {
    private val mutableState = MutableStateFlow<ImportState>(ImportState.Idle)
    val state = mutableState.asStateFlow()
    private var operation: Job? = null

    fun select(uri: String?) {
        if (uri == null || state.value != ImportState.Idle) return
        mutableState.value = ImportState.Validating
        runOperation { ImportState.Review(coordinator.review(uri)) }
    }

    fun install() {
        val candidate = (state.value as? ImportState.Review)?.candidate ?: return
        if (!candidate.compatible) return
        mutableState.value = ImportState.Installing
        runOperation {
            when (val result = coordinator.install(candidate)) {
                ModResult.Success -> ImportState.Installed(candidate.manifest.name)
                is ModResult.Rejected -> ImportState.Failed(result.message)
            }
        }
    }

    fun dismiss() {
        if (state.value == ImportState.Installing || state.value == ImportState.Closing) return
        val previous = operation
        mutableState.value = ImportState.Closing
        runOperation {
            previous?.cancelAndJoin()
            coordinator.discard()
            ImportState.Idle
        }
    }

    private fun runOperation(action: suspend () -> ImportState) {
        operation = viewModelScope.launch {
            mutableState.value = try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                ImportState.Failed("Could not read or save the package. Check the document and available storage.")
            } catch (error: Exception) {
                ImportState.Failed(error.message?.take(180) ?: "Could not validate the package.")
            }
        }
    }
}
