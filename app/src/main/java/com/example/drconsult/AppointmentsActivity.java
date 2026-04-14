package com.example.drconsult;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class AppointmentsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AppointmentsAdapter adapter;
    private List<Appointment> appointmentList;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Null guard
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginView.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());

        recyclerView = findViewById(R.id.appointments_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        appointmentList = new ArrayList<>();

        // ✅ Pass click listener so tapping "Join Call" opens VideoCallActivity
        adapter = new AppointmentsAdapter(appointmentList, appointment -> {
            Intent intent = new Intent(AppointmentsActivity.this, VideoCallActivity.class);
            intent.putExtra("APPOINTMENT_ID", appointment.getDocumentId());
            intent.putExtra("DOCTOR_NAME",    appointment.getDoctorName());

            // Get current user's display name for Jitsi
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        String name = doc.exists() ? doc.getString("username") : "User";
                        intent.putExtra("DISPLAY_NAME", name != null ? name : "User");
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        intent.putExtra("DISPLAY_NAME", "User");
                        startActivity(intent);
                    });
        });

        recyclerView.setAdapter(adapter);

        loadAppointments(currentUser.getUid());
    }

    private void loadAppointments(String userId) {
        db.collection("appointments")
                .whereEqualTo("userId", userId)
                .orderBy("appointmentTime", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    appointmentList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Appointment appointment = doc.toObject(Appointment.class);
                        if (appointment != null) {
                            appointment.setDocumentId(doc.getId());
                            appointmentList.add(appointment);
                        }
                    }
                    adapter.notifyDataSetChanged();

                    if (appointmentList.isEmpty()) {
                        Toast.makeText(this, "No appointments found", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AppointmentsActivity", "Error loading appointments", e);
                    Toast.makeText(this,
                            "Error loading appointments: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}