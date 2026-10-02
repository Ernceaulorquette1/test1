package cl.driverlink.app.presentation.company

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import cl.driverlink.app.R
import cl.driverlink.app.core.ui.UiState
import cl.driverlink.app.domain.model.Vehicle
import cl.driverlink.app.domain.model.VehicleDraft
import cl.driverlink.app.domain.model.VehicleType
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.EmptyView
import cl.driverlink.app.ui.components.StateContent

private fun VehicleType.labelRes(): Int = when (this) {
    VehicleType.CAR -> R.string.vehicle_type_car
    VehicleType.VAN -> R.string.vehicle_type_van
    VehicleType.TRUCK -> R.string.vehicle_type_truck
    VehicleType.MOTORCYCLE -> R.string.vehicle_type_motorcycle
    VehicleType.OTHER -> R.string.vehicle_type_other
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehiclesScreen(onBack: () -> Unit) {
    val viewModel = appViewModel {
        VehiclesViewModel(createSavedStateHandle(), it.vehicleRepository, it.companyRepository, it.createVehicleUseCase)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf<Vehicle?>(null) }
    val canManage = (state as? UiState.Success)?.data?.canManage == true

    Scaffold(
        topBar = { DriverLinkTopBar(stringResource(R.string.company_vehicles), onBack = onBack) },
        floatingActionButton = {
            if (canManage) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.vehicle_add)) },
                )
            }
        },
    ) { padding ->
        StateContent(state, emptyMessage = "", modifier = Modifier.padding(padding)) { data ->
            Column(Modifier.padding(padding)) {
                message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                if (data.vehicles.isEmpty()) EmptyView(stringResource(R.string.vehicles_empty))
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(data.vehicles, key = { it.id }) { vehicle ->
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.DirectionsCar, contentDescription = null) },
                            headlineContent = { Text("${vehicle.plate} · ${vehicle.brand} ${vehicle.model} ${vehicle.year}") },
                            supportingContent = {
                                Text(
                                    stringResource(vehicle.type.labelRes()) + " · " +
                                        (vehicle.assignedDriverName ?: stringResource(R.string.vehicle_unassigned))
                                )
                            },
                            modifier = if (data.canAssign) Modifier.clickable { assigning = vehicle } else Modifier,
                        )
                        HorizontalDivider()
                    }
                }
            }
            assigning?.let { vehicle ->
                AlertDialog(
                    onDismissRequest = { assigning = null },
                    title = { Text(stringResource(R.string.vehicle_assign_title, vehicle.plate)) },
                    text = {
                        Column {
                            TextButton(onClick = { viewModel.assign(vehicle, null); assigning = null }) {
                                Text(stringResource(R.string.vehicle_unassigned))
                            }
                            data.drivers.forEach { driver ->
                                TextButton(onClick = { viewModel.assign(vehicle, driver); assigning = null }) { Text(driver.displayName) }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { assigning = null }) { Text(stringResource(R.string.action_cancel)) } },
                )
            }
        }
    }

    if (adding) {
        var plate by remember { mutableStateOf("") }
        var brand by remember { mutableStateOf("") }
        var model by remember { mutableStateOf("") }
        var year by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(VehicleType.CAR) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.vehicle_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(plate, { plate = it.take(8) }, label = { Text(stringResource(R.string.vehicle_plate)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(brand, { brand = it.take(30) }, label = { Text(stringResource(R.string.vehicle_brand)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(model, { model = it.take(30) }, label = { Text(stringResource(R.string.vehicle_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        year, { year = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.vehicle_year)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VehicleType.entries.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringResource(t.labelRes())) })
                        }
                    }
                    message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.create(VehicleDraft(plate, brand, model, year.toIntOrNull() ?: 0, type)) { adding = false }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
