package cl.driverlink.app.presentation.company

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.MemberStatus
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.Avatar
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.StateContent
import cl.driverlink.app.ui.components.initialsOf

@Composable
fun CompanyMembersScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { CompanyMembersViewModel(createSavedStateHandle(), it.companyRepository, it.authRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.company_members), onBack = onBack) }) { padding ->
        StateContent(state, emptyMessage = "", modifier = Modifier.padding(padding)) { data ->
            Column(Modifier.padding(padding)) {
                message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                LazyColumn {
                    items(data.members, key = { it.userId }) { member ->
                        ListItem(
                            leadingContent = { Avatar(null, initialsOf(member.displayName)) },
                            headlineContent = { Text(member.displayName) },
                            supportingContent = {
                                Text(stringResource(member.role.labelRes()) + " · " + stringResource(member.status.labelRes()))
                            },
                            trailingContent = {
                                if (data.canApprove && member.status == MemberStatus.PENDING) {
                                    Row {
                                        TextButton(onClick = { viewModel.review(member, false) }) { Text(stringResource(R.string.action_reject)) }
                                        TextButton(onClick = { viewModel.review(member, true) }) { Text(stringResource(R.string.action_approve)) }
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
