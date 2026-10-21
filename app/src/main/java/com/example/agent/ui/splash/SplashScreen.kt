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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.ui.theme.Inter
import com.example.agent.ui.theme.LocalMotionEnabled
import com.example.agent.ui.theme.PillShape
import com.example.agent.ui.theme.SpideyBlue
import com.example.agent.ui.theme.SpideyTheme
import kotlinx.coroutines.delay

/**
 * The launch screen: solid Instamart blue with a bold white wordmark.
 *
 * The Android 12 system splash shows the same blue (see `Theme.Spidey.Splash`), so the
 * hand-off to this screen is seamless.
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

    SplashContent(alpha = alpha, scale = scale, ruleWidth = ruleWidth)
}

@Composable
private fun SplashContent(alpha: Float, scale: Float, ruleWidth: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpideyBlue),
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
                text = "spidey",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 64.sp,
                letterSpacing = (-1.5).sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            // A white pill rule that draws itself in beneath the wordmark.
            Box(
                Modifier
                    .width((96 * ruleWidth).dp)
                    .height(4.dp)
                    .background(Color.White, PillShape),
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "your on-device agent",
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Splash", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SplashPreview() {
    SpideyTheme {
        SplashContent(alpha = 1f, scale = 1f, ruleWidth = 1f)
    }
}
