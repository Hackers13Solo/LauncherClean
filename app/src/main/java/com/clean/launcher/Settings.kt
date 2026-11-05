package com.clean.launcher

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private enum class SPage(val title: String, val sub: String, val icon: ImageVector) {
    Look("Aparência", "Cor de destaque e formato dos ícones", Icons.Rounded.Palette),
    Wall("Papel de parede", "Trocar a imagem, escurecer e desfocar", Icons.Rounded.Wallpaper),
    Fx("Efeitos visuais", "Aurora, partículas, neve e chuva", Icons.Rounded.AutoAwesome),
    Start("Tela inicial", "Relógio, seu nome, atalhos e mais usados", Icons.Rounded.Home),
    Apps("Lista de apps", "Grade, tamanhos, espaçamento e barra A–Z", Icons.Rounded.ViewList),
    Bars("Barras do sistema", "Barra de status e de navegação", Icons.Rounded.Fullscreen),
    Hidden("Apps ocultos", "Mostrar apps que você escondeu", Icons.Rounded.VisibilityOff),
    Sys("Sistema e sobre", "Launcher padrão, notificações e novidades", Icons.Rounded.Tune)
}

@Composable
private fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(0.07f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (title != null) Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent, letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun Toggle(text: String, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 16.sp, color = Color.White)
            if (hint != null) Text(hint, fontSize = 12.sp, color = Color.White.copy(0.55f))
        }
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White))
    }
}

@Composable
private fun Slide(text: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 14.sp, color = Color.White.copy(0.85f))
            Text(label, fontSize = 13.sp, color = Accent)
        }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, hint: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 16.sp, color = Color.White)
            if (hint != null) Text(hint, fontSize = 12.sp, color = Color.White.copy(0.55f))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(0.45f))
    }
}

@Composable
private fun ChipsRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Ajustes em tela cheia, separados por categoria. */
@Composable
fun SettingsScreen(vm: LauncherVM, onClose: () -> Unit, onChangeWallpaper: () -> Unit, onGallery: () -> Unit) {
    val ctx = LocalContext.current
    var page by remember { mutableStateOf<SPage?>(null) }

    BackHandler { if (page != null) page = null else onClose() }

    Box(
        Modifier.fillMaxSize().background(Color(0xFA0E0E12)).pointerInput(Unit) { detectTapGestures { } }.systemBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ if (page != null) page = null else onClose() }) {
                    Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Color.White)
                }
                Text(page?.title ?: "Ajustes", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            key(page) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val pg = page
                    if (pg == null) {
                        SPage.values().forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(0.07f))
                                    .clickable { page = p }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(Accent.copy(0.22f)),
                                    contentAlignment = Alignment.Center
                                ) { Icon(p.icon, null, tint = Accent, modifier = Modifier.size(24.dp)) }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                    Text(p.sub, fontSize = 13.sp, color = Color.White.copy(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(0.45f))
                            }
                        }
                    } else when (pg) {
                        SPage.Look -> {
                            Group("COR DE DESTAQUE") {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AccentColors.forEachIndexed { i, c ->
                                        Box(
                                            Modifier.size(38.dp).clip(CircleShape).background(c)
                                                .then(if (i == vm.accentIdx && !vm.autoAccent) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                                .clickable { vm.setAccent(i) }
                                        )
                                    }
                                }
                                Toggle(
                                    "Cor automática", vm.autoAccent,
                                    "A cor de destaque acompanha as cores do papel de parede"
                                ) { vm.autoAccent = it; vm.saveSettings(); if (it) vm.refreshAutoAccent() }
                            }
                            Group("FORMATO DOS ÍCONES") {
                                ChipsRow {
                                    Chip("Original", vm.iconShape == 0) { vm.iconShape = 0; vm.saveSettings() }
                                    Chip("Redondo", vm.iconShape == 1) { vm.iconShape = 1; vm.saveSettings() }
                                    Chip("Arredondado", vm.iconShape == 2) { vm.iconShape = 2; vm.saveSettings() }
                                }
                            }
                        }

                        SPage.Wall -> {
                            Group("IMAGEM") {
                                ActionRow(Icons.Rounded.Wallpaper, "Trocar papel de parede", "Buscar na web ou ver as 10 categorias do sistema") { onChangeWallpaper() }
                                ActionRow(Icons.Rounded.PhotoLibrary, "Escolher da galeria", "Use uma foto do celular") { onGallery() }
                                if (vm.useCustomWallpaper)
                                    ActionRow(Icons.Rounded.Refresh, "Voltar ao papel de parede do sistema", "Remove a imagem escolhida") {
                                        vm.useCustomWallpaper = false; vm.saveSettings()
                                    }
                            }
                            Group("AJUSTE FINO") {
                                Slide("Escurecer fundo", vm.dim, 0f..0.85f, "${(vm.dim * 100).roundToInt()}%") { vm.dim = it; vm.saveSettings() }
                                Slide("Desfoque (Android 12+)", vm.blur, 0f..25f, "${vm.blur.roundToInt()}") { vm.blur = it; vm.saveSettings() }
                            }
                        }

                        SPage.Fx -> {
                            Group("ANIMAÇÕES DE FUNDO") {
                                Toggle("Partículas brilhantes", (vm.fxMask and 1) != 0) { vm.setFx(1, it) }
                                Toggle("Aurora", (vm.fxMask and 2) != 0, "Faixas de luz em movimento") { vm.setFx(2, it) }
                                Toggle("Neve", (vm.fxMask and 4) != 0) { vm.setFx(4, it) }
                                Toggle("Chuva", (vm.fxMask and 8) != 0) { vm.setFx(8, it) }
                            }
                            Group("LISTA") {
                                Toggle("Profundidade ao rolar", vm.edgeFx, "Linhas nas bordas desvanecem e encolhem") { vm.edgeFx = it; vm.saveSettings() }
                            }
                        }

                        SPage.Start -> {
                            Group("RELÓGIO E SAUDAÇÃO") {
                                Toggle("Relógio grande com saudação", vm.bigClock) { vm.bigClock = it; vm.saveSettings() }
                                Toggle("Relógio em 24 horas", vm.use24h) { vm.use24h = it; vm.saveSettings() }
                                OutlinedTextField(
                                    vm.userName, { vm.userName = it.take(20); vm.saveSettings() },
                                    Modifier.fillMaxWidth(), singleLine = true,
                                    label = { Text("Seu nome (aparece na saudação)") }
                                )
                            }
                            Group("ATALHOS") {
                                Toggle("Atalhos rápidos", vm.quickActions, "Lanterna, Wi-Fi, Bluetooth, câmera e calculadora") { vm.quickActions = it; vm.saveSettings() }
                                Toggle("Mostrar “Mais usados”", vm.showMostUsed) { vm.showMostUsed = it; vm.saveSettings() }
                                Chip("Zerar “Mais usados”", false) { vm.resetUsage() }
                            }
                        }

                        SPage.Apps -> {
                            Group("EXIBIÇÃO") {
                                Toggle("Exibir em grade (4 colunas)", vm.gridMode) { vm.gridMode = it; vm.saveSettings() }
                                Toggle("Selo NOVO em apps recém-instalados", vm.newBadge) { vm.newBadge = it; vm.saveSettings() }
                                Toggle("Vibração ao usar a barra A–Z", vm.haptics) { vm.haptics = it; vm.saveSettings() }
                            }
                            Group("TAMANHOS") {
                                Slide("Tamanho dos ícones", vm.iconSize, 40f..72f, "${vm.iconSize.roundToInt()}") { vm.iconSize = it; vm.saveSettings() }
                                Slide("Tamanho do texto", vm.textSize, 18f..32f, "${vm.textSize.roundToInt()}") { vm.textSize = it; vm.saveSettings() }
                                Slide("Espaçamento entre apps", vm.rowPad, 2f..18f, "${vm.rowPad.roundToInt()}") { vm.rowPad = it; vm.saveSettings() }
                            }
                        }

                        SPage.Bars -> Group("TELA CHEIA") {
                            Toggle("Ocultar barra de status", vm.hideStatus) { vm.hideStatus = it; vm.saveSettings() }
                            Toggle("Ocultar barra de navegação", vm.hideNav) { vm.hideNav = it; vm.saveSettings() }
                        }

                        SPage.Hidden -> Group {
                            val hiddenApps = vm.apps.filter { it.pkg in vm.hidden }
                            if (hiddenApps.isEmpty()) {
                                Text(
                                    "Nenhum app oculto. Segure um app na lista e escolha “Ocultar app”.",
                                    fontSize = 14.sp, color = Color.White.copy(0.65f)
                                )
                            } else hiddenApps.forEach { a ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(a.pkg, 36.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(a.label, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    TextButton({ vm.toggleHidden(a.pkg) }) { Text("Mostrar", color = Accent) }
                                }
                            }
                        }

                        SPage.Sys -> {
                            Group("SISTEMA") {
                                ActionRow(Icons.Rounded.Home, "Launcher padrão", "Definir o Clean Launcher como início") {
                                    runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                                ActionRow(Icons.Rounded.Notifications, "Acesso às notificações", "Necessário para o player de música e os selos") {
                                    openListenerSettings(ctx)
                                }
                            }
                            Group("SOBRE") {
                                Text("Clean Launcher 1.2", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Novidades: tela de papel de parede com busca na web e 10 categorias, ajustes por categoria, " +
                                        "aurora renovada, neve e chuva, cor automática, seu nome na saudação, calculadora na busca " +
                                        "e espaçamento ajustável.",
                                    fontSize = 13.sp, color = Color.White.copy(0.65f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
