package com.example.drconsult;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupView extends AppCompatActivity {

    private static final String TAG = "SignupView";

    private TextInputEditText etUsername, etEmail, etPassword, etConfirmPassword;
    private Button signupButton;
    private TextView tvLogin;
    private ImageButton backButton;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String selectedRole = "patient"; // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup_view);

        // 1. Get the Role from the previous screen
        if (getIntent() != null && getIntent().hasExtra("SELECTED_ROLE")) {
            selectedRole = getIntent().getStringExtra("SELECTED_ROLE");
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etUsername = findViewById(R.id.username_edit_text);
        etEmail = findViewById(R.id.email_edit_text);
        etPassword = findViewById(R.id.password_edit_text);
        etConfirmPassword = findViewById(R.id.confirm_password_edit_text);
        signupButton = findViewById(R.id.signup_button);
        tvLogin = findViewById(R.id.already_have_account);
        backButton = findViewById(R.id.back_button);

        signupButton.setOnClickListener(v -> registerUser());

        tvLogin.setOnClickListener(v -> {
            Intent intent = new Intent(SignupView.this, LoginView.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });

        backButton.setOnClickListener(v -> onBackPressed());
    }

    private void registerUser() {
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(username)) { etUsername.setError("Username is required."); return; }
        if (TextUtils.isEmpty(email)) { etEmail.setError("Email is required."); return; }
        if (TextUtils.isEmpty(password)) { etPassword.setError("Password is required."); return; }
        if (!password.equals(confirmPassword)) { etConfirmPassword.setError("Passwords do not match."); return; }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        saveUserToFirestore(firebaseUser.getUid(), username, email);
                    } else {
                        Toast.makeText(SignupView.this, "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String userId, String username, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("email", email);
        user.put("role", selectedRole);
        user.put("createdAt", com.google.firebase.Timestamp.now());

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(SignupView.this, "Account Created.", Toast.LENGTH_SHORT).show();

                    if ("doctor".equals(selectedRole)) {
                        Intent intent = new Intent(SignupView.this, DoctorRegistrationActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(SignupView.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error writing document", e);
                    // --- CHANGED TO SHOW SPECIFIC ERROR MESSAGE ---
                    Toast.makeText(SignupView.this, "Firestore Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}