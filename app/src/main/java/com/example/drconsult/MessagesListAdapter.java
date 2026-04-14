package com.example.drconsult;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessagesListAdapter extends RecyclerView.Adapter<MessagesListAdapter.ViewHolder> {

    private Context context;
    private List<Chat> chatList;
    private String currentUserId;
    private FirebaseFirestore db;

    // ✅ FIX: Cache resolved names so we never re-fetch the same user from Firestore
    private final Map<String, String> nameCache = new HashMap<>();

    public MessagesListAdapter(Context context, List<Chat> chatList) {
        this.context = context;
        this.chatList = chatList;
        // ✅ FIX: Null-safe currentUser access
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Chat chat = chatList.get(position);

        holder.lastMessage.setText(chat.getLastMessage());

        if (chat.getLastMessageTime() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
            holder.time.setText(sdf.format(chat.getLastMessageTime().toDate()));
        }

        // Find the other participant
        List<String> participants = chat.getParticipants();
        if (participants == null || currentUserId == null) return;

        String otherUserId = null;
        for (String id : participants) {
            if (!id.equals(currentUserId)) {
                otherUserId = id;
                break;
            }
        }

        if (otherUserId == null) return;
        final String finalOtherUserId = otherUserId;

        // ✅ FIX: Check cache first — only fetch from Firestore if we don't already have the name
        if (nameCache.containsKey(finalOtherUserId)) {
            holder.userName.setText(nameCache.get(finalOtherUserId));
            bindClickListener(holder, finalOtherUserId, nameCache.get(finalOtherUserId));
        } else {
            holder.userName.setText("Loading...");
            resolveUserName(finalOtherUserId, holder);
        }
    }

    /**
     * Resolves the name for a user ID.
     * First checks the 'doctors' collection, then falls back to 'users'.
     * Result is stored in nameCache to prevent repeated reads.
     */
    private void resolveUserName(String userId, ViewHolder holder) {
        db.collection("doctors").document(userId).get()
                .addOnSuccessListener(doc -> {
                    String name;
                    if (doc.exists() && doc.getString("name") != null) {
                        name = doc.getString("name");
                        nameCache.put(userId, name); // ✅ Cache the result
                        holder.userName.setText(name);
                        bindClickListener(holder, userId, name);
                    } else {
                        // Not a doctor — check users collection
                        db.collection("users").document(userId).get()
                                .addOnSuccessListener(userDoc -> {
                                    String resolvedName = "Unknown User";
                                    if (userDoc.exists() && userDoc.getString("username") != null) {
                                        resolvedName = userDoc.getString("username");
                                    }
                                    nameCache.put(userId, resolvedName); // ✅ Cache the result
                                    holder.userName.setText(resolvedName);
                                    bindClickListener(holder, userId, resolvedName);
                                });
                    }
                });
    }

    private void bindClickListener(ViewHolder holder, String otherUserId, String name) {
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChatActivity.class);
            intent.putExtra("RECEIVER_ID", otherUserId);
            intent.putExtra("RECEIVER_NAME", name != null ? name : "");
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView userName, lastMessage, time;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            userName = itemView.findViewById(R.id.chat_user_name);
            lastMessage = itemView.findViewById(R.id.chat_last_message);
            time = itemView.findViewById(R.id.chat_time);
        }
    }
}