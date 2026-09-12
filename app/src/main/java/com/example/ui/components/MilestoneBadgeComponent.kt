package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import com.example.util.rememberHapticHelper

/**
 * Represents a milestone achievement badge.
 */
data class MilestoneBadge(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val current: Int,
    val target: Int,
    val unit: String,
    val icon: ImageVector,
    val accentColor: Color,
    val isUnlocked: Boolean,
    val unlockedDate: String? = null
) {
    val progressFraction: Float
        get() = if (target > 0) (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Section on Dashboard displaying milestone rewards and badge icons.
 */
@Composable
fun MilestoneRewardsSection(
    badges: List<MilestoneBadge>,
    onBadgeClick: (MilestoneBadge) -> Unit,
    modifier: Modifier = Modifier
) {
    val unlockedCount = badges.count { it.isUnlocked }
    val totalCount = badges.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Milestones & Badges",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                }

                // Badge count pill
                Surface(
                    color = BluePrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "$unlockedCount of $totalCount Unlocked",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = BluePrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Badges Horizontal Carousel / Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(badges, key = { it.id }) { badge ->
                    BadgeItemCard(
                        badge = badge,
                        onClick = { onBadgeClick(badge) }
                    )
                }
            }
        }
    }
}

/**
 * Individual badge preview card.
 */
@Composable
fun BadgeItemCard(
    badge: MilestoneBadge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticHelper = rememberHapticHelper()

    Box(
        modifier = modifier
            .width(115.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (badge.isUnlocked) badge.accentColor.copy(alpha = 0.06f) else BackgroundLight)
            .border(
                BorderStroke(
                    1.dp,
                    if (badge.isUnlocked) badge.accentColor.copy(alpha = 0.4f) else BorderLight
                ),
                RoundedCornerShape(14.dp)
            )
            .clickable {
                hapticHelper.onToggleOrAdjust()
                onClick()
            }
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag("badge_${badge.id}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular Emblem Icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (badge.isUnlocked) badge.accentColor else BorderLight
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badge.icon,
                    contentDescription = badge.title,
                    tint = if (badge.isUnlocked) Color.White else TextMuted,
                    modifier = Modifier.size(26.dp)
                )

                // Small status pin (Check or Lock)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(if (badge.isUnlocked) SuccessGreen else TextMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (badge.isUnlocked) Icons.Default.Check else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badge Title
            Text(
                text = badge.title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (badge.isUnlocked) TextPrimary else TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Progress text or Unlocked state
            if (badge.isUnlocked) {
                Text(
                    text = "Unlocked",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = badge.accentColor
                )
            } else {
                Text(
                    text = "${badge.current}/${badge.target} ${badge.unit}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = TextMuted
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Detailed Dialog displaying milestone badge info, celebration, and requirements.
 */
@Composable
fun MilestoneDetailDialog(
    badge: MilestoneBadge?,
    onDismiss: () -> Unit
) {
    if (badge == null) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("milestone_detail_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Badge Emblem
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            if (badge.isUnlocked) badge.accentColor else BorderLight
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badge.icon,
                        contentDescription = badge.title,
                        tint = if (badge.isUnlocked) Color.White else TextMuted,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title and category
                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = (if (badge.isUnlocked) badge.accentColor else TextMuted).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (badge.isUnlocked) "Milestone Achieved" else "In Progress",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (badge.isUnlocked) badge.accentColor else TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Progress Bar & Ratio
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${badge.current} / ${badge.target} ${badge.unit}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { badge.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (badge.isUnlocked) badge.accentColor else BluePrimary,
                        trackColor = BorderLight
                    )
                }

                if (badge.isUnlocked && badge.unlockedDate != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Achieved on ${badge.unlockedDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("close_badge_dialog_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Result screen milestone reward celebration banner.
 */
@Composable
fun MilestoneRewardBanner(
    badge: MilestoneBadge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("milestone_reward_banner"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = badge.accentColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, badge.accentColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(badge.accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badge.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Milestone Unlocked!",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = badge.accentColor
                )
                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = badge.accentColor
            )
        }
    }
}
