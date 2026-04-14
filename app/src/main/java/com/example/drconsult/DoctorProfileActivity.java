package com.example.drconsult;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

public class DoctorProfileActivity extends AppCompatActivity {

    private static final String TAG = "DoctorProfileActivity";

    private FirebaseFirestore db;
    private String doctorId;
    private String doctorNameStr    = "Doctor";   // default fallback
    private String doctorSpecialtyStr = "Specialist";
    private String currentUserName  = "User";

    private TextView docName, docSpecialty, docReviews,
            statPatients, statExperience, statSuccess, aboutText;
    private TextView availabilityMonday, availabilitySaturday, availabilitySunday;
    private MaterialToolbar toolbar;
    private MaterialButton bookAppointmentButton, videoCallButton, messageButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_profile);

        // ✅ doctorId comes from intent — always available immediately
        if (getIntent() != null) {
            doctorId = getIntent().getStringExtra("DOCTOR_ID");
        }

        if (doctorId == null || doctorId.isEmpty()) {
            Toast.makeText(this, "Error: Doctor ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        toolbar               = findViewById(R.id.toolbar);
        docName               = findViewById(R.id.doctor_profile_name);
        docSpecialty          = findViewById(R.id.doctor_profile_specialty);
        docReviews            = findViewById(R.id.doctor_profile_reviews);
        statPatients          = findViewById(R.id.stat_patients);
        statExperience        = findViewById(R.id.stat_experience);
        statSuccess           = findViewById(R.id.stat_success_rate);
        aboutText             = findViewById(R.id.about_text);
        availabilityMonday    = findViewById(R.id.availability_monday);
        availabilitySaturday  = findViewById(R.id.availability_saturday);
        availabilitySunday    = findViewById(R.id.availability_sunday);
        bookAppointmentButton = findViewById(R.id.book_appointment_button);
        videoCallButton       = findViewById(R.id.video_call_button);
        messageButton         = findViewById(R.id.message_button);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed());
        }

        loadCurrentUserName();
        loadDoctorData();

        // ── Book Appointment ──────────────────────────────────────────────────
        if (bookAppointmentButton != null) {
            bookAppointmentButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, BookAppointmentActivity.class);
                intent.putExtra("DOCTOR_ID",        doctorId);
                intent.putExtra("DOCTOR_NAME",      doctorNameStr);
                intent.putExtra("DOCTOR_SPECIALTY", doctorSpecialtyStr);
                startActivity(intent);
            });
        }

        // ── Message ───────────────────────────────────────────────────────────
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("RECEIVER_ID",   doctorId);
                intent.putExtra("RECEIVER_NAME", doctorNameStr);
                startActivity(intent);
            });
        }

        // ── Video Call ────────────────────────────────────────────────────────
        if (videoCallButton != null) {
            videoCallButton.setOnClickListener(v -> {
                // ✅ doctorId is always set from intent above — never null here
                // doctorNameStr has a default "Doctor" so it's also safe
                Intent intent = new Intent(this, VideoCallActivity.class);
                intent.putExtra("APPOINTMENT_ID", doctorId);   // channel name base
                intent.putExtra("DOCTOR_ID",      doctorId);   // for Firestore call doc
                intent.putExtra("DOCTOR_NAME",    doctorNameStr);
                intent.putExtra("DISPLAY_NAME",   currentUserName);
                startActivity(intent);
            });
        }
    }

    private void loadCurrentUserName() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("username");
                        if (name != null) currentUserName = name;
                    }
                });
    }

    private void loadDoctorData() {
        DocumentReference docRef = db.collection("doctors").document(doctorId);

        docRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                try {
                    String name      = documentSnapshot.getString("name");
                    String specialty = documentSnapshot.getString("specialty");

                    // ✅ Update class fields so video call button uses latest values
                    if (name != null)      doctorNameStr      = name;
                    if (specialty != null) doctorSpecialtyStr = specialty;

                    String reviewCountStr = "(0 reviews)";
                    if (documentSnapshot.contains("reviewCount")) {
                        Object rc = documentSnapshot.get("reviewCount");
                        reviewCountStr = "(" + rc + " reviews)";
                    }

                    String patients    = documentSnapshot.getString("patients");
                    String experience  = documentSnapshot.getString("experience");
                    String successRate = documentSnapshot.getString("successRate");
                    String about       = documentSnapshot.getString("about");

                    if (docName != null)        docName.setText(doctorNameStr);
                    if (docSpecialty != null)   docSpecialty.setText(doctorSpecialtyStr);
                    if (docReviews != null)     docReviews.setText(reviewCountStr);
                    if (statPatients != null)   statPatients.setText(patients != null ? patients : "N/A");
                    if (statExperience != null) statExperience.setText(experience != null ? experience : "N/A");
                    if (statSuccess != null)    statSuccess.setText(successRate != null ? successRate : "N/A");
                    if (aboutText != null)      aboutText.setText(about != null ? about : "No details.");

                    try {
                        Object availabilityObj = documentSnapshot.get("availability");
                        if (availabilityObj instanceof Map) {
                            Map<String, String> availability = (Map<String, String>) availabilityObj;
                            if (availabilityMonday != null)
                                availabilityMonday.setText("Monday - Friday   "
                                        + availability.get("monday_friday"));
                            if (availabilitySaturday != null)
                                availabilitySaturday.setText("Saturday          "
                                        + availability.get("saturday"));
                            if (availabilitySunday != null)
                                availabilitySunday.setText("Sunday            "
                                        + availability.get("sunday"));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Availability format error: " + e.getMessage());
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing doctor data: " + e.getMessage());
                    Toast.makeText(this, "Error showing some details",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Doctor details not found.",
                        Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error loading profile.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error", e);
        });
    }
}