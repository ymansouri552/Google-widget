package com.googlewidget.calendar.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.googlewidget.calendar.ui.theme.CalendarTheme

/**
 * The signature violet pair marks the selected day and the one primary action.
 * Both stops stay dark enough for white label text to clear WCAG AA.
 */
internal fun neonAccent(theme: CalendarTheme): Brush {
    val base = theme.todayCircleBg
    val darker = Color(base.red * 0.82f, base.green * 0.82f, base.blue * 0.95f, 1f)
    return Brush.linearGradient(listOf(darker, base))
}

/**
 * One low-contrast violet wash in the top corner. It sits behind the content to
 * separate the app from system screens, not to tint every surface violet.
 */
internal fun Modifier.neonAppBackground(theme: CalendarTheme): Modifier = this
    .background(theme.bg)
    .background(
        Brush.radialGradient(
            colors = listOf(Color(0x292A1B5C), Color(0x0C171530), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(0f, 0f),
            radius = 1000f
        )
    )

internal data class NeonTab(val label: String, val icon: ImageVector)

/**
 * Five-slot bar with the add action raised in the centre. The extra height gives
 * the action room without shrinking the four text-and-icon touch targets.
 */
@Composable
internal fun NeonBottomBar(
    theme: CalendarTheme,
    tabs: List<NeonTab>,
    selected: Int,
    onTab: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(theme.bg.copy(alpha = 0.96f))
            .border(androidx.compose.foundation.BorderStroke(1.dp, theme.grid.copy(alpha = 0.7f)))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                if (index == 2) Spacer(Modifier.width(64.dp))
                NeonTabItem(
                    tab = tab,
                    active = selected == index,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = { onTab(index) }
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .size(54.dp)
                .shadow(10.dp, CircleShape, spotColor = theme.todayCircleBg.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(neonAccent(theme))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add task",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NeonTabItem(
    tab: NeonTab,
    active: Boolean,
    theme: CalendarTheme,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = if (active) theme.todayCircleBg else theme.sub,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            color = if (active) theme.text else theme.sub,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
internal fun NeonSectionTitle(
    text: String,
    theme: CalendarTheme,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = theme.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun NeonPillButton(
    text: String,
    theme: CalendarTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(neonAccent(theme))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun NeonChip(text: String, theme: CalendarTheme, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(theme.chipBg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = theme.chipText, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
internal fun NeonSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    theme: CalendarTheme,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(theme.chipBg.copy(alpha = 0.75f), RoundedCornerShape(15.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = theme.sub,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(theme.todayCircleBg),
                textStyle = TextStyle(color = theme.text, fontSize = 14.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "Search events and tasks",
                            color = theme.sub,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    inner()
                }
            )
        }
    }
}

@Composable
internal fun NeonAvatar(theme: CalendarTheme, label: String) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(neonAccent(theme))
            .border(2.dp, theme.text.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun NeonCaption(text: String, theme: CalendarTheme, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = theme.sub,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
