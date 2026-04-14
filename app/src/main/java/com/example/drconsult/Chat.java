package com.example.drconsult;

import com.google.firebase.Timestamp;
import java.util.List;

public class Chat {
    private String documentId;
    private List<String> participants;
    private String lastMessage;
    private Timestamp lastMessageTime;

    public Chat() {} // Required for Firestore

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public List<String> getParticipants() { return participants; }
    public String getLastMessage() { return lastMessage; }
    public Timestamp getLastMessageTime() { return lastMessageTime; }
}