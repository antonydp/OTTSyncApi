package com.adippe.ottSyncApi.example

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.adippe.ottSyncApi.RoomSyncLibrary
import com.adippe.ottSyncApi.SyncEvent
import okhttp3.OkHttpClient

class MainActivity : AppCompatActivity() {
    private lateinit var roomSyncLibrary: RoomSyncLibrary
    private val roomId = "60a4ee0a-e30d-494e-9086-858c42a4414f"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val okHttpClient = OkHttpClient()
        roomSyncLibrary = RoomSyncLibrary(okHttpClient)

        setupButtons()

        // Collect sync events using Flow
        lifecycleScope.launch {
            Log.d("FlowCollect", "Flow collection started")
            roomSyncLibrary.syncMessageFlow.collect { syncEvent ->
                Log.d("FlowCollect", "Received sync event: $syncEvent")
                handleSyncEvent(syncEvent)            }
            Log.d("FlowCollect", "Flow collection ended")
        }
    }

    private fun handleSyncEvent(syncEvent: SyncEvent) {
        when (syncEvent) {
            is SyncEvent.Seek -> {
                val playbackPosition = syncEvent.playbackPosition
                Log.d("SyncEventDebug", "Seek event - Playback Position: $playbackPosition")
                // Handle Seek sync event
            }
            is SyncEvent.PlaybackSpeed -> {
                val playbackPosition = syncEvent.playbackPosition
                val playbackSpeed = syncEvent.playbackSpeed
                Log.d("SyncEventDebug", "PlaybackSpeed event - Playback Position: $playbackPosition, Playback Speed: $playbackSpeed")
                // Handle PlaybackSpeed sync event
            }
            is SyncEvent.Pause -> {
                val playbackPosition = syncEvent.playbackPosition
                Log.d("SyncEventDebug", "Pause event - Playback Position: $playbackPosition")
                // Handle Pause sync event
            }
            is SyncEvent.Play -> {
                Log.d("SyncEventDebug", "Play event")
                // Handle Play sync event
            }
            is SyncEvent.TitleChanged -> {
                val title = syncEvent.titleValue
                Log.d("SyncEventDebug", "Title event - Value: $title")
                // Handle Title sync event
            }
            is SyncEvent.Message -> {
                val messageData = syncEvent.messageData
                Log.d("SyncEventDebug", "Message event - Value: $messageData")
                // Handle Message sync event
            }
        }
    }

    private fun setupButtons() {
        val joinButton = findViewById<Button>(R.id.joinButton)
        val playButton = findViewById<Button>(R.id.playButton)
        val pauseButton = findViewById<Button>(R.id.pauseButton)
        val seekButton = findViewById<Button>(R.id.seekButton)
        val chatButton = findViewById<Button>(R.id.chatButton)
        val updateTitleButton = findViewById<Button>(R.id.updateTitleButton)
        val playbackRate = findViewById<Button>(R.id.updatePlaybackRate)
        val leaveRoom = findViewById<Button>(R.id.leaveRoom)

        joinButton.setOnClickListener {
            lifecycleScope.launch {
                roomSyncLibrary.joinRoom(roomId)
            }
        }

        playButton.setOnClickListener {
            roomSyncLibrary.sendPlayAction()
        }

        pauseButton.setOnClickListener {
            lifecycleScope.launch {
                roomSyncLibrary.sendPauseAction()
            }
        }

        seekButton.setOnClickListener {
            lifecycleScope.launch {
                roomSyncLibrary.sendSeekAction(20)
            }
        }

        chatButton.setOnClickListener {
            lifecycleScope.launch {
                roomSyncLibrary.sendChatAction("Hello, everyone!")
            }
        }

        updateTitleButton.setOnClickListener {
            lifecycleScope.launch {
                roomSyncLibrary.updateRoomTitle(roomId, "ghas")
            }
        }

        playbackRate.setOnClickListener {
            lifecycleScope.launch {
                roomSyncLibrary.sendSetPlaybackRateAction(1.5)
            }
        }

        leaveRoom.setOnClickListener {
            lifecycleScope.launch {
                roomSyncLibrary.leaveRoom()
            }
        }
    }

}
