package com.chatarena.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// 🚀 NEUMORPHIC CARD (Pop-out 3D effect)
// ==========================================
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(BgColor)
            .drawBehind {
                // Dark Shadow (Bottom Right)
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = Color.Transparent.toArgb()
                    frameworkPaint.setShadowLayer(
                        12.dp.toPx(),
                        6.dp.toPx(),
                        6.dp.toPx(),
                        Color(0xFFA3B1C6).copy(alpha = 0.6f).toArgb()
                    )
                    canvas.nativeCanvas.drawRoundRect(
                        0f, 0f, size.width, size.height,
                        cornerRadius.toPx(), cornerRadius.toPx(), frameworkPaint
                    )
                }

                // Light Shadow (Top Left)
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = Color.Transparent.toArgb()
                    frameworkPaint.setShadowLayer(
                        12.dp.toPx(),
                        -6.dp.toPx(),
                        -6.dp.toPx(),
                        Color.White.copy(alpha = 0.8f).toArgb()
                    )
                    canvas.nativeCanvas.drawRoundRect(
                        0f, 0f, size.width, size.height,
                        cornerRadius.toPx(), cornerRadius.toPx(), frameworkPaint
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ==========================================
// 🚀 NEUMORPHIC INPUT (Inset / Pressed look)
// ==========================================
@Composable
fun NeumorphicInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(BgColor)
            .drawBehind {
                // Inset Dark Shadow
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = BgColor.toArgb()
                    frameworkPaint.setShadowLayer(
                        6.dp.toPx(),
                        3.dp.toPx(),
                        3.dp.toPx(),
                        Color(0xFFA3B1C6).copy(alpha = 0.6f).toArgb()
                    )
                    canvas.nativeCanvas.drawRoundRect(
                        0f, 0f, size.width, size.height,
                        cornerRadius.toPx(), cornerRadius.toPx(), frameworkPaint
                    )
                }

                // Inset Light Shadow
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = BgColor.toArgb()
                    frameworkPaint.setShadowLayer(
                        6.dp.toPx(),
                        -3.dp.toPx(),
                        -3.dp.toPx(),
                        Color.White.copy(alpha = 0.8f).toArgb()
                    )
                    canvas.nativeCanvas.drawRoundRect(
                        0f, 0f, size.width, size.height,
                        cornerRadius.toPx(), cornerRadius.toPx(), frameworkPaint
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = SubTextColor, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = TextColor, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==========================================
// 🚀 NEUMORPHIC BUTTON (Clickable 3D effect)
// ==========================================
@Composable
fun NeumorphicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = PrimaryBlue,
    cornerRadius: Dp = 14.dp
) {
    NeumorphicCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable { onClick() },
        cornerRadius = cornerRadius
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp)
        )
    }
}
