package com.monekx.hyprlink

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DynamicScreen(
    config: UIConfig,
    moduleValues: Map<String, Float>,
    moduleTexts: Map<String, String>,
    onAction: (String, Double?) -> Unit
) {
    val bgColor = StyleParser.parseColor(config.css, "background", Color(0xFF1A1B26))
    val textColor = StyleParser.parseColor(config.css, "text-color", Color.White)
    val accentColor = StyleParser.parseColor(config.css, "accent", Color(0xFF7AA2F7))

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Безопасный сброс индекса, если количество вкладок изменилось или профили отсутствуют
    LaunchedEffect(config.profiles.size) {
        if (selectedTabIndex >= config.profiles.size) {
            selectedTabIndex = 0
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {
        Text(
            text = config.hostname,
            style = MaterialTheme.typography.headlineMedium,
            color = textColor,
            modifier = Modifier.padding(16.dp)
        )

        // Проверка: если профилей нет, не отрисовываем TabRow, чтобы избежать IndexOutOfBoundsException
        if (config.profiles.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = accentColor,
                edgePadding = 16.dp,
                divider = {}
            ) {
                config.profiles.forEachIndexed { index, profile ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = profile.name,
                                color = if (selectedTabIndex == index) accentColor else textColor.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }
        }

        // Безопасное получение модулей текущего профиля
        val currentModules = config.profiles.getOrNull(selectedTabIndex)?.modules ?: emptyList()

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            items(
                items = currentModules,
                key = { it.id },
                span = { module ->
                    val span = when (module.type) {
                        "slider", "row" -> 3
                        else -> 1
                    }
                    GridItemSpan(span)
                }
            ) { module ->
                RenderModule(module, config, moduleValues, moduleTexts, onAction)
            }
        }
    }
}

@Composable
fun Modifier.applyCss(css: String?, prefix: String = ""): Modifier {
    if (css == null) return this

    val padding = StyleParser.parseSize(css, "${prefix}padding", 0).dp
    val margin = StyleParser.parseSize(css, "${prefix}margin", 0).dp

    return this
        .padding(margin)
        .padding(padding)
}

@Composable
fun RenderModule(
    module: Module,
    config: UIConfig,
    values: Map<String, Float>,
    texts: Map<String, String>,
    onAction: (String, Double?) -> Unit
) {
    val css = config.css
    val cardColor = StyleParser.parseColor(css, "card-bg", Color(0xFF24283B))
    val borderColor = StyleParser.parseColor(css, "card-border-color", Color.Transparent)
    val borderWidth = StyleParser.parseSize(css, "card-border-width", 0).dp
    val corner = StyleParser.parseSize(css, "card-radius", 12).dp
    val elevation = StyleParser.parseSize(css, "card-shadow", 0).dp

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(corner),
        border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        modifier = (if (module.type == "slider" || module.type == "row")
            Modifier.fillMaxWidth().heightIn(min = 60.dp)
        else Modifier.fillMaxWidth().aspectRatio(1f))
            .applyCss(css, "module-")
    ) {
        // Контент модуля с применением текстовых стилей
        val labelColor = StyleParser.parseColor(css, "label-color", Color.Gray)
        val labelWeight = StyleParser.parseFontWeight(css, "label-weight")
        val labelSize = StyleParser.parseSize(css, "label-size", 12).sp

        Box(modifier = Modifier.padding(12.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            when (module.type) {
                "button" -> ButtonElement(module, config, onAction)
                "display" -> DisplayElement(module, config, values, texts)
                "slider" -> SliderElement(module, config, values, onAction)
                "row" -> RowElement(module, config, values, texts, onAction)
            }
        }
    }
}

@Composable
fun DisplayElement(module: Module, text: String, value: Float) {
    val displayVal = if (text.isNotEmpty()) text else value.toInt().toString()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = module.label ?: "", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        Text(text = displayVal, color = Color.Cyan, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SliderElement(module: Module, config: UIConfig, value: Float, onAction: (String, Double?) -> Unit) {
    val accent = StyleParser.parseColor(config.css, "accent", Color(0xFF7AA2F7))
    var pos by remember(value) { mutableStateOf(value / 100f) }
    Column {
        Text(text = module.label ?: "", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = pos,
            onValueChange = { pos = it },
            onValueChangeFinished = { onAction(module.id, (pos * 100).toDouble()) },
            colors = SliderDefaults.colors(activeTrackColor = accent, thumbColor = accent)
        )
    }
}

@Composable
fun RenderModuleWrapper(
    module: Module,
    config: UIConfig,
    moduleValues: Map<String, Float>,
    moduleTexts: Map<String, String>,
    onAction: (String, Double?) -> Unit
) {
    val cardColor = StyleParser.parseColor(config.css, "card-color", MaterialTheme.colorScheme.surfaceVariant)
    val borderColor = StyleParser.parseColor(config.css, "border-color", Color.Transparent)
    val borderWidth = StyleParser.parseSize(config.css, "border-width", 0)
    val cornerRadius = StyleParser.parseSize(config.css, "border-radius", 12)

    val finalModifier = when (module.type) {
        "slider", "row" -> Modifier.fillMaxWidth().height(IntrinsicSize.Min).defaultMinSize(minHeight = 80.dp)
        else -> Modifier.fillMaxWidth().aspectRatio(1f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(cornerRadius.dp),
        border = if (borderWidth > 0) BorderStroke(borderWidth.dp, borderColor) else null,
        modifier = finalModifier
    ) {
        if (module.type == "row") {
            ModuleContent(module, config, moduleValues, moduleTexts, onAction)
        } else {
            Box(modifier = Modifier.padding(12.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                ModuleContent(module, config, moduleValues, moduleTexts, onAction)
            }
        }
    }
}

@Composable
fun ModuleContent(
    module: Module,
    config: UIConfig,
    moduleValues: Map<String, Float>,
    moduleTexts: Map<String, String>,
    onAction: (String, Double?) -> Unit
) {
    when (module.type) {
        "row" -> RowElement(module, config, moduleValues, moduleTexts, onAction)
        "button" -> ButtonElement(module, config, onAction)
        "display" -> DisplayElement(module, config, moduleValues, moduleTexts)
        "slider" -> SliderElement(module, config, moduleValues, onAction)
    }
}

@Composable
fun RowElement(
    module: Module,
    config: UIConfig,
    moduleValues: Map<String, Float>,
    moduleTexts: Map<String, String>,
    onAction: (String, Double?) -> Unit
) {
    val textColor = StyleParser.parseColor(config.css, "text-color", MaterialTheme.colorScheme.onSurface)
    val accentColor = StyleParser.parseColor(config.css, "accent", MaterialTheme.colorScheme.primary)
    val displayText = moduleTexts[module.id] ?: module.label ?: ""

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        if (displayText.isNotEmpty()) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                color = if (moduleTexts.containsKey(module.id)) accentColor else textColor.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            module.children?.forEach { child ->
                Box(modifier = Modifier.weight(1f)) {
                    ModuleContent(child, config, moduleValues, moduleTexts, onAction)
                }
            }
        }
    }
}

@Composable
fun ButtonElement(module: Module, config: UIConfig, onAction: (String, Double?) -> Unit) {
    val accent = StyleParser.parseColor(config.css, "accent", MaterialTheme.colorScheme.primary)
    val btnContentColor = StyleParser.parseColor(config.css, "button-color", accent)
    val btnBgColor = StyleParser.parseColor(config.css, "button-bg", Color.Transparent)
    val textColor = StyleParser.parseColor(config.css, "text-color", MaterialTheme.colorScheme.onSurface)

    Button(
        onClick = { onAction(module.id, null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = btnBgColor, contentColor = btnContentColor),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!module.icon.isNullOrEmpty()) {
                Text(text = module.icon, color = btnContentColor, style = MaterialTheme.typography.headlineSmall)
            }
            if (!module.label.isNullOrEmpty()) {
                Text(text = module.label, color = textColor, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
fun DisplayElement(module: Module, config: UIConfig, moduleValues: Map<String, Float>, moduleTexts: Map<String, String>) {
    val labelColor = StyleParser.parseColor(config.css, "display-label-color", Color.Gray)
    val valueColor = StyleParser.parseColor(config.css, "display-value-color", StyleParser.parseColor(config.css, "accent", Color.Cyan))
    val textValue = moduleTexts[module.id]
    val floatValue = moduleValues[module.id] ?: 0f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = module.label ?: module.id, color = labelColor, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(text = textValue ?: floatValue.toInt().toString(), color = valueColor, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SliderElement(module: Module, config: UIConfig, moduleValues: Map<String, Float>, onAction: (String, Double?) -> Unit) {
    val accent = StyleParser.parseColor(config.css, "accent", MaterialTheme.colorScheme.primary)
    val activeColor = StyleParser.parseColor(config.css, "slider-active-color", accent)
    val trackColor = StyleParser.parseColor(config.css, "slider-track-color", activeColor.copy(alpha = 0.24f))
    val textColor = StyleParser.parseColor(config.css, "text-color", MaterialTheme.colorScheme.onSurface)
    val serverValue = moduleValues[module.id] ?: 0f
    var pos by remember(serverValue) { mutableStateOf(serverValue / 100f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = module.label ?: module.id, color = textColor, style = MaterialTheme.typography.labelSmall)
            Text(text = "${(pos * 100).toInt()}%", color = activeColor, style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = pos,
            onValueChange = { pos = it },
            onValueChangeFinished = { onAction(module.id, (pos * 100).toDouble()) },
            colors = SliderDefaults.colors(thumbColor = activeColor, activeTrackColor = activeColor, inactiveTrackColor = trackColor)
        )
    }
}