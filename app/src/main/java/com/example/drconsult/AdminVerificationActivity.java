package com.example.drconsult;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AdminVerificationActivity extends AppCompatActivity {

    private static final String TAG = "AdminPanel";

    private RecyclerView recyclerView;
    private PendingDoctorAdapter adapter;
    private List<DocumentSnapshot> pendingDoctors;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        db = FirebaseFirestore.getInstance();

        // Reuses activity_appointments layout (same toolbar + recycler structure)
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle("Pending Doctor Approvals");
            toolbar.setNavigationOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed());
        }

        recyclerView = findViewById(R.id.appointments_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        pendingDoctors = new ArrayList<>();
        adapter = new PendingDoctorAdapter(pendingDoctors);
        recyclerView.setAdapter(adapter);

        loadPendingDoctors();
    }

    private void loadPendingDoctors() {
        db.collection("doctors")
                .whereEqualTo("isVerified", false)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    pendingDoctors.clear();
                    for (DocumentSnapshot doc : querySnapshot) {
                        pendingDoctors.add(doc);
                    }
                    adapter.notifyDataSetChanged();

                    if (pendingDoctors.isEmpty()) {
                        Toast.makeText(this,
                                "No pending verifications 🎉",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading pending doctors", e);
                    Toast.makeText(this, "Error loading data.", Toast.LENGTH_SHORT).show();
                });
    }

    // ── Inner Adapter ──────────────────────────────────────────────────────────

    class PendingDoctorAdapter extends RecyclerView.Adapter<PendingDoctorAdapter.ViewHolder> {

        private final List<DocumentSnapshot> list;

        PendingDoctorAdapter(List<DocumentSnapshot> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pending_doctor, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = list.get(position);

            String name      = doc.getString("name");
            String specialty = doc.getString("specialty");
            String license   = doc.getString("licenseNumber");

            holder.tvName.setText(name != null ? name : "Unknown");
            holder.tvSpecialty.setText(specialty != null ? specialty : "");
            holder.tvLicense.setText("License #: " + (license != null ? license : "N/A"));

            // Approve button
            holder.btnApprove.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_ID) return;

                db.collection("doctors").document(doc.getId())
                        .update("isVerified", true)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(AdminVerificationActivity.this,
                                    (name != null ? name : "Doctor") + " approved ✅",
                                    Toast.LENGTH_SHORT).show();
                            list.remove(pos);
                            notifyItemRemoved(pos);
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(AdminVerificationActivity.this,
                                        "Failed to approve. Try again.",
                                        Toast.LENGTH_SHORT).show());
            });

            // Reject button
            holder.btnReject.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_ID) return;

                db.collection("doctors").document(doc.getId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(AdminVerificationActivity.this,
                                    (name != null ? name : "Doctor") + " rejected ❌",
                                    Toast.LENGTH_SHORT).show();
                            list.remove(pos);
                            notifyItemRemoved(pos);
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(AdminVerificationActivity.this,
                                        "Failed to reject. Try again.",
                                        Toast.LENGTH_SHORT).show());
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvSpecialty, tvLicense;
            Button btnApprove, btnReject;


            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName      = itemView.findViewById(R.id.pending_doctor_name);
                tvSpecialty = itemView.findViewById(R.id.pending_doctor_specialty);
                tvLicense   = itemView.findViewById(R.id.pending_doctor_license);
                btnApprove  = itemView.findViewById(R.id.btn_approve);
                btnReject   = itemView.findViewById(R.id.btn_reject);
            }
        }
    }
}