package cl.driverlink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.driverlink.app.navigation.DriverLinkRoot
import cl.driverlink.app.ui.theme.DriverLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriverLinkTheme {
                DriverLinkRoot()
            }
        }
    }
}
