package com.example.agent.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.ui.theme.LocalMotionEnabled
import com.example.agent.ui.theme.SpideyBlack
import com.example.agent.ui.theme.SpideyRed
import kotlinx.coroutines.delay

/**
 * The launch screen: pure black, the wordmark in red cursive handwriting.
 *
 * The handwriting uses the platform's built-in cursive family, so there is no font
 * to bundle or license. To use a specific script face, drop a .ttf into
 * res/font/ and swap [FontFamily.Cursive] for that FontFamily here.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    val motion = LocalMotionEnabled.current

    val alpha by animateFloatAsState(
        targetValue = if (started || !motion) 1f else 0f,
        animationSpec = tween(durationMillis = if (motion) 700 else 0, easing = FastOutSlowInEasing),
        label = "wordmarkAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (started || !motion) 1f else 0.9f,
        animationSpec = tween(durationMillis = if (motion) 800 else 0, easing = FastOutSlowInEasing),
        label = "wordmarkScale",
    )
    val ruleWidth by animateFloatAsState(
        targetValue = if (started || !motion) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (motion) 700 else 0,
            delayMillis = if (motion) 350 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "ruleWidth",
    )

    LaunchedEffect(Unit) {
        started = true
        delay(if (motion) 2600 else 1200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpideyBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .alpha(alpha)
                .scale(scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Spidey",
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Normal,
                fontSize = 76.sp,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(14.dp))

            // A thin red rule that draws itself in beneath the wordmark.
            Box(
                Modifier
                    .width((120 * ruleWidth).dp)
                    .height(2.dp)
                    .background(SpideyRed.copy(alpha = 0.65f)),
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "your on-device agent",
                fontSize = 13.sp,
                letterSpacing = 3.sp,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
