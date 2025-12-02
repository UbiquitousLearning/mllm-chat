package org.saltedfish.chatbot

enum class RouteDestination {
    LOCAL_MODEL,
    CLOUD_API
}

object AppConfig {
    var useLocalModel: Boolean = true

    var apiKey: String = ""
    var cloudApiUrl: String = "https://api.deepseek.com/chat/completions"
    var cloudModelName: String = "deepseek-chat"

    const val LOCAL_API_URL = "http://127.0.0.1:8080/v1/chat/completions"

    fun getCurrentApiUrl(): String {
        return if (useLocalModel) LOCAL_API_URL else cloudApiUrl
    }

    fun determineRoute(prompt: String, hasImage: Boolean): RouteDestination {
        if (!useLocalModel) return RouteDestination.CLOUD_API
        return RouteDestination.LOCAL_MODEL
    }
}