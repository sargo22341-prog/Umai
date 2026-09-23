package org.opensources.umai.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opensources.umai.R
import org.opensources.umai.core.ui.component.UserAvatar

/**
 * Bottom navigation with search promoted to the centre.
 *
 * `Home | Planning | SEARCH | Shopping | Profile`
 *
 * Every slot keeps the same width so the bar stays balanced, and labels are
 * allowed to ellipsize rather than push the bar out of shape when the system
 * font size is enlarged or the user chose a long display name.
 */
@Composable
fun UmaiBottomBar(
    selected: TopLevelTab?,
    searchSelected: Boolean,
    onSelect: (TopLevelTab) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    profile: ProfileTabInfo? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = 72.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TopLevelTab.leading.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                    profile = profile,
                )
            }

            SearchAction(
                selected = searchSelected,
                onClick = onSearch,
                modifier = Modifier.weight(1f),
            )

            TopLevelTab.trailing.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                    profile = profile,
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: TopLevelTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    profile: ProfileTabInfo? = null,
) {
    val avatar = profile?.takeIf { tab == TopLevelTab.PROFILE }
    val label = avatar?.displayName?.takeIf { it.isNotBlank() } ?: stringResource(tab.labelRes)
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (avatar != null) {
            UserAvatar(
                url = avatar.avatarUrl,
                initials = avatar.initials,
                // The label right below already names the destination.
                contentDescription = null,
                modifier = if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
                size = 26.dp,
                textStyle = 11.sp,
            )
        } else {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchAction(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.nav_search)
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
