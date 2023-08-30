package com.adippe.ottSyncApi

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception


class RoomSyncLibrary(private val okHttpClient: OkHttpClient) {

    private val baseUrl = "https://opentogethertube.com"
    private var token: String? = null
    private lateinit var webSocket: WebSocket
    private var userID: String? = null
    private var isAdmin: Boolean = false
    private var roomID: String? = null

    // Create a MutableSharedFlow for sync messages
    private val _syncMessageFlow = MutableSharedFlow<SyncEvent>()
    val syncMessageFlow: Flow<SyncEvent> = _syncMessageFlow.asSharedFlow()

    suspend fun generateRoom(username: String, password: String) : String? = withContext(Dispatchers.IO) {
        generateRoomID()
        joinRoom(roomID?: throw Exception("No roomID"))
        registerUser(username, password)
        performLogin(token?: throw Exception("No token"), username, password)
        claimRoom(roomID?: throw Exception("No roomID"), token?: throw Exception("No token"))
        return@withContext roomID
    }
    private suspend fun generateRoomID() = withContext(Dispatchers.IO) {

        token = getToken() ?: throw Exception("token not found")
        val request = Request.Builder()
            .url("$baseUrl/api/room/generate")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body.string()
            val roomJson = JSONObject(responseBody)
            roomID = roomJson.getString("room")
            Log.d("fah", roomID?:"")
        }
        else {
            throw Exception("Room Generation Exception")
        }
    }
    private suspend fun registerUser(username: String, password: String) = withContext(Dispatchers.IO) {

        val registerRequestBody = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val requestBody = registerRequestBody.toString().toRequestBody("application/json".toMediaTypeOrNull())


        val request = Request.Builder()
            .url("$baseUrl/api/user/register")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (response.isSuccessful) {
            val responseBody = response.body.string()
            val jsonResponse = JSONObject(responseBody)
            val user = jsonResponse.getJSONObject("user")
            val success = jsonResponse.getBoolean("success")

            println("Registration successful: $success")
            println("Username: ${user.getString("username")}")
            println("Email: ${user.getString("email")}")
        } else {
            println("Registration failed: ${response.code}")
        }

        response.close()
    }
    private suspend fun performLogin(token: String, username: String, password: String) = withContext(Dispatchers.IO) {
        val loginRequestBody = JSONObject().apply {
            put("user", username)
            put("password", password)
        }

        val requestBody = loginRequestBody.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$baseUrl/api/user/login")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute()
    }

    // Function to retrieve the auth token
    private suspend fun claimRoom(roomId: String, token: String) = withContext(Dispatchers.IO) {
        val claimRequest = JSONObject().apply { put("claim", true) }
        val requestBody = claimRequest.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$baseUrl/api/room/$roomId")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .patch(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            println("Room claimed successfully")
        } else {
            println("Failed to claim room: ${response.code}")
        }
    }


    private suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/auth/grant")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body.string()
            val tokenJson = JSONObject(responseBody)
            tokenJson.getString("token").also { token = it }
        } else {
            null
        }
    }

    // Function to open a WebSocket connection
    private fun connectWebSocket(roomId: String) {
        val webSocketUrl = "wss://opentogethertube.com/api/room/$roomId"
        val request = Request.Builder()
            .url(webSocketUrl)
            .build()
        webSocket = WebSocket.Factory { _, listener -> okHttpClient.newWebSocket(request, listener) }
            .newWebSocket(request, createWebSocketListener())
    }

    // Function to join the websocket
    suspend fun joinRoom(roomId: String) {
        connectWebSocket(roomId)
        val token = getToken() ?: return
        sendAuthAction(token)
    }

    // Function to create a WebSocketListener
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketDebug", "WebSocket connection opened")
            }


            override fun onMessage(webSocket: WebSocket, text: String) {
                val messageJson = JSONObject(text)
                val action = messageJson.getString("action")
                val webSocketAction = WebSocketAction.valueOf(action.toUpperCase())

                when (webSocketAction) {
                    WebSocketAction.AUTH -> handleAuthMessage(messageJson)
                    WebSocketAction.SYNC -> handleSyncMessage(messageJson)
                    WebSocketAction.EVENT -> handleEventMessage(messageJson)
                    WebSocketAction.USER -> handleUserMessage(messageJson)
                    WebSocketAction.STATUS -> handleStatusMessage(messageJson)
                    WebSocketAction.CHAT -> handleChatMessage(messageJson)
                    WebSocketAction.YOU -> handleYouMessage(messageJson)

                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                val reasonInfo = if (code == 4005) {"kicked"} else reason
                Log.d("WebSocketDebug", "WebSocket connection closed with code $code: $reasonInfo")

                webSocket.cancel()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketDebug", "WebSocket connection closed with code $code: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketDebug", "WebSocket connection failure: ${t.message}")
            }

            // You can override other WebSocketListener methods if needed
        }
    }

    // Function to send a JSON action over WebSocket
    private fun sendWebSocketAction(actionJson: JSONObject) {
        webSocket.send(actionJson.toString())
    }

    // Function to the play auth over WebSocket
    private fun sendAuthAction(token: String) {
        val playPayload = JSONObject()
        playPayload.put("action", "auth")
        playPayload.put("token", token)
        sendWebSocketAction(playPayload)
    }

    // Function to send the play action over WebSocket
    fun sendPlayAction() {
        val playPayload = JSONObject()
        playPayload.put("action", "req")
        playPayload.put("request", JSONObject().apply {
            put("type", 2)
            put("state", true)
        })
        sendWebSocketAction(playPayload)
    }

    // Function to send the pause action over WebSocket
    fun sendPauseAction() {
        val pausePayload = JSONObject()
        pausePayload.put("action", "req")
        pausePayload.put("request", JSONObject().apply {
            put("type", 2)
            put("state", false)
        })
        sendWebSocketAction(pausePayload)
    }

    // Function to send the seek action over WebSocket
    fun sendSeekAction(seconds: Int) {
        val seekPayload = JSONObject()
        seekPayload.put("action", "req")
        seekPayload.put("request", JSONObject().apply {
            put("type", 4)
            put("value", seconds)
        })
        sendWebSocketAction(seekPayload)
    }

    // Function to send the chat action over WebSocket
    fun sendChatAction(message: String) {
        val chatPayload = JSONObject()
        chatPayload.put("action", "req")
        chatPayload.put("request", JSONObject().apply {
            put("type", 11)
            put("text", message)
        })
        sendWebSocketAction(chatPayload)
    }

    // Function to send the set playback rate action over WebSocket
    fun sendSetPlaybackRateAction(speed: Double) {
        val playbackRatePayload = JSONObject()
        playbackRatePayload.put("action", "req")
        playbackRatePayload.put("request", JSONObject().apply {
            put("type", 16)
            put("speed", speed)
        })
        sendWebSocketAction(playbackRatePayload)
    }

    fun kickUser(user: User) {
        val kickPayload = JSONObject()
        kickPayload.put("action", "req")
        kickPayload.put("request", JSONObject().apply {
                put("type", 18)
                put("clientId", user.id)
            })

        if (isAdmin){sendWebSocketAction(kickPayload)}
        else {throw Exception("NOT AN ADMIN")}
    }
    // Function to close the WebSocket
    fun leaveRoom() {
        webSocket.cancel()
    }

    suspend fun updateRoomTitle(roomId: String, newTitle: String) {
        val apiUrl = "$baseUrl/api/room/$roomId"
        val requestBody = JSONObject().apply {
            put("title", newTitle)
        }

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .patch(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        withContext(Dispatchers.IO) {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                println("Room title updated successfully")
            } else {
                println("Failed to update room title")
            }
        }
    }

    private fun handleAuthMessage(messageJson: JSONObject) {
        val token = messageJson.getString("token")
        Log.d("boh", token)
        // Handle authentication token
    }

    private fun handleSyncMessage(messageJson: JSONObject) {
        val playbackPosition = messageJson.optDouble("playbackPosition", Double.NaN)
        val playbackSpeed = messageJson.optDouble("playbackSpeed", Double.NaN)
        val titleChanged = messageJson.optString("title", "")

        val isPlaying = messageJson.optBoolean("isPlaying", false)
        val isSeek = !playbackPosition.isNaN() && !messageJson.has("isPlaying") && !messageJson.has("playbackSpeed")
        val isPlaybackSpeed = !playbackSpeed.isNaN() && !isSeek
        val isPause = !playbackPosition.isNaN() && !isSeek &&!isPlaybackSpeed

        val syncEvent = when {
            isPlaying -> SyncEvent.Play
            isSeek -> SyncEvent.Seek(playbackPosition)
            isPause -> SyncEvent.Pause(playbackPosition)
            isPlaybackSpeed -> SyncEvent.PlaybackSpeed(playbackPosition, playbackSpeed)
            titleChanged.isNotBlank() -> SyncEvent.TitleChanged(titleChanged)
            else -> null
        }

        syncEvent?.let {
            // Emit the sync event through syncMessageFlow
            runBlocking {
                _syncMessageFlow.emit(it)
            }
        }

    }

    private fun handleEventMessage(messageJson: JSONObject) {
        val requestType = messageJson.getJSONObject("request").getInt("type")
        // Handle event based on request type
        Log.d("boh", requestType.toString())

    }

    private fun handleUserMessage(messageJson: JSONObject) {
        val updateType = messageJson.getJSONObject("update").getString("kind")

        val updateObject = messageJson.getJSONObject("update")
        when (val value = updateObject.get("value")) {
            is JSONObject -> {
                // Handle the case where value is a JSONObject
                val user = userFromJsonObject(value)
                Log.d("WebSocketDebug", "${user.name} (ID: ${user.id}), type: $updateType")


                // Now you can use the extracted values as needed
            }

            is JSONArray -> {
                // Handle the case where value is a JSONArray

                for (i in 0 until value.length()) {
                    val userObject = value.getJSONObject(i)
                    val user = userFromJsonObject(userObject)

                    Log.d("WebSocketDebug", "${user.name} (ID: ${user.id})), type: $updateType")

                    // Now you can use the extracted values as needed for each element in the array
                }
            }

            else -> {
                // Handle the case where value has an unexpected type
            }
        }
        // Handle user update based on update type and value
        Log.d("boh", messageJson.toString())

    }

    private fun handleStatusMessage(messageJson: JSONObject) {
        val status = messageJson.getString("status")
        // Handle status update
        Log.d("boh", status.toString())

    }

    private fun handleChatMessage(messageJson: JSONObject) {
        val from = messageJson.getJSONObject("from")
        val user = userFromJsonObject(from)
        val text = messageJson.getString("text")
        val syncEvent = SyncEvent.Message(text)
        syncEvent.let {
            // Emit the sync event through syncMessageFlow
            runBlocking {
                _syncMessageFlow.emit(it)
            }
        }
        // Now you can use the extracted values as needed for handling the chat message
        Log.d("WebSocketDebug", "Received chat message: $text from ${user.name} (ID: ${user.id})")
    }

    private fun handleYouMessage(messageJson: JSONObject) {
        userID = messageJson.getJSONObject("info").getString("id")
        Log.d("WebSocketDebug", "Received userID: $messageJson")
    }

    private fun userFromJsonObject(userObject: JSONObject): User {
        val id = userObject.getString("id")
        val name = userObject.getString("name")
        val isLoggedIn = userObject.getBoolean("isLoggedIn")
        val status = userObject.getString("status")
        val role = userObject.getInt("role")

        return User(id, name, isLoggedIn, status, role)

    }

    suspend fun getUser(roomName: String): List<User>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/room/$roomName")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body.string()
            val roomJson = JSONObject(responseBody)
            val usersJsonArray = roomJson.getJSONArray("users")

            val users = mutableListOf<User>()
            for (i in 0 until usersJsonArray.length()) {
                val userJson = usersJsonArray.getJSONObject(i)
                val user = User(
                    id = userJson.getString("id"),
                    name = userJson.getString("name"),
                    isLoggedIn = userJson.getBoolean("isLoggedIn"),
                    status = userJson.getString("status"),
                    role = userJson.getInt("role")
                )
                if (user.id == userID && user.role == -1) {isAdmin = true}
                users.add(user)
            }
            users
        } else {
            null
        }
    }

}

sealed class SyncEvent {
    data class Seek(val playbackPosition: Double) : SyncEvent()
    data class PlaybackSpeed(val playbackPosition: Double, val playbackSpeed: Double) : SyncEvent()
    data class Pause(val playbackPosition: Double) : SyncEvent()
    object Play : SyncEvent()
    data class TitleChanged(val titleValue: String) : SyncEvent()

    data class Message(val messageData: String) : SyncEvent()

}


enum class WebSocketAction(val value: String) {
    AUTH("auth"),
    SYNC("sync"),
    EVENT("event"),
    USER("user"),
    STATUS("status"),
    CHAT("chat"),
    YOU("you")
}

data class User(
    val id: String,
    val name: String,
    val isLoggedIn: Boolean,
    val status: String,
    val role: Int
)

