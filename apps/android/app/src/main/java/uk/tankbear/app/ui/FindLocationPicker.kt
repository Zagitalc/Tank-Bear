package uk.tankbear.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.tankbear.app.data.Validation
import uk.tankbear.app.ui.theme.TankBearTokens

@Composable
internal fun FindLocationRow(vm: AppViewModel, target: MapTarget, onChoose: () -> Unit) {
    val selected = if (target == MapTarget.Start) Validation.point(vm.originLat, vm.originLon) != null
        else Validation.point(vm.destLat, vm.destLon) != null
    Surface(onClick = onChoose, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("choose-${target.name.lowercase()}"),
        shape = RoundedCornerShape(TankBearTokens.radius.card), color = TankBearTokens.colors.surface,
        border = BorderStroke(1.dp, TankBearTokens.colors.outline)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(target.name, style = MaterialTheme.typography.labelMedium, color = TankBearTokens.colors.muted)
                Text(vm.locationDescription(target), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(if (selected) "Change" else "Choose", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FindLocationPicker(vm: AppViewModel, target: MapTarget, onDismiss: () -> Unit, onShowOnMap: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose your ${target.name.lowercase()}", style = MaterialTheme.typography.headlineSmall)
            Text(vm.locationDescription(target), style = MaterialTheme.typography.bodyMedium, color = TankBearTokens.colors.muted)
            FindPill("Choose ${target.name.lowercase()} on map", onClick = {
                vm.beginMapChoice(target)
                onDismiss()
                onShowOnMap()
            })
            if (target == MapTarget.Start) LocationButton(vm, onLocationChosen = onDismiss)
            if (vm.saved.places.isNotEmpty()) {
                Text("Saved places", style = MaterialTheme.typography.titleMedium)
                vm.saved.places.forEach { place ->
                    FindPill(place.name, onClick = { vm.usePlace(place, asDestination = target == MapTarget.Destination); onDismiss() })
                }
            }
            PlaceSearch(vm, target, onShowOnMap = { onDismiss(); onShowOnMap() })
        }
    }
}
