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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import android.content.Context
import com.example.remoteshutdown.SSHManager

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
    private val sshManager = SSHManager()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PCShutdownTheme {
                // Передаем контекст в MainScreen для Toast
                MainScreen(this, sshManager)
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
fun MainScreen(context: Context, sshManager: SSHManager) { // Добавляем context как параметр
    // Состояния для анимаций и логики
    var isShuttingDown by remember { mutableStateOf(false) }
    var buttonPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // val sshManager = remember { SSHManager() } // Создаем экземпляр SSHManager
    
    // Функция для выполнения shutdown
    fun performShutdown() {
        scope.launch {
            val success = sshManager.executeShutdown()
            if (success) {
                Toast.makeText(context, "Компьютер успешно выключен!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Ошибка при выключении компьютера. Проверьте настройки SSH.", Toast.LENGTH_LONG).show()
            }
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
                            // Логика нажатия кнопки
                            if (!isShuttingDown) {
                                buttonPressed = true
                                scope.launch {
                                    delay(150)  // Короткая задержка для анимации
                                    buttonPressed = false
                                    isShuttingDown = true
                                    
                                    // Выполняем shutdown через SSH
                                    val success = sshManager.executeShutdown()
                                    
                                    // Логируем результат для отладки
                                    if (success) {
                                        Log.d("MainActivity", "Shutdown команда отправлена успешно")
                                    } else {
                                        Log.e("MainActivity", "Ошибка при отправке shutdown команды")
                                    }
                                    
                                    delay(3000)  // Время для выполнения команды
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
                            Text(
                                text = "⏻",  // Символ power
                                fontSize = 48.sp,
                                color = Color.White
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
            
            // Статус соединения - показываем IP адрес
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
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🌐 Статус подключения",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleColors.textPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "IP: 192.168.8.101",  // Твой IP
                        fontSize = 14.sp,
                        color = AppleColors.textSecondary
                    )
                    Text(
                        text = "Пользователь: zverev",  // Твой username
                        fontSize = 14.sp,
                        color = AppleColors.textSecondary
                    )
                }
            }

@Composable
fun ActionButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        interactionSource = interactionSource
    ) {
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}
