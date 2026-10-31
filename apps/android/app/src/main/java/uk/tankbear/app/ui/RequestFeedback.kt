package uk.tankbear.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Display-only timer. Request entry points also enforce the server deadline. */
@Composable
internal fun retrySeconds(vm: AppViewModel): Long {
    var seconds by remember(vm) { mutableLongStateOf(vm.retrySecondsRemaining) }
    LaunchedEffect(vm, vm.retryUntil, vm.saved.baseUrl, vm.saved.appKey) {
        do {
            seconds = vm.retrySecondsRemaining
            if (seconds > 0) delay(250)
        } while (seconds > 0)
    }
    return seconds
}

@Composable
internal fun RequestLoading(message: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
    }
}
