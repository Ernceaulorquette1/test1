package cl.driverlink.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.driverlink.app.DriverLinkApp
import cl.driverlink.app.di.AppContainer

/**
 * Crea un ViewModel con dependencias del [AppContainer].
 * Dentro de [create] se puede usar `createSavedStateHandle()` para argumentos de navegación.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: CreationExtras.(AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as DriverLinkApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as DriverLinkApp).container
