package com.example.drconsult;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AppointmentsAdapter extends RecyclerView.Adapter<AppointmentsAdapter.ViewHolder> {

    // ✅ Interface for Join Call button click
    public interface OnJoinCallListener {
        void onJoinCall(Appointment appointment);
    }

    private final List<Appointment> appointmentList;
    private Context context;
    private final OnJoinCallListener joinCallListener;

    public AppointmentsAdapter(List<Appointment> appointmentList,
                               OnJoinCallListener joinCallListener) {
        this.appointmentList   = appointmentList;
        this.joinCallListener  = joinCallListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.list_item_appointment_full, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Appointment appt = appointmentList.get(position);

        holder.docName.setText(appt.getDoctorName());
        holder.docSpecialty.setText(appt.getDoctorSpecialty());
        holder.status.setText(appt.getStatus());

        // Format date and time
        if (appt.getAppointmentTime() != null) {
            java.util.Date date = appt.getAppointmentTime().toDate();
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            holder.date.setText(dateFormat.format(date));
            holder.time.setText(timeFormat.format(date));
        }

        // Status styling
        if ("Scheduled".equals(appt.getStatus())) {
            holder.status.setTextColor(Color.parseColor("#2E7D32"));
            holder.status.setBackgroundColor(Color.parseColor("#E8F5E9"));
        } else if ("Cancelled".equals(appt.getStatus())) {
            holder.status.setTextColor(Color.RED);
            holder.status.setBackgroundColor(Color.parseColor("#FFEBEE"));
        }

        // ✅ Show "Join Call" button only for Scheduled appointments
        if ("Scheduled".equals(appt.getStatus()) && holder.btnJoinCall != null) {
            holder.btnJoinCall.setVisibility(View.VISIBLE);
            holder.btnJoinCall.setOnClickListener(v -> {
                if (joinCallListener != null) {
                    joinCallListener.onJoinCall(appt);
                }
            });
        } else if (holder.btnJoinCall != null) {
            holder.btnJoinCall.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return appointmentList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView date, time, status, docName, docSpecialty;
        MaterialButton btnJoinCall;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            date        = itemView.findViewById(R.id.app_date);
            time        = itemView.findViewById(R.id.app_time);
            status      = itemView.findViewById(R.id.app_status);
            docName     = itemView.findViewById(R.id.app_doc_name);
            docSpecialty = itemView.findViewById(R.id.app_doc_specialty);
            btnJoinCall = itemView.findViewById(R.id.btn_join_call);
        }
    }
}