package cl.driverlink.app.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.ui.UiState
import cl.driverlink.app.core.ui.toListUiState
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelMode
import cl.driverlink.app.domain.model.MemberStatus
import cl.driverlink.app.domain.repository.ChannelRepository
import cl.driverlink.app.domain.repository.CompanyRepository
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.StateContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ChannelSection(val title: String, val channels: List<Channel>)

/** Separa los espacios COMUNIDAD y MI EMPRESA con la misma cuenta. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsViewModel(
    channelRepository: ChannelRepository,
    companyRepository: CompanyRepository,
) : ViewModel() {

    val community: StateFlow<UiState<List<ChannelSection>>> =
        channelRepository.observeCommunityChannels(ChannelMode.TEXT)
            .map { channels ->
                channels.groupBy { it.region.ifBlank { it.city } }
                    .map { (region, list) -> ChannelSection(region, list) }
                    .toListUiState()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val company: StateFlow<UiState<List<ChannelSection>>> =
        companyRepository.observeMyMemberships()
            .map { memberships -> memberships.filter { it.member.status == MemberStatus.ACTIVE } }
            .flatMapLatest { memberships ->
                if (memberships.isEmpty()) flowOf(emptyList())
                else combine(memberships.map { m ->
                    channelRepository.observeCompanyChannels(m.company.id, ChannelMode.TEXT)
                        .map { ChannelSection(m.company.tradeName, it) }
                }) { it.toList() }
            }
            .map { it.toListUiState() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}

@Composable
fun ChannelsScreen(onOpenChannel: (String, String?) -> Unit, onOpenCompany: () -> Unit) {
    val viewModel = appViewModel { ChannelsViewModel(it.channelRepository, it.companyRepository) }
    val community by viewModel.community.collectAsStateWithLifecycle()
    val company by viewModel.company.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.channels_title)) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.space_community)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.space_company)) })
            }
            if (tab == 0) {
                StateContent(community, emptyMessage = stringResource(R.string.channels_empty)) { sections ->
                    ChannelList(sections, onOpenChannel)
                }
            } else {
                Card(onClick = onOpenCompany, modifier = Modifier.padding(16.dp)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.company_manage_spaces)) },
                        leadingContent = { Icon(Icons.Filled.Business, contentDescription = null) },
                    )
                }
                StateContent(company, emptyMessage = stringResource(R.string.channels_company_empty)) { sections ->
                    ChannelList(sections, onOpenChannel)
                }
            }
        }
    }
}

@Composable
private fun ChannelList(sections: List<ChannelSection>, onOpenChannel: (String, String?) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
        sections.forEach { section ->
            item(key = "header_${section.title}") {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(section.channels, key = { "${it.companyId}_${it.id}" }) { channel ->
                ListItem(
                    headlineContent = { Text(channel.name) },
                    supportingContent = { if (channel.description.isNotBlank()) Text(channel.description) },
                    leadingContent = {
                        Icon(
                            if (channel.companyId != null) Icons.Filled.Lock else Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickableListItem { onOpenChannel(channel.id, channel.companyId) },
                )
                HorizontalDivider()
            }
        }
    }
}

private fun Modifier.clickableListItem(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
