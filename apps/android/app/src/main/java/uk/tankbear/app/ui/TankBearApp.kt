package uk.tankbear.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.tankbear.app.R
import uk.tankbear.app.ui.theme.TankBearTheme

private enum class Destination(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val status: Int,
) {
    Find(R.string.find, R.drawable.ic_find, R.string.journey_title, R.string.journey_body, R.string.journey_status),
    Car(R.string.car, R.drawable.ic_car, R.string.car_title, R.string.car_body, R.string.car_status),
    Settings(R.string.settings, R.drawable.ic_settings, R.string.settings_title, R.string.settings_body, R.string.settings_status),
}

@Composable
fun TankBearApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.Find) }

    TankBearTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selected == destination,
                            onClick = { selected = destination },
                            icon = {
                                Icon(painterResource(destination.icon), contentDescription = null)
                            },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(selected.title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(selected.body), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(selected.status), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
