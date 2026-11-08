package com.moody.player.ui.vault

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moody.player.data.vault.UnlockResult
import com.moody.player.data.vault.VaultInfo
import com.moody.player.data.vault.VaultLock
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.ChoiceChip
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.design.illustrations.EmptyVault
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps the vault out of screenshots, screen recordings and the recents thumbnail while it is shown. */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

val RECOVERY_QUESTIONS = listOf(
    "Name of your first pet",
    "Street you grew up on",
    "Your favourite film as a kid",
    "City where your parents met",
)

@Composable
private fun GateFrame(onBack: () -> Unit, content: @Composable () -> Unit) {
    SecureWindow()
    Box(Modifier.fillMaxSize().background(MoodyTheme.colors.bg).statusBarsPadding().navigationBarsPadding()) {
        IconAction(MoodyIcons.ArrowBack, "Back", onBack, Modifier.padding(4.dp), tint = MoodyTheme.colors.text)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    val c = MoodyTheme.colors
    Icon(MoodyIcons.Vault, null, Modifier.size(28.dp), tint = c.accent)
    Gap(16)
    Text(title, style = MoodyTheme.type.display, color = c.text, textAlign = TextAlign.Center)
    Gap(8)
    Text(body, style = MoodyTheme.type.body, color = c.text2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
}

/** First visit: choose a PIN, confirm it, pick a recovery question, read the uninstall warning. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultSetupScreen(lock: VaultLock, onBack: () -> Unit) {
    val c = MoodyTheme.colors
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var mismatch by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf(RECOVERY_QUESTIONS.first()) }
    var custom by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    GateFrame(onBack = { if (step > 0) step-- else onBack() }) {
        when (step) {
            0 -> {
                Heading("Set a vault PIN", "4 to 6 digits. You'll enter it each time you open the vault")
                Gap(32)
                PinDots(pin.length, maxOf(4, minOf(6, pin.length + if (pin.length < 6) 1 else 0)), 0, false)
                Gap(32)
                PinPad({ d -> if (pin.length < 6) pin += d }, { pin = pin.dropLast(1) })
                Gap(24)
                PrimaryButton("Next", { step = 1 }, enabled = pin.length >= 4)
            }
            1 -> {
                Heading("Enter it again", if (mismatch) "Those didn't match. Try once more" else "Just to be sure")
                Gap(32)
                PinDots(confirm.length, pin.length, shake, mismatch)
                Gap(32)
                PinPad({ d ->
                    if (confirm.length < pin.length) confirm += d
                    if (confirm.length == pin.length) {
                        if (confirm == pin) { mismatch = false; step = 2 } else { mismatch = true; shake++; confirm = "" }
                    }
                }, { confirm = confirm.dropLast(1) })
            }
            2 -> {
                Heading("A question for a forgotten PIN", "If you forget your PIN, the right answer lets you set a new one")
                Gap(24)
                Column(Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RECOVERY_QUESTIONS.forEach { q -> ChoiceChip(q, !custom && question == q, { custom = false; question = q }) }
                        ChoiceChip("Write my own", custom, { custom = true; question = "" })
                    }
                    if (custom) { Gap(16); Field("Your question", question) { question = it } }
                    Gap(16)
                    Field("Answer", answer) { answer = it }
                    Gap(8)
                    Text("Capital letters and extra spaces don't matter", style = MoodyTheme.type.caption, color = c.text3)
                    Gap(24)
                    PrimaryButton("Next", { step = 3 }, enabled = question.isNotBlank() && answer.trim().length >= 2)
                }
            }
            else -> {
                EmptyVault()
                Gap(16)
                Text("One thing to know", style = MoodyTheme.type.display, color = c.text, textAlign = TextAlign.Center)
                Gap(8)
                Text(
                    "Hidden videos move into Moody's own storage. If you uninstall Moody, they are deleted with it. Unhide anything you want to keep first.",
                    style = MoodyTheme.type.body, color = c.text2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 340.dp),
                )
                Gap(32)
                PrimaryButton(if (saving) "Setting up" else "Open the vault", {
                    saving = true
                    scope.launch { lock.setUp(pin, question, answer) }
                }, enabled = !saving)
            }
        }
    }
}

@Composable
fun VaultUnlockScreen(lock: VaultLock, info: VaultInfo, onBack: () -> Unit) {
    val c = MoodyTheme.colors
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var checking by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { val ms = lock.lockedForMs(); if (ms > 0) lockedUntil = System.currentTimeMillis() + ms }
    LaunchedEffect(lockedUntil) { while (System.currentTimeMillis() < lockedUntil) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    val waiting = lockedUntil > now

    if (recovering) { RecoveryScreen(lock, info, onBack = { recovering = false }); return }

    GateFrame(onBack) {
        Heading("Vault", when {
            waiting -> "Too many tries. Wait ${((lockedUntil - now) / 1000) + 1} s"
            message != null -> message!!
            else -> "Enter your PIN"
        })
        Gap(32)
        PinDots(pin.length, info.pinLength, shake, message != null)
        Gap(32)
        PinPad(
            onDigit = { d ->
                if (checking || waiting) return@PinPad
                pin += d
                if (pin.length == info.pinLength) {
                    checking = true
                    scope.launch {
                        when (val r = lock.unlock(pin)) {
                            UnlockResult.Ok -> Unit
                            is UnlockResult.Wrong -> { message = if (r.attemptsLeft <= 2) "Wrong PIN. ${r.attemptsLeft} tries before a short wait" else "Wrong PIN"; shake++ }
                            is UnlockResult.LockedFor -> { lockedUntil = System.currentTimeMillis() + r.ms; message = null; shake++ }
                        }
                        pin = ""
                        checking = false
                    }
                }
            },
            onBackspace = { pin = pin.dropLast(1) },
            enabled = !waiting && !checking,
            leftLabel = "Forgot",
            onLeft = { recovering = true },
        )
    }
}

@Composable
private fun RecoveryScreen(lock: VaultLock, info: VaultInfo, onBack: () -> Unit) {
    val c = MoodyTheme.colors
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    GateFrame(onBack) {
        Heading("Forgot your PIN", info.question.ifBlank { "Answer your recovery question" })
        Gap(24)
        Column(Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
            Field("Answer", answer) { answer = it; error = null }
            Gap(16)
            Field("New PIN, 4 to 6 digits", newPin, digits = true) { v -> newPin = v.filter { it.isDigit() }.take(6); error = null }
            error?.let { Gap(8); Text(it, style = MoodyTheme.type.caption, color = c.danger) }
            Gap(24)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (busy) "Checking" else "Set new PIN", {
                    busy = true
                    scope.launch {
                        if (!lock.recover(answer, newPin)) error = "That answer doesn't match"
                        busy = false
                    }
                }, enabled = !busy && answer.isNotBlank() && newPin.length >= 4)
                QuietButton("Cancel", onBack)
            }
        }
    }
}

@Composable
fun Field(label: String, value: String, digits: Boolean = false, onChange: (String) -> Unit) {
    val c = MoodyTheme.colors
    Column {
        Text(label, style = MoodyTheme.type.section, color = c.text3)
        Gap(8)
        Box(Modifier.fillMaxWidth().height(48.dp).background(c.surface, Radii.md).border(1.dp, c.line2, Radii.md).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value, onChange, singleLine = true, textStyle = MoodyTheme.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accent),
                keyboardOptions = if (digits) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword) else androidx.compose.foundation.text.KeyboardOptions.Default,
                visualTransformation = if (digits) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
