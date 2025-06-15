package com.example.remoteshutdown

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 🚀 Universal Shutdown Manager
 * Умный менеджер который автоматически определяет ОС и выбирает подходящий метод
 */
class UniversalShutdownManager {
    companion object {
        private const val TAG = "UniversalShutdownManager"
        private const val DETECTION_TIMEOUT = 5000L // 5 секунд на определение ОС
    }
    
    private val sshManager = SSHManager()
    private val windowsManager = WindowsManager()
    
    /**
     * Автоматически определяет ОС и выполняет shutdown
     */
    suspend fun executeShutdown(preferredOS: OSType = OSType.AUTO): ShutdownResult {
        return when (preferredOS) {
            OSType.UBUNTU -> executeUbuntuShutdown()
            OSType.WINDOWS -> executeWindowsShutdown()
            OSType.AUTO -> executeAutoShutdown()
        }
    }
    
    /**
     * Автоматическое определение ОС и выключение
     */
    private suspend fun executeAutoShutdown(): ShutdownResult {
        Log.d(TAG, "🔍 Автоматическое определение ОС...")
        
        return coroutineScope {
            // Запускаем проверки параллельно для скорости
            val ubuntuCheck = async { 
                withTimeoutOrNull(DETECTION_TIMEOUT) { 
                    checkUbuntuAvailability() 
                } ?: false 
            }
            
            val windowsCheck = async { 
                withTimeoutOrNull(DETECTION_TIMEOUT) { 
                    windowsManager.checkServerHealth() 
                } ?: false 
            }
            
            val ubuntuAvailable = ubuntuCheck.await()
            val windowsAvailable = windowsCheck.await()
            
            Log.d(TAG, "Результаты проверки: Ubuntu=$ubuntuAvailable, Windows=$windowsAvailable")
            
            when {
                windowsAvailable && ubuntuAvailable -> {
                    Log.d(TAG, "🤔 Обе ОС доступны, приоритет Windows")
                    executeWindowsShutdown()
                }
                windowsAvailable -> {
                    Log.d(TAG, "🪟 Выбрана Windows")
                    executeWindowsShutdown()
                }
                ubuntuAvailable -> {
                    Log.d(TAG, "🐧 Выбрана Ubuntu")
                    executeUbuntuShutdown()
                }
                else -> {
                    Log.e(TAG, "❌ Ни одна ОС не доступна")
                    ShutdownResult.Error("Компьютер недоступен. Проверь сеть и настройки.")
                }
            }
        }
    }
    
    /**
     * Выключение Ubuntu через SSH
     */
    private suspend fun executeUbuntuShutdown(): ShutdownResult {
        Log.d(TAG, "🐧 Выключаем Ubuntu через SSH...")
        
        return try {
            val success = sshManager.executeShutdown()
            if (success) {
                ShutdownResult.Success("Ubuntu компьютер успешно выключен!", OSType.UBUNTU)
            } else {
                ShutdownResult.Error("Ошибка SSH подключения к Ubuntu")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ubuntu shutdown exception: ${e.message}", e)
            ShutdownResult.Error("Ошибка выключения Ubuntu: ${e.message}")
        }
    }
    
    /**
     * Выключение Windows через HTTP API
     */
    private suspend fun executeWindowsShutdown(): ShutdownResult {
        Log.d(TAG, "🪟 Выключаем Windows через HTTP API...")
        
        return try {
            val success = windowsManager.executeShutdown(delay = 10, force = true)
            if (success) {
                ShutdownResult.Success("Windows компьютер успешно выключен!", OSType.WINDOWS)
            } else {
                ShutdownResult.Error("Ошибка подключения к Windows серверу")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Windows shutdown exception: ${e.message}", e)
            ShutdownResult.Error("Ошибка выключения Windows: ${e.message}")
        }
    }
    
    /**
     * Перезагрузка (только для Windows пока)
     */
    suspend fun executeRestart(osType: OSType = OSType.AUTO): ShutdownResult {
        return when (osType) {
            OSType.WINDOWS -> {
                try {
                    val success = windowsManager.executeRestart()
                    if (success) {
                        ShutdownResult.Success("Windows компьютер перезагружается!", OSType.WINDOWS)
                    } else {
                        ShutdownResult.Error("Ошибка перезагрузки Windows")
                    }
                } catch (e: Exception) {
                    ShutdownResult.Error("Ошибка перезагрузки: ${e.message}")
                }
            }
            OSType.AUTO -> {
                // В режиме авто сначала попробуем Windows
                if (windowsManager.checkServerHealth()) {
                    executeRestart(OSType.WINDOWS)
                } else {
                    ShutdownResult.Error("Перезагрузка доступна только для Windows")
                }
            }
            else -> ShutdownResult.Error("Перезагрузка пока поддерживается только для Windows")
        }
    }
    
    /**
     * Проверяем доступность Ubuntu (упрощенная проверка)
     */
    private suspend fun checkUbuntuAvailability(): Boolean {
        return try {
            // Используем новый метод checkConnection для проверки доступности SSH
            sshManager.checkConnection()
        } catch (e: Exception) {
            // Любая ошибка означает, что SSH недоступен
            Log.e(TAG, "Ошибка при проверке доступности Ubuntu: ${e.message}")
            false
        }
    }
    
    /**
     * Получаем статус всех доступных систем
     */
    suspend fun getSystemStatus(): SystemStatus {
        return coroutineScope {
            val ubuntuCheck = async { 
                withTimeoutOrNull(DETECTION_TIMEOUT) { 
                    checkUbuntuAvailability() 
                } ?: false 
            }
            
            val windowsCheck = async { 
                withTimeoutOrNull(DETECTION_TIMEOUT) { 
                    windowsManager.checkServerHealth() 
                } ?: false 
            }
            
            SystemStatus(
                ubuntuAvailable = ubuntuCheck.await(),
                windowsAvailable = windowsCheck.await()
            )
        }
    }
}

/**
 * Результат операции выключения
 */
sealed class ShutdownResult {
    data class Success(val message: String, val osType: OSType) : ShutdownResult()
    data class Error(val message: String) : ShutdownResult()
}

/**
 * Статус доступности систем
 */
data class SystemStatus(
    val ubuntuAvailable: Boolean,
    val windowsAvailable: Boolean
) {
    val hasAnySystem: Boolean get() = ubuntuAvailable || windowsAvailable
    val hasBothSystems: Boolean get() = ubuntuAvailable && windowsAvailable
}
