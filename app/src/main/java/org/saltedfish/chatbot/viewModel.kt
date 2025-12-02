package org.saltedfish.chatbot

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Photo(
    var id: Int = 0,
    val uri: Uri,
    val request: ImageRequest?
)

class ChatViewModel : ViewModel() {

    private var _messageList = mutableStateListOf<Message>()
    val messageList: List<Message> = _messageList

    var isBusy = mutableStateOf(false)
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private var _lastId = 0

    fun sendMessage(context: Context, message: Message) {
        val userMsgText = message.text
        if (message.type == MessageType.TEXT && userMsgText.isEmpty()) return

        message.id = _lastId++
        _messageList.add(message)

        val botMessage = Message("...", false, _lastId++)
        _messageList.add(botMessage)

        isBusy.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (message.type == MessageType.IMAGE && message.content != null) {
                    val imageUri = message.content as Uri
                    val base64Image = uriToBase64(context, imageUri)

                    if (base64Image != null) {
                        streamResponseOCR(userMsgText, base64Image) { partialText, isDone ->
                            updateBotMessage(botMessage, partialText, isDone)
                        }
                    } else {
                        updateBotMessage(botMessage, "Error: Failed to load image", true)
                    }
                } else {
                    streamResponseQwen(userMsgText) { partialText, isDone ->
                        updateBotMessage(botMessage, partialText, isDone)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateBotMessage(botMessage, "Error: ${e.message}", true)
            }
        }
    }

    private fun updateBotMessage(botMessage: Message, partialText: String, isDone: Boolean) {
        val index = _messageList.indexOf(botMessage)
        if (index != -1) {
            val currentText = _messageList[index].text
            val newText = if (currentText == "...") partialText else currentText + partialText

            val updatedMessage = botMessage.copy(text = newText)
            _messageList[index] = updatedMessage
            botMessage.text = newText
        }
        if (isDone) {
            isBusy.value = false
        }
    }

    private fun streamResponseQwen(prompt: String, onUpdate: (String, Boolean) -> Unit) {
        val apiUrl = AppConfig.getCurrentApiUrl()
        val messagesArray = JSONArray()

        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", "You are a helpful assistant.")
        })

        val history = _messageList.toList()
        for (i in 0 until history.size - 1) {
            val msg = history[i]
            if (msg.type == MessageType.TEXT) {
                messagesArray.put(JSONObject().apply {
                    put("role", if (msg.isUser) "user" else "assistant")
                    put("content", msg.text)
                })
            }
        }

        val modelName = if (AppConfig.useLocalModel) "qwen" else AppConfig.cloudModelName

        val jsonBody = JSONObject().apply {
            put("model", modelName)
            put("stream", true)
            put("messages", messagesArray)
        }

        sendRequest(apiUrl, jsonBody, onUpdate)
    }

    private fun streamResponseOCR(prompt: String, base64Image: String, onUpdate: (String, Boolean) -> Unit) {
        val apiUrl = AppConfig.getCurrentApiUrl()
        val messagesArray = JSONArray()

        val userContent = JSONObject().apply {
            put("role", "user")
            val contentParts = JSONArray()
            contentParts.put(JSONObject().apply {
                put("type", "text")
                put("text", if (prompt.isEmpty()) "Describe this image" else prompt)
            })
            contentParts.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", base64Image)
                })
            })
            put("content", contentParts)
        }
        messagesArray.put(userContent)

        val modelName = if (AppConfig.useLocalModel) "ocr" else AppConfig.cloudModelName

        val jsonBody = JSONObject().apply {
            put("model", modelName)
            put("stream", true)
            put("messages", messagesArray)
        }

        sendRequest(apiUrl, jsonBody, onUpdate)
    }

    private fun sendRequest(url: String, jsonBody: JSONObject, onUpdate: (String, Boolean) -> Unit) {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, jsonBody.toString())

        val requestBuilder = Request.Builder().url(url).post(body)

        if (!AppConfig.useLocalModel && AppConfig.apiKey.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer ${AppConfig.apiKey}")
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                onUpdate("Server Error: ${response.code} ${response.message}", true)
                return
            }
            val source = response.body?.source() ?: return
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        if (json.has("choices")) {
                            val choices = json.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val delta = choices.getJSONObject(0).getJSONObject("delta")
                                if (delta.has("content")) {
                                    onUpdate(delta.getString("content"), false)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
            onUpdate("", true)
        }
    }

    private fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    var functions_: Any? = null
    var docVecDB: Any? = null
    val photoList = MutableLiveData<List<Photo>>(listOf())
    val previewUri = MutableLiveData<Uri?>(null)
    val isLoading = MutableLiveData(false)
    val modelId = MutableLiveData(0)
    val modelType = MutableLiveData(0)
    val profilingTime = MutableLiveData<DoubleArray>()

    fun setModelType(type: Int) {}
    fun setBackendType(type: Int) {}
    fun setModelId(id: Int) {}
    fun setPreviewUri(uri: Uri?) {
        previewUri.value = uri
    }
    fun addPhoto(photo: Photo): Int { return 0 }
    fun initStatus(context: Context, modelType: Int = 0) {}
}

class VQAViewModel : ViewModel() { fun initStatus(c: Context) {} }
class SummaryViewModel : ViewModel() { fun initStatus() {} }
class PhotoViewModel : ViewModel() { fun setBitmap(b: android.graphics.Bitmap) {} }