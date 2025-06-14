package com.example.remoteshutdown

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class SSHManager {
    // Конфигурация подключения - здесь твои данные
    companion object {
        private const val HOST = "192.168.8.101"  // IP твоего компа
        private const val PORT = 22               // Стандартный SSH порт
        private const val USERNAME = "zverev"     // Твой username
        private const val PASSWORD = "zve12345"  // Замени на свой пароль
        private const val SHUTDOWN_COMMAND = "sudo shutdown -h now"
        private const val TAG = "SSHManager"
    }
    
    /**
     * Выполняет shutdown команду через SSH соединение
     * Функция асинхронная - не блокирует UI поток
     */
    suspend fun executeShutdown(): Boolean {
        return withContext(Dispatchers.IO) {  // Выполняем в фоновом потоке
            var session: Session? = null
            
            try {
                Log.d(TAG, "Начинаем SSH подключение к $HOST:$PORT")
                
                // Создаем SSH сессию с помощью JSch библиотеки
                val jsch = JSch()
                session = jsch.getSession(USERNAME, HOST, PORT)
                session.setPassword(PASSWORD)
                
                // Отключаем проверку host key - для локальной сети это безопасно
                session.setConfig("StrictHostKeyChecking", "no")
                
                // Устанавливаем соединение с таймаутом 10 секунд
                session.connect(10000)
                Log.d(TAG, "SSH соединение установлено успешно")
                
                // Создаем канал для выполнения команды
                val channel = session.openChannel("exec")
                (channel as com.jcraft.jsch.ChannelExec).setCommand(SHUTDOWN_COMMAND)
                
                Log.d(TAG, "Отправляем команду shutdown...")
                
                // Выполняем команду
                channel.connect(5000)  // Таймаут 5 секунд на выполнение
                
                // Ждем завершения команды (или таймаут)
                var timeout = 0
                while (!channel.isClosed && timeout < 3000) {
                    Thread.sleep(100)
                    timeout += 100
                }
                
                val exitCode = channel.exitStatus
                channel.disconnect()
                
                Log.d(TAG, "Команда выполнена с кодом: $exitCode")
                
                // Возвращаем true если команда выполнилась успешно
                return@withContext exitCode == 0 || exitCode == -1  // -1 может быть если соединение оборвалось после shutdown
                
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка SSH подключения: ${e.message}", e)
                return@withContext false
            } finally {
                // Обязательно закрываем сессию
                session?.disconnect()
                Log.d(TAG, "SSH сессия закрыта")
            }
        }
    }
}
