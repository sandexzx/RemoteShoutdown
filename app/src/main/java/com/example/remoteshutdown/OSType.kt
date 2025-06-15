package com.example.remoteshutdown

/**
 * Enum для типов операционных систем
 */
enum class OSType(val displayName: String, val emoji: String) {
    UBUNTU("Ubuntu", "🐧"),
    WINDOWS("Windows", "🪟"),
    AUTO("Автоопределение", "🔍")
}
