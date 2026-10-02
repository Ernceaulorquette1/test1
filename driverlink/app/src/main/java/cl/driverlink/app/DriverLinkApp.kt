package cl.driverlink.app

import android.app.Application
import cl.driverlink.app.di.AppContainer
import kotlinx.coroutines.launch

class DriverLinkApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.createChannels()
        container.applicationScope.launch { container.configRepository.refresh() }
    }
}
