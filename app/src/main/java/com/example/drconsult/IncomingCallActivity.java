package com.example.drconsult;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

public class IncomingCallActivity extends AppCompatActivity {

    private static final int CALL_TIMEOUT_MS = 30000; // 30 seconds

    private FirebaseFirestore db;
    private String callDocId;
    private String channelName;
    private String callerName;
    private String doctorName;

    private Handler  timeoutHandler = new Handler();
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

        db = FirebaseFirestore.getInstance();

        // Get call details from the notification/intent
        callDocId   = getIntent().getStringExtra("CALL_DOC_ID");
        channelName = getIntent().getStringExtra("CHANNEL_NAME");
        callerName  = getIntent().getStringExtra("CALLER_NAME");
        doctorName  = getIntent().getStringExtra("DOCTOR_NAME");

        if (callDocId == null || channelName == null) {
            finish();
            return;
        }

        // Bind views
        TextView tvCallerName = findViewById(R.id.tv_incoming_caller_name);
        TextView tvCallType   = findViewById(R.id.tv_incoming_call_type);
        MaterialButton btnAccept  = findViewById(R.id.btn_accept_call);
        MaterialButton btnDecline = findViewById(R.id.btn_decline_call);

        tvCallerName.setText(callerName != null ? callerName : "Patient");
        tvCallType.setText("Incoming Video Consultation");

        // Accept
        btnAccept.setOnClickListener(v -> acceptCall());

        // Decline
        btnDecline.setOnClickListener(v -> declineCall());

        // Auto-decline after 30 seconds if no response
        timeoutRunnable = () -> {
            Toast.makeText(this, "Call missed", Toast.LENGTH_SHORT).show();
            declineCall();
        };
        timeoutHandler.postDelayed(timeoutRunnable, CALL_TIMEOUT_MS);

        // Listen for call cancellation (if patient hangs up before doctor answers)
        listenForCallCancellation();
    }

    private void acceptCall() {
        cancelTimeout();

        // Update Firestore status
        db.collection("calls").document(callDocId)
                .update("status", "accepted")
                .addOnSuccessListener(aVoid -> {
                    // Join the Agora channel
                    Intent intent = new Intent(this, VideoCallActivity.class);
                    intent.putExtra("APPOINTMENT_ID", callDocId);
                    intent.putExtra("DOCTOR_ID",      "");
                    intent.putExtra("DOCTOR_NAME",    callerName);
                    intent.putExtra("DISPLAY_NAME",   doctorName);
                    intent.putExtra("CHANNEL_OVERRIDE", channelName); // use exact channel
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error accepting call.", Toast.LENGTH_SHORT).show());
    }

    private void declineCall() {
        cancelTimeout();
        db.collection("calls").document(callDocId)
                .update("status", "declined")
                .addOnCompleteListener(task -> finish());
    }

    /**
     * Listens for the patient cancelling the call before doctor answers.
     * If status becomes "ended", close this screen automatically.
     */
    private void listenForCallCancellation() {
        db.collection("calls").document(callDocId)
                .addSnapshotListener((snap, error) -> {
                    if (snap == null || !snap.exists()) return;
                    String status = snap.getString("status");
                    if ("ended".equals(status) || "declined".equals(status)) {
                        cancelTimeout();
                        Toast.makeText(this, "Call cancelled.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void cancelTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimeout();
    }
}