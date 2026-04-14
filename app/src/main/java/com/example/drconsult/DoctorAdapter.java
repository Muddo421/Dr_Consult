package com.example.drconsult; // Replace with your package name

import android.content.Context; // <-- Import
import android.content.Intent; // <-- Import
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DoctorAdapter extends RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder> {

    private List<Doctor> doctorList;
    private Context context; // <-- Add Context

    public DoctorAdapter(List<Doctor> doctorList) {
        this.doctorList = doctorList;
    }

    @NonNull
    @Override
    public DoctorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext(); // <-- Initialize Context
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_doctor, parent, false);
        return new DoctorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DoctorViewHolder holder, int position) {
        Doctor doctor = doctorList.get(position);
        holder.name.setText(doctor.getName());
        holder.specialty.setText(doctor.getSpecialty());
        holder.rating.setText(doctor.getRating());
        holder.experience.setText(doctor.getExperience());
        holder.price.setText(doctor.getPrice());

        // --- NEW CLICK LISTENER ---
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, DoctorProfileActivity.class);
            // Pass the unique ID of the clicked doctor to the new activity
            intent.putExtra("DOCTOR_ID", doctor.getDoctorId());
            context.startActivity(intent);
        });
        // --------------------------
    }

    @Override
    public int getItemCount() {
        return doctorList.size();
    }

    static class DoctorViewHolder extends RecyclerView.ViewHolder {
        TextView name, specialty, rating, experience, price;

        public DoctorViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.doctor_name);
            specialty = itemView.findViewById(R.id.doctor_specialty);
            rating = itemView.findViewById(R.id.doctor_rating);
            experience = itemView.findViewById(R.id.doctor_experience);
            price = itemView.findViewById(R.id.doctor_price);
        }
    }
}

