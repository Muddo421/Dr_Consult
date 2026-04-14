package com.example.drconsult;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class TimeSlotAdapter extends RecyclerView.Adapter<TimeSlotAdapter.TimeSlotViewHolder> {

    private List<String> timeSlots;
    private Context context;
    private OnTimeSlotListener onTimeSlotListener;
    private int selectedPosition = -1; // -1 means no selection

    public TimeSlotAdapter(Context context, List<String> timeSlots, OnTimeSlotListener onTimeSlotListener) {
        this.context = context;
        this.timeSlots = timeSlots;
        this.onTimeSlotListener = onTimeSlotListener;
    }

    @NonNull
    @Override
    public TimeSlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_timeslot, parent, false);
        return new TimeSlotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimeSlotViewHolder holder, int position) {
        String timeSlot = timeSlots.get(position);
        holder.timeSlotText.setText(timeSlot);

        // Change appearance if this is the selected item
        if (position == selectedPosition) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.primary_blue));
            holder.timeSlotText.setTextColor(Color.WHITE);
        } else {
            holder.card.setCardBackgroundColor(Color.WHITE);
            holder.timeSlotText.setTextColor(ContextCompat.getColor(context, R.color.primary_blue));
        }

        holder.itemView.setOnClickListener(v -> {
            notifyItemChanged(selectedPosition); // Unselect old
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(selectedPosition); // Select new
            onTimeSlotListener.onTimeSlotClick(timeSlot);
        });
    }

    @Override
    public int getItemCount() {
        return timeSlots.size();
    }

    static class TimeSlotViewHolder extends RecyclerView.ViewHolder {
        TextView timeSlotText;
        MaterialCardView card;

        public TimeSlotViewHolder(@NonNull View itemView) {
            super(itemView);
            timeSlotText = itemView.findViewById(R.id.time_slot_text);
            card = (MaterialCardView) itemView;
        }
    }

    // Interface to communicate back to the Activity
    public interface OnTimeSlotListener {
        void onTimeSlotClick(String timeSlot);
    }
}