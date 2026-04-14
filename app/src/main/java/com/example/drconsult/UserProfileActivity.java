package com.example.drconsult;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserProfileActivity extends AppCompatActivity {

    private static final String TAG = "UserProfileActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextView profileName, profileEmail;
    private MaterialButton logoutButton;
    private MaterialButton btnSetAvailability; // doctor only
    private MaterialButton btnAdminPanel;      // admin only
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        profileName        = findViewById(R.id.user_profile_name);
        profileEmail       = findViewById(R.id.user_profile_email);
        logoutButton       = findViewById(R.id.logout_button);
        btnSetAvailability = findViewById(R.id.btn_set_availability);
        btnAdminPanel      = findViewById(R.id.btn_admin_panel);
        toolbar            = findViewById(R.id.toolbar_user_profile);

        toolbar.setNavigationOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());

        logoutButton.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(UserProfileActivity.this, LoginView.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        loadUserData();
    }

    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Null guard — redirect if not logged in
        if (currentUser == null) {
            startActivity(new Intent(this, LoginView.class));
            finish();
            return;
        }

        String userId = currentUser.getUid();
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get()
                .addOnSuccessListener(snap -> {

                    // ✅ Debug logs — all inside the listener where 'snap' exists
                    Log.d("AdminDebug", "Current UID: " + currentUser.getUid());
                    Log.d("AdminDebug", "All fields: " + snap.getData());
                    Log.d("AdminDebug", "isAdmin = " + snap.getBoolean("isAdmin"));
                    Log.d("AdminDebug", "btnAdminPanel null? " + (btnAdminPanel == null));

                    if (snap.exists()) {
                        String name  = snap.getString("username");
                        String email = currentUser.getEmail();

                        profileName.setText(name != null ? name : "No name found");
                        profileEmail.setText(email);

                        // Show availability button for doctors only
                        String role = snap.getString("role");
                        if ("doctor".equals(role)) {
                            btnSetAvailability.setVisibility(View.VISIBLE);
                            btnSetAvailability.setOnClickListener(v ->
                                    startActivity(new Intent(UserProfileActivity.this,
                                            DoctorAvailabilityActivity.class))
                            );
                        }

                        // Show admin panel button for admins only
                        Boolean isAdmin = snap.getBoolean("isAdmin");
                        if (Boolean.TRUE.equals(isAdmin)) {
                            btnAdminPanel.setVisibility(View.VISIBLE);
                            btnAdminPanel.setOnClickListener(v ->
                                    startActivity(new Intent(UserProfileActivity.this,
                                            AdminVerificationActivity.class))
                            );
                        }

                    } else {
                        Log.d(TAG, "No such user document");
                        profileEmail.setText(currentUser.getEmail());
                        profileName.setText("No name found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user data", e);
                    Toast.makeText(this, "Error loading profile",
                            Toast.LENGTH_SHORT).show();
                });
    }
}