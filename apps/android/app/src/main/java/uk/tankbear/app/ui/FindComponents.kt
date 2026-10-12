package uk.tankbear.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.tankbear.app.R
import uk.tankbear.app.ui.theme.TankBearTokens
import uk.tankbear.app.ui.theme.TankBearType

@Composable
internal fun FindBrand() {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TankBearTokens.space.dp12)) {
        Image(painterResource(if (androidx.compose.foundation.isSystemInDarkTheme()) R.drawable.bear_brand_dark else R.drawable.bear_brand), contentDescription = null, modifier = Modifier.size(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TankBearTokens.space.dp4)) {
            Text("Tank Bear", style = TankBearType.title)
            Text(stringResource(R.string.tagline), style = TankBearType.body, color = TankBearTokens.colors.muted)
        }
    }
}

/** The whole row is one accessible radio target; the indicator has no separate action. */
@Composable
internal fun JourneyChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = TankBearTokens.touch.min)
        .selectable(selected, role = Role.RadioButton, onClick = onSelect)
        .padding(vertical = TankBearTokens.space.dp12),
        horizontalArrangement = Arrangement.spacedBy(TankBearTokens.space.dp12),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, modifier = Modifier.size(24.dp).scale(1.2f))
        Text(label, Modifier.weight(1f), style = TankBearType.body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) TankBearTokens.colors.primary else TankBearTokens.colors.text)
    }
}

@Composable
internal fun FindPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = TankBearTokens.touch.min), enabled = enabled,
        shape = RoundedCornerShape(TankBearTokens.radius.pill),
        colors = ButtonDefaults.buttonColors(containerColor = TankBearTokens.colors.soft, contentColor = TankBearTokens.colors.text),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
        Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = TankBearType.label)
    }
}

@Composable
internal fun LocationSummary(label: String, selected: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(TankBearTokens.radius.card),
        color = TankBearTokens.colors.surface, border = BorderStroke(1.dp, TankBearTokens.colors.outline)) {
        Column(Modifier.padding(TankBearTokens.space.dp16), verticalArrangement = Arrangement.spacedBy(TankBearTokens.space.dp4)) {
            Text(label, style = TankBearType.caption, color = TankBearTokens.colors.muted)
            Text(if (selected) "Location selected" else "Not set", style = TankBearType.body)
        }
    }
}
