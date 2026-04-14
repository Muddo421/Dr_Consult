package com.example.drconsult;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginView extends AppCompatActivity {

    private static final String TAG = "LoginView";
    private static final String WEB_CLIENT_ID =
            "548815050791-c0dg7hehgpgvakcu8n3jljd622pft7fu.apps.googleusercontent.com";

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // ── Google Sign-In ────────────────────────────────────────────────────────
    private GoogleSignInClient googleSignInClient;

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextInputEditText editTextEmail, editTextPassword;
    private Button buttonLogin;
    private TextView textViewSignUp, textViewForgotPassword;
    private SignInButton btnGoogleSignIn;

    // ── Google Sign-In Launcher ───────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> handleGoogleSignInResult(result)
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_view);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        // ── Bind views ────────────────────────────────────────────────────────
        editTextEmail         = findViewById(R.id.email_edit_text);
        editTextPassword      = findViewById(R.id.password_edit_text);
        buttonLogin           = findViewById(R.id.login_button);
        textViewSignUp        = findViewById(R.id.new_user);
        textViewForgotPassword = findViewById(R.id.forgotpassword);
        btnGoogleSignIn       = findViewById(R.id.btn_google_sign_in);

        // ── Google Sign-In setup ──────────────────────────────────────────────
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // ── If already logged in skip login screen ────────────────────────────
        if (mAuth.getCurrentUser() != null) {
            toggleUI(false);
            checkUserRole(mAuth.getCurrentUser().getUid());
        }

        // ── Listeners ─────────────────────────────────────────────────────────
        textViewSignUp.setOnClickListener(v ->
                startActivity(new Intent(LoginView.this, RoleSelectionActivity.class)));

        textViewForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        buttonLogin.setOnClickListener(v -> loginUser());

        // ── Google Sign-In button ─────────────────────────────────────────────
        btnGoogleSignIn.setOnClickListener(v -> {
            // Sign out first to always show account picker
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        });
    }

    // ── Google Sign-In Result ─────────────────────────────────────────────────

    private void handleGoogleSignInResult(ActivityResult result) {
        Task<GoogleSignInAccount> task =
                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            Log.d(TAG, "Google sign-in succeeded: " + account.getEmail());
            firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            Log.w(TAG, "Google sign-in failed", e);
            Toast.makeText(this, "Google Sign-In failed. Try again.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        toggleUI(false);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) return;

                    // Check if this Google user already exists in Firestore
                    db.collection("users").document(user.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    // Returning user — check their role and route normally
                                    checkUserRole(user.getUid());
                                } else {
                                    // New Google user — save basic info then show role selection
                                    saveNewGoogleUser(user);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error checking user doc", e);
                                toggleUI(true);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Firebase auth with Google failed", e);
                    Toast.makeText(this, "Authentication failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    toggleUI(true);
                });
    }

    /**
     * Saves a new Google user to Firestore without a role yet,
     * then sends them to RoleSelectionActivity to pick doctor or patient.
     */
    private void saveNewGoogleUser(FirebaseUser user) {
        String username = user.getDisplayName() != null ?
                user.getDisplayName() : "User";

        Map<String, Object> userData = new HashMap<>();
        userData.put("username", username);
        userData.put("email",    user.getEmail());
        userData.put("role",     ""); // role will be set after selection
        userData.put("createdAt", com.google.firebase.Timestamp.now());
        userData.put("loginMethod", "google");

        db.collection("users").document(user.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    // Send to role selection screen
                    Intent intent = new Intent(LoginView.this, RoleSelectionActivity.class);
                    intent.putExtra("FROM_GOOGLE", true);
                    intent.putExtra("USER_ID", user.getUid());
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving Google user", e);
                    Toast.makeText(this, "Error saving profile. Try again.",
                            Toast.LENGTH_SHORT).show();
                    toggleUI(true);
                });
    }

    // ── Email/Password Login ──────────────────────────────────────────────────

    private void loginUser() {
        String email    = String.valueOf(editTextEmail.getText()).trim();
        String password = String.valueOf(editTextPassword.getText()).trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Enter email", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show();
            return;
        }

        toggleUI(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        checkUserRole(mAuth.getCurrentUser().getUid());
                    } else {
                        Log.w(TAG, "Login failed", task.getException());
                        Toast.makeText(this, "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                        toggleUI(true);
                    }
                });
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    private void showForgotPasswordDialog() {
        final EditText emailInput = new EditText(this);
        emailInput.setHint("Enter your registered email");
        emailInput.setInputType(
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setPadding(48, 24, 48, 24);

        new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("We'll send a password reset link to your email.")
                .setView(emailInput)
                .setPositiveButton("Send Link", (dialog, which) -> {
                    String email = emailInput.getText().toString().trim();
                    if (TextUtils.isEmpty(email)) {
                        Toast.makeText(this, "Please enter your email.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mAuth.sendPasswordResetEmail(email)
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this,
                                            "Reset link sent to " + email,
                                            Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Role Check & Navigation ───────────────────────────────────────────────

    private void checkUserRole(String userId) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String role = doc.getString("role");
                        if ("doctor".equals(role)) {
                            checkIfDoctorProfileExists(userId);
                        } else if ("patient".equals(role)) {
                            goToMainActivity();
                        } else {
                            // Role not set yet (Google user) — show role selection
                            Intent intent = new Intent(this, RoleSelectionActivity.class);
                            intent.putExtra("FROM_GOOGLE", true);
                            intent.putExtra("USER_ID", userId);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }
                    } else {
                        goToMainActivity();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Network error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    toggleUI(true);
                });
    }

    private void checkIfDoctorProfileExists(String userId) {
        db.collection("doctors").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        goToMainActivity();
                    } else {
                        Intent intent = new Intent(this, DoctorRegistrationActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error checking profile.",
                            Toast.LENGTH_SHORT).show();
                    toggleUI(true);
                });
    }

    private void goToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── UI Helper ─────────────────────────────────────────────────────────────

    private void toggleUI(boolean show) {
        int visibility = show ? View.VISIBLE : View.INVISIBLE;
        if (editTextEmail != null)          safeSetVisibility(editTextEmail, visibility);
        if (editTextPassword != null)       safeSetVisibility(editTextPassword, visibility);
        if (buttonLogin != null)            buttonLogin.setVisibility(visibility);
        if (textViewSignUp != null)         textViewSignUp.setVisibility(visibility);
        if (textViewForgotPassword != null) textViewForgotPassword.setVisibility(visibility);
        if (btnGoogleSignIn != null)        btnGoogleSignIn.setVisibility(visibility);
    }

    private void safeSetVisibility(View view, int visibility) {
        if (view.getParent() instanceof View) {
            ((View) view.getParent()).setVisibility(visibility);
        } else {
            view.setVisibility(visibility);
        }
    }
}