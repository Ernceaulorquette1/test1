package cl.driverlink.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.AlertCategory

/** Estilo visual de cada categoría: color + ícono + texto (nunca solo color). */
data class AlertStyle(val color: Color, val icon: ImageVector, @StringRes val label: Int, val markerHue: Float)

fun AlertCategory.style(): AlertStyle = when (this) {
    AlertCategory.ACCIDENT -> AlertStyle(AlertAccident, Icons.Filled.CarRepair, R.string.alert_category_accident, 20f)
    AlertCategory.TRAFFIC -> AlertStyle(AlertTraffic, Icons.Filled.Traffic, R.string.alert_category_traffic, 50f)
    AlertCategory.ROAD_CLOSED -> AlertStyle(AlertRoadClosed, Icons.Filled.Block, R.string.alert_category_road_closed, 280f)
    AlertCategory.DANGER -> AlertStyle(AlertDanger, Icons.Filled.Warning, R.string.alert_category_danger, 0f)
    AlertCategory.SECURITY -> AlertStyle(AlertSecurity, Icons.Filled.Shield, R.string.alert_category_security, 230f)
    AlertCategory.ROAD_PROBLEM -> AlertStyle(AlertRoadProblem, Icons.Filled.Construction, R.string.alert_category_road_problem, 30f)
    AlertCategory.EMERGENCY -> AlertStyle(AlertEmergency, Icons.Filled.LocalHospital, R.string.alert_category_emergency, 350f)
    AlertCategory.OTHER -> AlertStyle(AlertOther, Icons.Filled.Report, R.string.alert_category_other, 200f)
}
