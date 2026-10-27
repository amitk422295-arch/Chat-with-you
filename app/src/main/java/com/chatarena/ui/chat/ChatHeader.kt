package com.chatarena.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatarena.ui.auth.*

@Composable
fun ChatHeader(
    name: String,
    avatarUrl: String? = null,
    isGroupChat: Boolean = false,
    membersCount: Int = 0,
    isAdmin: Boolean = false,
    isTyping: Boolean = false,
    typingText: String? = null, // Group typing ke liye (e.g. "Rahul is typing...")
    onBackClick: () -> Unit = {},
    onHeaderClick: () -> Unit = {},
    onVoiceCallClick: () -> Unit = {},
    onVideoCallClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColor)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Side (Back Button + Avatar + Title)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            Surface(
                shape = CircleShape,
                color = BgColor,
                shadowElevation = 4.dp,
                modifier = Modifier.size(38.dp).clickable { onBackClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Clickable Area for Avatar & Name
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { if (isGroupChat) onHeaderClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Surface(
                    shape = CircleShape,
                    color = BgColor,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isGroupChat) {
                            Icon(Icons.Default.Groups, contentDescription = "Group", tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                        } else {
                            Text(name.take(1).uppercase(), color = PrimaryBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Name & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Subtitle Logic
                    val subtitle = when {
                        typingText != null -> typingText
                        isTyping -> "typing..."
                        isGroupChat -> "$membersCount members${if (isAdmin) " • Admin" else ""}"
                        else -> "ChatArena Secure"
                    }
                    
                    val subtitleColor = if (typingText != null || isTyping) PrimaryBlue else SubTextColor
                    
                    Text(
                        text = subtitle,
                        color = subtitleColor,
                        fontSize = 11.sp,
                        fontWeight = if (typingText != null || isTyping) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Right Side (Call Buttons or Chevron)
        if (!isGroupChat) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderIconButton(
                    icon = Icons.Default.Call,
                    contentDescription = "Voice Call",
                    onClick = onVoiceCallClick
                )
                HeaderIconButton(
                    icon = Icons.Default.Videocam,
                    contentDescription = "Video Call",
                    onClick = onVideoCallClick
                )
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Group Info",
                tint = SubTextColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = BgColor,
        shadowElevation = 4.dp,
        modifier = Modifier.size(38.dp).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
