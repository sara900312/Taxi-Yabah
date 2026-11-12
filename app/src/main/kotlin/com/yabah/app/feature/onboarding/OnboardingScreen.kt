package com.yabah.app.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yabah.app.R
import com.yabah.app.ui.components.YabahPrimaryButton
import com.yabah.app.ui.theme.Spacing
import com.yabah.app.ui.theme.YabahColors
import kotlinx.coroutines.launch

private data class Page(val title: Int, val body: Int)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = listOf(Page(R.string.onb1_title, R.string.onb1_body), Page(R.string.onb2_title, R.string.onb2_body), Page(R.string.onb3_title, R.string.onb3_body))
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(YabahColors.Navy).safeDrawingPadding().padding(Spacing.xl).testTag("onboarding"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onFinished) { Text(stringResource(R.string.onb_skip), color = YabahColors.Ivory) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(painterResource(R.drawable.ic_yabah_mark), null, Modifier.size(140.dp))
                Spacer(Modifier.height(Spacing.xl))
                Text(stringResource(pages[i].title), style = MaterialTheme.typography.headlineMedium, color = YabahColors.Ivory, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(pages[i].body), style = MaterialTheme.typography.bodyLarge, color = YabahColors.Ivory.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = Spacing.lg)) {
            repeat(pages.size) { i ->
                Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                    .background(if (i == pager.currentPage) YabahColors.Gold else YabahColors.Ivory.copy(alpha = 0.3f)))
            }
        }
        val last = pager.currentPage == pages.lastIndex
        YabahPrimaryButton(
            stringResource(if (last) R.string.onb_start else R.string.onb_next),
            onClick = { if (last) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.testTag("onboarding_next"),
        )
        Spacer(Modifier.width(1.dp))
    }
}
