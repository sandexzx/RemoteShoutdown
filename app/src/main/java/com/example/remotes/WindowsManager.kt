package com.example.remoteshutdown

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * 🪟 Windows Manager - для работы с Flask сервером
 * Отправляет HTTP запросы на винду для shutdown/restart
 */
class WindowsManager {
    companion object {
        private const val TAG = "WindowsManager"
        private const val HOST = "192.168.8.101"  // IP твоего компа
        private const val PORT = 8888             // Порт Flask сервера
        private const val SECRET_KEY = "zverev_shutdown_2025"  // Авторизация
        private const val TIMEOUT = 10000  // 10 секунд таймаут
    }
    
    /**
     * Проверяем доступность Windows сервера
     */
    suspend fun checkServerHealth(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Проверяем здоровье Windows сервера...")
                
                val url = URL("http://$HOST:$PORT/health")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT
                    readTimeout = TIMEOUT
                    setRequestProperty("User-Agent", "RemoteShutdown-Android/1.0")
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Health check response code: $responseCode")
                
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    Log.d(TAG, "Health response: $response")
                    
                    // Парсим JSON ответ
                    val jsonResponse = JSONObject(response)
                    val status = jsonResponse.getString("status")
                    val hasAdminRights = jsonResponse.optBoolean("admin_rights", false)
                    
                    Log.d(TAG, "Server status: $status, admin rights: $hasAdminRights")
                    
                    return@withContext status == "ok" && hasAdminRights
                } else {
                    Log.e(TAG, "Health check failed with code: $responseCode")
                    return@withContext false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Health check exception: ${e.message}", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Выключаем Windows компьютер через Flask API
     */
    suspend fun executeShutdown(delay: Int = 10, force: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Отправляем shutdown команду на Windows...")
                
                val url = URL("http://$HOST:$PORT/shutdown")
                val connection = url.openConnection() as HttpURLConnection
                
                // Настраиваем POST запрос
                connection.apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT
                    readTimeout = TIMEOUT
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $SECRET_KEY")
                    setRequestProperty("User-Agent", "RemoteShutdown-Android/1.0")
                }
                
                // Формируем JSON payload
                val jsonPayload = JSONObject().apply {
                    put("delay", delay)
                    put("force", force)
                }
                
                // Отправляем данные
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonPayload.toString())
                writer.flush()
                writer.close()
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Shutdown response code: $responseCode")
                
                // Читаем ответ
                val response = if (responseCode < 400) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                }
                
                Log.d(TAG, "Shutdown response: $response")
                
                if (responseCode == 200) {
                    val jsonResponse = JSONObject(response)
                    val status = jsonResponse.getString("status")
                    val message = jsonResponse.optString("message", "")
                    
                    Log.d(TAG, "Shutdown успешно: $message")
                    return@withContext status == "success"
                } else {
                    Log.e(TAG, "Shutdown failed with code: $responseCode, response: $response")
                    return@withContext false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Shutdown exception: ${e.message}", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Перезагружаем Windows компьютер (бонус)
     */
    suspend fun executeRestart(delay: Int = 10, force: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Отправляем restart команду на Windows...")
                
                val url = URL("http://$HOST:$PORT/restart")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT
                    readTimeout = TIMEOUT
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $SECRET_KEY")
                    setRequestProperty("User-Agent", "RemoteShutdown-Android/1.0")
                }
                
                val jsonPayload = JSONObject().apply {
                    put("delay", delay)
                    put("force", force)
                }
                
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonPayload.toString())
                writer.flush()
                writer.close()
                
                val responseCode = connection.responseCode
                val response = if (responseCode < 400) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                }
                
                Log.d(TAG, "Restart response: $response")
                
                if (responseCode == 200) {
                    val jsonResponse = JSONObject(response)
                    val status = jsonResponse.getString("status")
                    return@withContext status == "success"
                } else {
                    Log.e(TAG, "Restart failed with code: $responseCode")
                    return@withContext false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Restart exception: ${e.message}", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Отменяем shutdown (если еще можно)
     */
    suspend fun cancelShutdown(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Отменяем shutdown...")
                
                val url = URL("http://$HOST:$PORT/cancel")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT
                    readTimeout = TIMEOUT
                    setRequestProperty("Authorization", "Bearer $SECRET_KEY")
                    setRequestProperty("User-Agent", "RemoteShutdown-Android/1.0")
                }
                
                val responseCode = connection.responseCode
                val response = connection.inputStream.bufferedReader().readText()
                
                Log.d(TAG, "Cancel response: $response")
                
                return@withContext responseCode == 200
                
            } catch (e: Exception) {
                Log.e(TAG, "Cancel exception: ${e.message}", e)
                return@withContext false
            }
        }
    }
}
