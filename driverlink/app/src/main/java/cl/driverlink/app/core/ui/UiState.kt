package cl.driverlink.app.core.ui

/** Estados estándar de cualquier pantalla que carga información. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: UiText) : UiState<Nothing>
}

fun <T> List<T>.toListUiState(): UiState<List<T>> =
    if (isEmpty()) UiState.Empty else UiState.Success(this)
