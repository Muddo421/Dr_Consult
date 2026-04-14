package com.example.drconsult;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;

public class RoleSelectionActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    // Set when coming from Google Sign-In
    private boolean fromGoogle = false;
    private String  googleUserId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        db = FirebaseFirestore.getInstance();

        // Check if we came from Google Sign-In
        if (getIntent() != null) {
            fromGoogle    = getIntent().getBooleanExtra("FROM_GOOGLE", false);
            googleUserId  = getIntent().getStringExtra("USER_ID");
        }

        MaterialCardView cardDoctor  = findViewById(R.id.card_doctor);
        MaterialCardView cardPatient = findViewById(R.id.card_patient);

        cardDoctor.setOnClickListener(v -> handleRoleSelection("doctor"));
        cardPatient.setOnClickListener(v -> handleRoleSelection("patient"));
    }

    private void handleRoleSelection(String role) {
        if (fromGoogle && googleUserId != null) {
            // Google user — save their role to Firestore then route correctly
            saveRoleForGoogleUser(googleUserId, role);
        } else {
            // Regular signup — go to SignupView with the selected role
            goToSignup(role);
        }
    }

    /**
     * For Google Sign-In users: updates their role in Firestore
     * then routes them to the correct next screen.
     */
    private void saveRoleForGoogleUser(String userId, String role) {
        db.collection("users").document(userId)
                .update("role", role)
                .addOnSuccessListener(aVoid -> {
                    if ("doctor".equals(role)) {
                        // Doctor needs to complete their profile
                        Intent intent = new Intent(this, DoctorRegistrationActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        // Patient goes straight to home
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error saving role. Try again.",
                                Toast.LENGTH_SHORT).show());
    }

    private void goToSignup(String role) {
        Intent intent = new Intent(this, SignupView.class);
        intent.putExtra("SELECTED_ROLE", role);
        startActivity(intent);
    }
}