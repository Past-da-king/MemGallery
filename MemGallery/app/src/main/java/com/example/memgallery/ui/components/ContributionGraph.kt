package com.example.memgallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Composable
fun ContributionGraph(
    data: Map<LocalDate, Int>,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Config
    val daysRows = 7
    val boxSize = 12.dp
    val spacing = 4.dp
    val boxWithSpacing = boxSize + spacing
    
    // Calculate how many weeks fit
    // Padding (16*2) + text labels (optional) -> Let's assume full width minus standard padding
    val availableWidth = screenWidth - 32.dp 
    val visibleWeeks = (availableWidth / boxWithSpacing).toInt().coerceAtLeast(10)

    val today = LocalDate.now()
    // Align end date to the end of the current week (Saturday) to keep the grid aligned
    // Or just use today.
    // GitHub ends on today (usually). But usually fills the grid.
    // Let's iterate backwards.
    
    // We want the last column to contain 'today'.
    // If today is Wednesday, the last column has top 3 squares filled (Sun, Mon, Tue) + Wed.
    // The visual grid is usually Sunday-based rows (0=Sun, 6=Sat).
    
    // Ensure we start on a Sunday 'visibleWeeks' ago.
    val endDay = today
    // To make the grid nice, let's start 'visibleWeeks' * 7 days ago.
    // But we need to align the start date to a Sunday.
    
    val totalDaysToShow = visibleWeeks * 7
    val startDate = endDay.minusDays(totalDaysToShow.toLong() - 1)
    // Find the Sunday before or on startDate
    val gridStartDate = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    
    // Re-calculate visible weeks based on gridStartDate to Today+Offset to fill the grid
    // Actually simpler: Just render 'visibleWeeks' columns.
    // Column 0 is the oldest week. Column N is the current week.
    
    Column(modifier = modifier) {
        // Optional Title or Month labels could go here
        
        Row(modifier = Modifier.fillMaxWidth()) {
            // We iterate columns (Weeks)
            for (weekIndex in 0 until visibleWeeks) {
                 Column(
                     verticalArrangement = Arrangement.spacedBy(spacing)
                 ) {
                     for (dayIndex in 0 until daysRows) {
                         // Calculate date for this cell
                         // gridStartDate is Sunday of the first week.
                         val daysToAdd = (weekIndex * 7) + dayIndex
                         val date = gridStartDate.plusDays(daysToAdd.toLong())
                         
                         // Don't render future days if we want strict behavior, 
                         // but typically we just show empty squares for future in the current week.
                         val isFuture = date.isAfter(today)
                         
                         val count = data[date] ?: 0
                         val color = if (isFuture) {
                             Color.Transparent // Or background color? Usually invisible or very faint
                         } else {
                             getColorForActivity(count, primaryColor)
                         }
                         
                         // Tooltip logic could be added here (Box with clickable)
                         Box(
                             modifier = Modifier
                                 .size(boxSize)
                                 .clip(RoundedCornerShape(2.dp))
                                 .background(
                                     color = if (isFuture) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else color
                                 )
                         )
                     }
                 }
                 Spacer(modifier = Modifier.width(spacing))
            }
        }
        
        // Legend or Metadata
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Less", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            
            val legendColors = listOf(0, 1, 3, 6, 10).map { getColorForActivity(it, primaryColor) }
            legendColors.take(5).forEach { color -> // Just 5 steps
                 Box(
                     modifier = Modifier
                         .size(10.dp)
                         .clip(RoundedCornerShape(2.dp))
                         .background(color)
                 )
                 Spacer(modifier = Modifier.width(2.dp))
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            Text("More", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun getColorForActivity(count: Int, primaryColor: Color): Color {
    // GitHub logic roughly:
    // 0 -> bg (ebedf0)
    // 1 -> level 1 (9be9a8)
    // ...
    // Let's use alpha/saturation of primary color
    
    return if (count == 0) {
        MaterialTheme.colorScheme.surfaceVariant // Empty state
    } else {
        // Scale alpha based on count
        val alpha = when {
            count == 0 -> 0.1f // Should be handled by if
            count <= 1 -> 0.3f
            count <= 3 -> 0.5f
            count <= 6 -> 0.7f
            else -> 1.0f
        }
        primaryColor.copy(alpha = alpha)
    }
}
