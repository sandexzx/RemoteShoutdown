package com.example.remoteshutdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import android.content.Context
import android.util.Log
import com.example.remoteshutdown.SSHManager
import com.example.remoteshutdown.UniversalShutdownManager
import com.example.remoteshutdown.ShutdownResult
import com.example.remoteshutdown.OSType
import com.example.remoteshutdown.SystemStatus

// Цветовая схема в стиле Apple
object AppleColors {
    val primaryBlue = Color(0xFF007AFF)  // Фирменный синий Apple
    val backgroundGray = Color(0xFFF2F2F7)  // Светло-серый фон iOS
    val cardWhite = Color(0xFFFFFFFF)
    val textPrimary = Color(0xFF000000)
    val textSecondary = Color(0xFF8E8E93)
    val destructiveRed = Color(0xFFFF3B30)  // Красный для опасных действий
}

class MainActivity : ComponentActivity() {
    private val universalManager = UniversalShutdownManager()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PCShutdownTheme {
                MainScreen(this, universalManager)
            }
        }
    }
}

@Composable
fun PCShutdownTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppleColors.primaryBlue,
            background = AppleColors.backgroundGray,
            surface = AppleColors.cardWhite
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context, universalManager: UniversalShutdownManager) {
    // Состояния для анимаций и логики
    var isShuttingDown by remember { mutableStateOf(false) }
    var buttonPressed by remember { mutableStateOf(false) }
    var selectedOS by remember { mutableStateOf(OSType.AUTO) }
    var systemStatus by remember { mutableStateOf<SystemStatus?>(null) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // Проверяем статус систем при запуске
    LaunchedEffect(Unit) {
        try {
            systemStatus = universalManager.getSystemStatus()
            Log.d("MainActivity", "System status: $systemStatus")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to get system status", e)
        }
    }
    
    // Анимация для пульсации кнопки (как в Apple Watch)
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Анимация нажатия кнопки
    val buttonScale by animateFloatAsState(
        targetValue = if (buttonPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 400f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Градиентный фон как в iOS - от светло-серого к белому
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppleColors.backgroundGray,
                        Color.White.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            // Заголовок в стиле Apple - крупный, жирный, минималистичный
            Text(
                text = "PC Remote",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = AppleColors.textPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Подзаголовок - объясняет функцию приложения
            Text(
                text = "Управляй компьютером удаленно",
                fontSize = 17.sp,
                color = AppleColors.textSecondary,
                modifier = Modifier.padding(bottom = 60.dp)
            )

            // Главная кнопка shutdown - центральный элемент экрана
            Card(
                modifier = Modifier
                    .size(200.dp)  // Крупная кнопка как в iOS Control Center
                    .scale(buttonScale * pulseScale)  // Комбинируем анимации
                    .padding(16.dp),
                shape = RoundedCornerShape(32.dp),  // Скругленные углы как в iOS
                colors = CardDefaults.cardColors(
                    containerColor = AppleColors.destructiveRed
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp,  // Тень для глубины
                    pressedElevation = 2.dp   // Меньше тени при нажатии
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            // Отключаем стандартный ripple эффект
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            // Логика нажатия кнопки с новым менеджером
                            if (!isShuttingDown) {
                                buttonPressed = true
                                scope.launch {
                                    delay(150)  // Короткая задержка для анимации
                                    buttonPressed = false
                                    isShuttingDown = true
                                    
                                    // Выполняем shutdown через универсальный менеджер
                                    val result = universalManager.executeShutdown(selectedOS)
                                    
                                    when (result) {
                                        is ShutdownResult.Success -> {
                                            Log.d("MainActivity", "Shutdown успешно: ${result.message}")
                                            lastResult = "${result.osType.emoji} ${result.message}"
                                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                        }
                                        is ShutdownResult.Error -> {
                                            Log.e("MainActivity", "Shutdown ошибка: ${result.message}")
                                            lastResult = "❌ ${result.message}"
                                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    
                                    delay(3000)  // Время для просмотра результата
                                    isShuttingDown = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isShuttingDown) {
                        // Анимированный индикатор загрузки в стиле iOS
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color.White,
                            strokeWidth = 4.dp
                        )
                    } else {
                        // Иконка и текст кнопки
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Эмодзи иконка - простой но эффективный способ
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Power button",
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Выключить ПК",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Селектор ОС - выбираем какую систему выключать
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AppleColors.cardWhite
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "🎯 Выбор операционной системы",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleColors.textPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Радиокнопки для выбора ОС
                    OSType.values().forEach { osType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOS = osType }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Простая радиокнопка
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        color = if (selectedOS == osType) AppleColors.primaryBlue else AppleColors.textSecondary.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(50)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedOS == osType) {
                                    Text(
                                        text = "✓",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Text(
                                text = "${osType.emoji} ${osType.displayName}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppleColors.textPrimary
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Индикатор доступности
                            systemStatus?.let { status ->
                                val isAvailable = when (osType) {
                                    OSType.UBUNTU -> status.ubuntuAvailable
                                    OSType.WINDOWS -> status.windowsAvailable
                                    OSType.AUTO -> status.hasAnySystem
                                }
                                
                                Text(
                                    text = if (isAvailable) "🟢" else "🔴",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Статус соединения - показываем детали подключения
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AppleColors.cardWhite
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🌐 Статус подключения",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleColors.textPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // IP адрес
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "IP адрес",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppleColors.textPrimary
                        )
                        Text(
                            text = "192.168.8.101",
                            fontSize = 15.sp,
                            color = AppleColors.textSecondary,
                        )
                    }
                    
                    // Статус систем
                    systemStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🐧 Ubuntu SSH",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppleColors.textPrimary
                            )
                            Text(
                                text = if (status.ubuntuAvailable) "Доступен" else "Недоступен",
                                fontSize = 15.sp,
                                color = if (status.ubuntuAvailable) Color(0xFF34C759) else AppleColors.destructiveRed,
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🪟 Windows API",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppleColors.textPrimary
                            )
                            Text(
                                text = if (status.windowsAvailable) "Доступен" else "Недоступен",
                                fontSize = 15.sp,
                                color = if (status.windowsAvailable) Color(0xFF34C759) else AppleColors.destructiveRed,
                            )
                        }
                    }
                    
                    // Последний результат операции
                    lastResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = AppleColors.backgroundGray
                            )
                        ) {
                            Text(
                                text = result,
                                fontSize = 13.sp,
                                color = AppleColors.textSecondary,
                                modifier = Modifier.padding(12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
