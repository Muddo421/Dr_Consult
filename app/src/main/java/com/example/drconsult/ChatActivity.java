package com.example.drconsult;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private String receiverId, receiverName;
    private String currentUserId;
    private String chatId;

    private EditText editMessage;
    private ImageButton btnSend;
    private TextView chatUserName;
    private RecyclerView recyclerChat;
    private ChatAdapter chatAdapter;
    private List<Message> messageList;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = FirebaseFirestore.getInstance();

        // ✅ FIX: Null guard — check auth before accessing UID
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginView.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        // Get details from Intent
        receiverId = getIntent().getStringExtra("RECEIVER_ID");
        receiverName = getIntent().getStringExtra("RECEIVER_NAME");

        if (receiverId == null) {
            Toast.makeText(this, "Error: User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Generate Chat ID (sorted UIDs so it's always the same for both users)
        chatId = generateChatId(currentUserId, receiverId);

        // UI Setup
        Toolbar toolbar = findViewById(R.id.toolbar_chat);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        // ✅ FIX: Replace deprecated onBackPressed()
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        chatUserName = findViewById(R.id.chat_user_name);
        chatUserName.setText(receiverName);

        editMessage = findViewById(R.id.edit_chat_message);
        btnSend = findViewById(R.id.btn_send_chat);
        recyclerChat = findViewById(R.id.recycler_chat);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messageList);
        recyclerChat.setAdapter(chatAdapter);
        recyclerChat.setLayoutManager(new LinearLayoutManager(this));

        btnSend.setOnClickListener(v -> sendMessage());

        listenForMessages();
    }

    private void listenForMessages() {
        db.collection("chats").document(chatId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    if (value != null) {
                        for (DocumentChange dc : value.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                Message message = dc.getDocument().toObject(Message.class);
                                messageList.add(message);
                                chatAdapter.notifyItemInserted(messageList.size() - 1);
                                recyclerChat.scrollToPosition(messageList.size() - 1);
                            }
                        }
                    }
                });
    }

    private void sendMessage() {
        String msgText = editMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msgText)) return;

        editMessage.setText("");

        Timestamp now = Timestamp.now();
        Message message = new Message(currentUserId, receiverId, msgText, now);

        // Add message to sub-collection
        db.collection("chats").document(chatId).collection("messages").add(message);

        // Update main chat document for recent chats list
        Map<String, Object> chatInfo = new HashMap<>();
        chatInfo.put("participants", Arrays.asList(currentUserId, receiverId));
        chatInfo.put("lastMessage", msgText);
        chatInfo.put("lastMessageTime", now);
        db.collection("chats").document(chatId).set(chatInfo);
    }

    private String generateChatId(String user1, String user2) {
        if (user1.compareTo(user2) < 0) {
            return user1 + "_" + user2;
        } else {
            return user2 + "_" + user1;
        }
    }
}
