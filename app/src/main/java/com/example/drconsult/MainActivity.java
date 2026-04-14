package com.example.drconsult;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private RecyclerView doctorsRecyclerView;
    private DoctorAdapter doctorAdapter;
    private List<Doctor> doctorList;
    private List<Doctor> fullDoctorList;
    private BottomNavigationView bottomNavigationView;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private TextView userNameText;
    private TextView greetingText;
    private EditText searchInput;

    // ✅ Listener for incoming calls — kept so we can remove it onDestroy
    private ListenerRegistration callListener;
    private String currentUserRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Guard: redirect to login if not authenticated
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginView.class));
            finish();
            return;
        }

        // Initialize Views
        userNameText         = findViewById(R.id.user_name_text);
        greetingText         = findViewById(R.id.greeting_text);
        searchInput          = findViewById(R.id.search_input);
        doctorsRecyclerView  = findViewById(R.id.doctors_recycler_view);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        setGreeting();
        loadUserInfo(currentUser.getUid());

        // Setup RecyclerView
        doctorsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        doctorList     = new ArrayList<>();
        fullDoctorList = new ArrayList<>();
        doctorAdapter  = new DoctorAdapter(doctorList);
        doctorsRecyclerView.setAdapter(doctorAdapter);

        loadDoctors();

        // Live search
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDoctors(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterDoctors(searchInput.getText().toString());
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        setupBottomNavigation();

        // FCM token registration
        DrConsultMessagingService.subscribeToNotifications(currentUser.getUid());

        // ✅ Start listening for incoming calls if user is a doctor
        listenForIncomingCallsIfDoctor(currentUser.getUid());
    }

    /**
     * Checks if the logged-in user is a doctor.
     * If yes, starts a Firestore real-time listener on the 'calls' collection
     * filtered to their UID with status == "calling".
     * When a call comes in, opens IncomingCallActivity automatically.
     */
    private void listenForIncomingCallsIfDoctor(String userId) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentUserRole = doc.getString("role") != null ?
                                doc.getString("role") : "";

                        if ("doctor".equals(currentUserRole)) {
                            String doctorName = doc.getString("username");
                            startCallListener(userId, doctorName);
                        }
                    }
                });
    }

    private void startCallListener(String doctorId, String doctorName) {
        callListener = db.collection("calls")
                .whereEqualTo("doctorId", doctorId)
                .whereEqualTo("status", "calling")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        // Only handle newly added calls
                        if (doc.getMetadata().hasPendingWrites()) continue;

                        String callDocId    = doc.getId();
                        String channelName  = doc.getString("channelName");
                        String callerName   = doc.getString("callerName");

                        // Open incoming call screen
                        Intent intent = new Intent(MainActivity.this,
                                IncomingCallActivity.class);
                        intent.putExtra("CALL_DOC_ID",   callDocId);
                        intent.putExtra("CHANNEL_NAME",  channelName);
                        intent.putExtra("CALLER_NAME",   callerName);
                        intent.putExtra("DOCTOR_NAME",   doctorName);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        break; // Handle one call at a time
                    }
                });
    }

    // ── Doctors Loading ───────────────────────────────────────────────────────

    private void loadDoctors() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("doctors")
                .whereEqualTo("isVerified", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        doctorList.clear();
                        fullDoctorList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // ✅ Hide doctor from their own list
                            if (document.getId().equals(currentUser.getUid())) continue;

                            Doctor doctor = document.toObject(Doctor.class);
                            doctor.setDoctorId(document.getId());
                            doctorList.add(doctor);
                            fullDoctorList.add(doctor);
                        }
                        doctorAdapter.notifyDataSetChanged();

                        if (doctorList.isEmpty()) {
                            Log.d(TAG, "No verified doctors found.");
                        }
                    } else {
                        Log.w(TAG, "Error getting documents.", task.getException());
                        Toast.makeText(this, "Error loading doctors.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── Greeting ──────────────────────────────────────────────────────────────

    private void setGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12)      greetingText.setText("Good morning");
        else if (hour < 17) greetingText.setText("Good afternoon");
        else                greetingText.setText("Good evening");
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void filterDoctors(String text) {
        doctorList.clear();
        if (text.isEmpty()) {
            doctorList.addAll(fullDoctorList);
        } else {
            String query = text.toLowerCase().trim();
            for (Doctor doctor : fullDoctorList) {
                String name = doctor.getName() != null ?
                        doctor.getName().toLowerCase() : "";
                String specialty = doctor.getSpecialty() != null ?
                        doctor.getSpecialty().toLowerCase() : "";
                if (name.contains(query) || specialty.contains(query)) {
                    doctorList.add(doctor);
                }
            }
        }
        doctorAdapter.notifyDataSetChanged();
    }

    // ── User Info ─────────────────────────────────────────────────────────────

    private void loadUserInfo(String userId) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("username");
                        if (name != null) userNameText.setText(name);
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error loading user info.", e));
    }

    // ── Bottom Navigation ─────────────────────────────────────────────────────

    private void setupBottomNavigation() {
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
        bottomNavigationView.setOnItemSelectedListener(
                new NavigationBarView.OnItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        int itemId = item.getItemId();

                        if (itemId == R.id.nav_home) {
                            return true;
                        } else if (itemId == R.id.nav_appointments) {
                            Intent intent = new Intent(MainActivity.this,
                                    AppointmentsActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            return true;
                        } else if (itemId == R.id.nav_messages) {
                            Intent intent = new Intent(MainActivity.this,
                                    MessagesActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            return true;
                        } else if (itemId == R.id.nav_profile) {
                            Intent intent = new Intent(MainActivity.this,
                                    UserProfileActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            return true;
                        }
                        return false;
                    }
                });
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove Firestore listener to prevent memory leaks
        if (callListener != null) {
            callListener.remove();
        }
    }
}