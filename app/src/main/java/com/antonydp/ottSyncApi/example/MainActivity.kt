package com.antonydp.ottSyncApi.example

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import com.antonydp.ottSyncApi.RoomSyncLibrary
import com.antonydp.ottSyncApi.SyncEvent
import com.antonydp.ottSyncApi.User
import kotlinx.coroutines.MainScope
import okhttp3.OkHttpClient

class MainActivity : AppCompatActivity() {
    private lateinit var roomSyncLibrary: RoomSyncLibrary
    private lateinit var roomId: String

    private val users: MutableList<User> = mutableListOf()
    private lateinit var userAdapter: UserAdapter
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
                handleSyncEvent(syncEvent)
            }
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
            is SyncEvent.Status -> {
                val statusData = syncEvent.statusData
                val userID = syncEvent.userID
                Log.d("SyncEventDebug", "Status event - Value: $statusData, userID: $userID")
            }
            is SyncEvent.SourceUpdated -> {
                val source = syncEvent.source
                Log.d("SyncEventDebug", "Source event - Source Title: ${source.title}, Source ID (link for direct urls): ${source.id} ")
            }
        }
    }

    private fun setupButtons() {
        val roomIdEditText = findViewById<EditText>(R.id.roomIdEditText)
        val userIdEditText = findViewById<EditText>(R.id.userEditText)
        val passwordIdEditText = findViewById<EditText>(R.id.passwordEditText)
        val joinButton = findViewById<Button>(R.id.joinButton)
        val generateRoomButton = findViewById<Button>(R.id.generateRoomButton)
        val playButton = findViewById<Button>(R.id.playButton)
        val pauseButton = findViewById<Button>(R.id.pauseButton)
        val seekButton = findViewById<Button>(R.id.seekButton)
        val chatButton = findViewById<Button>(R.id.chatButton)
        val updateTitleButton = findViewById<Button>(R.id.updateTitleButton)
        val playbackRate = findViewById<Button>(R.id.updatePlaybackRate)
        val leaveRoom = findViewById<Button>(R.id.leaveRoom)
        val playVideo = findViewById<Button>(R.id.playVideoUrl)
        val playVideoEditText = findViewById<EditText>(R.id.playVideoUrlEditText)
        val bufferingButon = findViewById<Button>(R.id.buffering)
        val readyButon = findViewById<Button>(R.id.ready)
        val getUserButton = findViewById<Button>(R.id.getUserButton)
        val userRecyclerView = findViewById<RecyclerView>(R.id.userRecyclerView)

        userAdapter = UserAdapter(
            users,
            kickUserClickListener = { user ->
                val success = roomSyncLibrary.kickUser(user)
                if (!success) {
                    Toast.makeText(this@MainActivity, "you need to be admin to kick user", Toast.LENGTH_SHORT).show()
                }
            },
            promoteUserClickListener = { user ->
                lifecycleScope.launch {
                    val success = roomSyncLibrary.promoteUser(user)
                    if (!success) {
                        Toast.makeText(this@MainActivity, "you need to be admin to promote user and the user to promote has to be logged", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        userRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = userAdapter
        }

        getUserButton.setOnClickListener {
            val roomId = roomIdEditText.text.toString()
            if (roomId.isNotEmpty()) {
                MainScope().launch {
                    val userList = roomSyncLibrary.getUser(roomId)
                    users.clear()
                    users.addAll(userList?: emptyList())
                    userAdapter.notifyDataSetChanged()
                }
            }
            else {
                // Handle empty input case
                Toast.makeText(this, "Please enter a valid Room ID", Toast.LENGTH_SHORT).show()
            }

        }

        joinButton.setOnClickListener {

            val roomId = roomIdEditText.text.toString()
            if (roomId.isNotEmpty()) {
                lifecycleScope.launch {
                    roomSyncLibrary.joinRoom(roomId)
                }
            } else {
                // Handle empty input case
                Toast.makeText(this, "Please enter a valid Room ID", Toast.LENGTH_SHORT).show()
            }
        }

        generateRoomButton.setOnClickListener {
            val user = userIdEditText.text.toString()
            val password = passwordIdEditText.text.toString()
            if (user.isNotEmpty() && password.isNotEmpty()) {
                lifecycleScope.launch {
                    roomId = roomSyncLibrary.generateRoom(user, password) ?: throw Exception("NO roomID")
                    roomIdEditText.text.clear()
                    roomIdEditText.append(roomId)

                }
            }
            else{
                Toast.makeText(this, "Please enter a valid user and password", Toast.LENGTH_SHORT).show()
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
            val roomId = roomIdEditText.text.toString()
            if (roomId.isNotEmpty()) {
                lifecycleScope.launch {
                    roomSyncLibrary.updateRoomTitle(roomId, "ghas")
                }
            }
            else {
                // Handle empty input case
                Toast.makeText(this, "Please enter a valid Room ID", Toast.LENGTH_SHORT).show()
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

        playVideo.setOnClickListener {

            val videoUrl = playVideoEditText.text.toString()
            if (videoUrl.isNotEmpty()) {
                lifecycleScope.launch {
                    roomSyncLibrary.setCurrentSource(videoUrl)
                }
            } else {
                // Handle empty input case
                Toast.makeText(this, "Please enter a valid video Url", Toast.LENGTH_SHORT).show()
            }
        }

        bufferingButon.setOnClickListener {
            roomSyncLibrary.sendBufferingAction()
        }

        readyButon.setOnClickListener {
            roomSyncLibrary.sendReadyAction()
        }

    }

}
class UserAdapter(
    private val userList: List<User>,
    private val promoteUserClickListener: (User) -> Unit,
    private val kickUserClickListener: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.user_item, parent, false)
        return UserViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val currentUser = userList[position]
        holder.bind(currentUser)
    }

    override fun getItemCount(): Int {
        return userList.size
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        private val idTextView: TextView = itemView.findViewById(R.id.idTextView)
        private val roleTextView: TextView = itemView.findViewById(R.id.roleTextView)
        private val kickButton: Button = itemView.findViewById(R.id.kickButton)
        private val promoteButton: Button = itemView.findViewById(R.id.promoteButton)

        fun bind(user: User) {
            nameTextView.text = user.name
            idTextView.text = user.id
            roleTextView.text = user.role.toString()

            kickButton.setOnClickListener {
                kickUserClickListener(user)
            }
            promoteButton.setOnClickListener {
                promoteUserClickListener(user)
            }
        }
    }
}