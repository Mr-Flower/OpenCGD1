package it.cgd1.sveglia.ui.welcome

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ui.theme.OpenCgd1Theme
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4
private const val PERMISSION_PAGE = 2

/** Guida del primo avvio: cos'è l'app, come preparare la sveglia, permesso Bluetooth, via. */
@Composable
fun WelcomeScreen(
    permissionsGranted: Boolean,
    permissionPermanentlyDenied: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == PAGE_COUNT - 1
    // Senza permesso l'app non serve a niente: dalla pagina del permesso non si va avanti finché non c'è.
    val blockedOnPermission = pager.currentPage == PERMISSION_PAGE && !permissionsGranted
    fun goTo(page: Int) = scope.launch { pager.animateScrollToPage(page) }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted && pager.currentPage == PERMISSION_PAGE) pager.animateScrollToPage(PERMISSION_PAGE + 1)
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    // "Salta" salta la presentazione, non il permesso.
                    if (pager.currentPage < PERMISSION_PAGE && !permissionsGranted) {
                        TextButton(onClick = { goTo(PERMISSION_PAGE) }) { Text(stringResource(R.string.welcome_skip)) }
                    } else if (!isLast && permissionsGranted) {
                        TextButton(onClick = onFinish) { Text(stringResource(R.string.welcome_skip)) }
                    }
                }
                PageIndicator(pager.currentPage)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    when {
                        blockedOnPermission && permissionPermanentlyDenied -> Button(onClick = onOpenAppSettings) {
                            Text(stringResource(R.string.welcome_open_settings), maxLines = 1)
                        }
                        blockedOnPermission -> Button(onClick = onRequestPermissions) {
                            Text(stringResource(R.string.welcome_allow), maxLines = 1)
                        }
                        isLast -> Button(onClick = onFinish) { Text(stringResource(R.string.welcome_start)) }
                        else -> Button(onClick = { goTo(pager.currentPage + 1) }) { Text(stringResource(R.string.welcome_next)) }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            userScrollEnabled = !blockedOnPermission,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            when (page) {
                0 -> WelcomePage(
                    icon = { Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(56.dp)) },
                    title = R.string.welcome_intro_title,
                    body = R.string.welcome_intro_body,
                )
                1 -> WelcomePage(
                    icon = { Icon(Icons.Filled.Info, null, Modifier.size(56.dp)) },
                    title = R.string.welcome_prepare_title,
                    body = R.string.welcome_prepare_body,
                    steps = listOf(R.string.welcome_prepare_step_1, R.string.welcome_prepare_step_2, R.string.welcome_prepare_step_3),
                )
                PERMISSION_PAGE -> WelcomePage(
                    icon = {
                        Icon(
                            if (permissionsGranted) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                            null,
                            Modifier.size(56.dp),
                        )
                    },
                    title = R.string.welcome_permission_title,
                    body = R.string.welcome_permission_body,
                ) {
                    when {
                        permissionsGranted -> Text(
                            stringResource(R.string.welcome_permission_granted),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        permissionPermanentlyDenied -> Text(
                            stringResource(R.string.welcome_permission_denied),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        else -> Text(
                            stringResource(R.string.welcome_permission_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> WelcomePage(
                    icon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(56.dp)) },
                    title = R.string.welcome_ready_title,
                    body = R.string.welcome_ready_body,
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(
    icon: @Composable () -> Unit,
    @StringRes title: Int,
    @StringRes body: Int,
    steps: List<Int> = emptyList(),
    extra: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) { Box(Modifier.padding(24.dp)) { icon() } }
            Spacer(Modifier.height(32.dp))
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    steps.forEachIndexed { index, step -> Step(index + 1, step) }
                }
            }
            Spacer(Modifier.height(24.dp))
            extra()
        }
    }
}

@Composable
private fun Step(number: Int, @StringRes text: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text(number.toString(), style = MaterialTheme.typography.titleSmall) }
        }
        Spacer(Modifier.width(16.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PageIndicator(current: Int) {
    val description = stringResource(R.string.welcome_page, current + 1, PAGE_COUNT)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(PAGE_COUNT) { index ->
            Box(
                Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    OpenCgd1Theme(dynamicColor = false) {
        WelcomeScreen(
            permissionsGranted = false,
            permissionPermanentlyDenied = false,
            onRequestPermissions = {},
            onOpenAppSettings = {},
            onFinish = {},
        )
    }
}
