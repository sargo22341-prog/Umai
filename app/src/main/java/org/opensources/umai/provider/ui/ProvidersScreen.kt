package org.opensources.umai.provider.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.provider.RecipeProvider

@Composable
fun ProvidersScreen(onBack: () -> Unit, onOpenProvider: (String) -> Unit, modifier: Modifier = Modifier) {
    ProvidersScreen(
        providers = LocalAppContainer.current.providerRegistry.providers,
        onBack = onBack,
        onOpenProvider = onOpenProvider,
        modifier = modifier,
    )
}

/** The recipe websites the app knows more about than Mealie does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    providers: List<RecipeProvider>,
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.providers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.providers_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(providers, key = { it.id }) { provider ->
                ListItem(
                    headlineContent = { Text(provider.name) },
                    supportingContent = { Text(stringResource(provider.descriptionRes), maxLines = 2) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onOpenProvider(provider.id) },
                )
            }
        }
    }
}
