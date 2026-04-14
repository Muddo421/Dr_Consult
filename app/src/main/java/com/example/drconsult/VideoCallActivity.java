package com.example.drconsult;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;

public class VideoCallActivity extends AppCompatActivity {

    private static final String TAG    = "AgoraDebug";
    private static final String APP_ID = "70102e326c4a4c81ba59f5342f15d873";
    private static final String TOKEN  = null;
    private static final int PERMISSION_REQUEST_CODE = 22;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    };

    private RtcEngine agoraEngine;
    private boolean isMuted    = false;
    private boolean isVideoOff = false;
    private String  channelName;
    private String  callDocId;
    private String  doctorId;
    private boolean isDoctor = false;

    private FrameLayout localVideoContainer;
    private FrameLayout remoteVideoContainer;
    private ImageButton btnMute, btnVideo, btnEndCall;
    private TextView    tvStatus, tvDoctorName;

    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;

    private final IRtcEngineEventHandler eventHandler = new IRtcEngineEventHandler() {

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.d(TAG, "✅ Joined channel: " + channel + " UID: " + uid);
            runOnUiThread(() ->
                    tvStatus.setText(isDoctor ?
                            "Waiting for patient..." : "Waiting for doctor to join..."));
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            Log.d(TAG, "✅ Remote user joined UID: " + uid);
            runOnUiThread(() -> {
                tvStatus.setVisibility(View.GONE);
                setupRemoteVideo(uid);
                if (!isDoctor) updateCallStatus("accepted");
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            Log.d(TAG, "Remote user left UID: " + uid);
            runOnUiThread(() -> {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText("Other participant left the call.");
                remoteVideoContainer.removeAllViews();
                if (!isDoctor) updateCallStatus("ended");
            });
        }

        @Override
        public void onRemoteVideoStateChanged(int uid, int state, int reason, int elapsed) {
            Log.d(TAG, "Remote video state changed uid=" + uid + " state=" + state);
            runOnUiThread(() -> {
                if (state == 0) {
                    remoteVideoContainer.removeAllViews();
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("Camera turned off");
                } else if (state == 2) {
                    tvStatus.setVisibility(View.GONE);
                    setupRemoteVideo(uid);
                }
            });
        }

        @Override
        public void onError(int err) {
            Log.e(TAG, "Agora error code: " + err);
            runOnUiThread(() ->
                    Toast.makeText(VideoCallActivity.this,
                            "Call error code: " + err, Toast.LENGTH_SHORT).show());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ── Read intent extras ─────────────────────────────────────────────
        String appointmentId   = getIntent().getStringExtra("APPOINTMENT_ID");
        doctorId               = getIntent().getStringExtra("DOCTOR_ID");
        String doctorName      = getIntent().getStringExtra("DOCTOR_NAME");
        String callerName      = getIntent().getStringExtra("DISPLAY_NAME");
        String channelOverride = getIntent().getStringExtra("CHANNEL_OVERRIDE");
        isDoctor               = getIntent().getBooleanExtra("IS_DOCTOR", false);

        // ── Validate ───────────────────────────────────────────────────────
        if (appointmentId == null || appointmentId.isEmpty()) {
            Toast.makeText(this, "Invalid call session.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (doctorId == null || doctorId.isEmpty()) doctorId = appointmentId;
        if (doctorName == null) doctorName = "Doctor";
        if (callerName == null) callerName = "Patient";

        // ✅ Doctor uses exact channelName from Firestore via CHANNEL_OVERRIDE
        // Patient builds channel name from doctorId
        if (channelOverride != null && !channelOverride.isEmpty()) {
            channelName = channelOverride;
        } else {
            channelName = "DrConsult" + appointmentId.replaceAll("[^a-zA-Z0-9]", "");
        }

        Log.d(TAG, "Channel: " + channelName + " | isDoctor: " + isDoctor);

        // ── Bind views ─────────────────────────────────────────────────────
        localVideoContainer  = findViewById(R.id.local_video_container);
        remoteVideoContainer = findViewById(R.id.remote_video_container);
        btnMute              = findViewById(R.id.btn_mute);
        btnVideo             = findViewById(R.id.btn_video);
        btnEndCall           = findViewById(R.id.btn_end_call);
        tvStatus             = findViewById(R.id.tv_call_status);
        tvDoctorName         = findViewById(R.id.tv_call_doctor_name);

        tvDoctorName.setText(isDoctor ? callerName : doctorName);
        tvStatus.setText("Connecting...");

        btnMute.setOnClickListener(v -> toggleMute());
        btnVideo.setOnClickListener(v -> toggleVideo());
        btnEndCall.setOnClickListener(v -> endCall());

        // ── Start call ─────────────────────────────────────────────────────
        if (isDoctor) {
            // Doctor — join Agora directly, no new Firestore doc
            if (permissionsGranted()) {
                initAgoraAndJoin();
            } else {
                ActivityCompat.requestPermissions(
                        this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
            }
        } else {
            // Patient — create Firestore call doc first, then join Agora
            final String finalCallerName = callerName;
            final String finalDoctorName = doctorName;
            createCallDocument(finalCallerName, finalDoctorName, () -> {
                if (permissionsGranted()) {
                    initAgoraAndJoin();
                } else {
                    ActivityCompat.requestPermissions(
                            this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
                }
            });
        }
    }

    // ── Firestore Signaling ───────────────────────────────────────────────────

    private void createCallDocument(String callerName, String doctorName,
                                    Runnable onSuccess) {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Map<String, Object> callData = new HashMap<>();
        callData.put("callerId",    mAuth.getCurrentUser().getUid());
        callData.put("callerName",  callerName);
        callData.put("doctorId",    doctorId);
        callData.put("doctorName",  doctorName);
        callData.put("channelName", channelName); // ✅ exact channel saved to Firestore
        callData.put("status",      "calling");
        callData.put("createdAt",   Timestamp.now());

        db.collection("calls")
                .add(callData)
                .addOnSuccessListener(ref -> {
                    callDocId = ref.getId();
                    Log.d(TAG, "Call doc created: " + callDocId);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create call doc", e);
                    Toast.makeText(this,
                            "Could not initiate call. Check connection.",
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void updateCallStatus(String status) {
        if (callDocId == null) return;
        db.collection("calls").document(callDocId)
                .update("status", status)
                .addOnFailureListener(e -> Log.w(TAG, "Status update failed", e));
    }

    // ── Agora Setup ───────────────────────────────────────────────────────────

    private void initAgoraAndJoin() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext      = getApplicationContext();
            config.mAppId        = APP_ID;
            config.mEventHandler = eventHandler;
            agoraEngine = RtcEngine.create(config);
        } catch (Exception e) {
            Log.e(TAG, "Agora init failed", e);
            Toast.makeText(this, "Error initializing call: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // ✅ Explicitly enable audio and video streams
        agoraEngine.enableAudio();
        agoraEngine.enableVideo();
        agoraEngine.enableLocalAudio(true);
        agoraEngine.enableLocalVideo(true);

        // ✅ Route audio to speaker (not earpiece) so both sides can hear
        agoraEngine.setEnableSpeakerphone(true);

        // ✅ Optimize audio for video call meetings
        agoraEngine.setAudioProfile(
                Constants.AUDIO_PROFILE_DEFAULT,
                Constants.AUDIO_SCENARIO_MEETING);

        agoraEngine.startPreview();
        setupLocalVideo();

        ChannelMediaOptions options = new ChannelMediaOptions();
        options.channelProfile         = Constants.CHANNEL_PROFILE_COMMUNICATION;
        options.clientRoleType         = Constants.CLIENT_ROLE_BROADCASTER;
        options.publishCameraTrack     = true;
        options.publishMicrophoneTrack = true;
        options.autoSubscribeAudio     = true;
        options.autoSubscribeVideo     = true;

        Log.d(TAG, "Joining Agora channel: " + channelName);
        int joinResult = agoraEngine.joinChannel(TOKEN, channelName, 0, options);
        Log.d(TAG, "joinChannel result: " + joinResult); // 0 = success
    }

    private void setupLocalVideo() {
        SurfaceView localView = new SurfaceView(this);
        localView.setZOrderMediaOverlay(true);
        localVideoContainer.removeAllViews();
        localVideoContainer.addView(localView);
        agoraEngine.setupLocalVideo(
                new VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
        Log.d(TAG, "Local video set up");
    }

    private void setupRemoteVideo(int uid) {
        Log.d(TAG, "Setting up remote video for UID: " + uid);
        remoteVideoContainer.removeAllViews();
        SurfaceView remoteView = new SurfaceView(this);
        remoteVideoContainer.addView(remoteView);
        int result = agoraEngine.setupRemoteVideo(
                new VideoCanvas(remoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
        Log.d(TAG, "setupRemoteVideo result: " + result);
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    private void toggleMute() {
        isMuted = !isMuted;
        agoraEngine.enableLocalAudio(!isMuted);
        btnMute.setImageResource(isMuted ?
                android.R.drawable.ic_lock_silent_mode :
                android.R.drawable.ic_lock_silent_mode_off);
        Toast.makeText(this,
                isMuted ? "Microphone muted" : "Microphone on",
                Toast.LENGTH_SHORT).show();
    }

    private void toggleVideo() {
        isVideoOff = !isVideoOff;
        agoraEngine.enableLocalVideo(!isVideoOff);
        localVideoContainer.setVisibility(isVideoOff ? View.INVISIBLE : View.VISIBLE);
        btnVideo.setImageResource(isVideoOff ?
                android.R.drawable.ic_menu_close_clear_cancel :
                android.R.drawable.ic_menu_camera);
        Toast.makeText(this,
                isVideoOff ? "Camera off" : "Camera on",
                Toast.LENGTH_SHORT).show();
    }

    private void endCall() {
        updateCallStatus("ended");
        leaveAndCleanup();
        finish();
    }

    private void leaveAndCleanup() {
        if (agoraEngine != null) {
            agoraEngine.stopPreview();
            agoraEngine.leaveChannel();
        }
        RtcEngine.destroy();
        agoraEngine = null;
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private boolean permissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initAgoraAndJoin();
            } else {
                Toast.makeText(this,
                        "Camera and microphone permission required.",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateCallStatus("ended");
        leaveAndCleanup();
    }
}