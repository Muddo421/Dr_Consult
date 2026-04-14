package com.example.drconsult;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BookAppointmentActivity extends AppCompatActivity implements TimeSlotAdapter.OnTimeSlotListener {

    private static final String TAG = "BookAppointment";

    // Maps Calendar.DAY_OF_WEEK → Firestore document name
    private static final Map<Integer, String> DAY_MAP = new HashMap<Integer, String>() {{
        put(Calendar.MONDAY,    "monday");
        put(Calendar.TUESDAY,   "tuesday");
        put(Calendar.WEDNESDAY, "wednesday");
        put(Calendar.THURSDAY,  "thursday");
        put(Calendar.FRIDAY,    "friday");
        put(Calendar.SATURDAY,  "saturday");
        put(Calendar.SUNDAY,    "sunday");
    }};

    private String doctorId, doctorName, doctorSpecialty;
    private String selectedTimeSlot = null;
    private Calendar selectedDate = Calendar.getInstance();

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private TextView tvDoctorName, tvDoctorSpecialty, tvNoSlots;
    private CalendarView calendarView;
    private RecyclerView timeSlotRecyclerView;
    private TimeSlotAdapter timeSlotAdapter;
    private Button confirmBookingButton;
    private MaterialToolbar toolbar;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_appointment);

        doctorId        = getIntent().getStringExtra("DOCTOR_ID");
        doctorName      = getIntent().getStringExtra("DOCTOR_NAME");
        doctorSpecialty = getIntent().getStringExtra("DOCTOR_SPECIALTY");

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        toolbar              = findViewById(R.id.toolbar);
        tvDoctorName         = findViewById(R.id.book_doctor_name);
        tvDoctorSpecialty    = findViewById(R.id.book_doctor_specialty);
        calendarView         = findViewById(R.id.calendar_view);
        timeSlotRecyclerView = findViewById(R.id.time_slot_recycler_view);
        confirmBookingButton = findViewById(R.id.confirm_booking_button);
        progressBar          = findViewById(R.id.booking_progress_bar);
        tvNoSlots            = findViewById(R.id.tv_no_slots);

        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        tvDoctorName.setText(doctorName);
        tvDoctorSpecialty.setText(doctorSpecialty);

        // Prevent selecting past dates
        calendarView.setMinDate(System.currentTimeMillis() - 1000);

        timeSlotRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate.set(year, month, dayOfMonth);
            selectedTimeSlot = null;
            loadSlotsForSelectedDate();
        });

        // Load today on open
        loadSlotsForSelectedDate();

        confirmBookingButton.setOnClickListener(v -> {
            if (selectedTimeSlot == null) {
                Toast.makeText(this, "Please select a time slot", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mAuth.getCurrentUser() == null) {
                Toast.makeText(this, "You must be logged in", Toast.LENGTH_SHORT).show();
                return;
            }
            saveAppointmentToFirestore();
        });
    }

    /**
     * 1. Determine which weekday the selected date falls on
     * 2. Fetch the doctor's schedule for that weekday from Firestore
     * 3. Generate 30-min slots between start and end time
     * 4. Remove already-booked slots by querying appointments
     * 5. Display what remains
     */
    private void loadSlotsForSelectedDate() {
        showLoading(true);

        int dayOfWeek = selectedDate.get(Calendar.DAY_OF_WEEK);
        String dayName = DAY_MAP.get(dayOfWeek);

        if (dayName == null) {
            showNoSlots("Unable to determine day.");
            return;
        }

        db.collection("doctors")
                .document(doctorId)
                .collection("weeklySchedule")
                .document(dayName)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        showNoSlots("This doctor has not set their availability yet.");
                        return;
                    }

                    Boolean isWorking = doc.getBoolean("isWorking");
                    if (isWorking == null || !isWorking) {
                        showNoSlots("Doctor does not work on " + capitalize(dayName) + "s.");
                        return;
                    }

                    int startHour    = safeInt(doc, "startHour", 9);
                    int startMinute  = safeInt(doc, "startMinute", 0);
                    int endHour      = safeInt(doc, "endHour", 17);
                    int endMinute    = safeInt(doc, "endMinute", 0);
                    int slotDuration = safeInt(doc, "slotDurationMinutes", 30);

                    List<String> allSlots = generateSlots(startHour, startMinute, endHour, endMinute, slotDuration);

                    if (allSlots.isEmpty()) {
                        showNoSlots("No slots available for this day.");
                        return;
                    }

                    filterBookedSlots(allSlots);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching schedule", e);
                    showNoSlots("Could not load schedule. Check your connection.");
                });
    }

    /**
     * Generates time slot strings every [durationMins] minutes
     * between startH:startM and endH:endM.
     *
     * e.g. 9:00 → 11:00, 30 min → ["09:00 AM", "09:30 AM", "10:00 AM", "10:30 AM"]
     */
    private List<String> generateSlots(int startH, int startM, int endH, int endM, int durationMins) {
        List<String> slots = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        Calendar slot = Calendar.getInstance();
        slot.set(Calendar.HOUR_OF_DAY, startH);
        slot.set(Calendar.MINUTE, startM);
        slot.set(Calendar.SECOND, 0);

        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, endH);
        end.set(Calendar.MINUTE, endM);

        while (slot.before(end)) {
            slots.add(sdf.format(slot.getTime()).toUpperCase(Locale.getDefault()));
            slot.add(Calendar.MINUTE, durationMins);
        }

        return slots;
    }

    /**
     * Queries appointments collection for this doctor on the selected date
     * and removes already-booked slots from the list.
     */
    private void filterBookedSlots(List<String> allSlots) {
        Calendar dayStart = (Calendar) selectedDate.clone();
        dayStart.set(Calendar.HOUR_OF_DAY, 0);
        dayStart.set(Calendar.MINUTE, 0);
        dayStart.set(Calendar.SECOND, 0);

        Calendar dayEnd = (Calendar) selectedDate.clone();
        dayEnd.set(Calendar.HOUR_OF_DAY, 23);
        dayEnd.set(Calendar.MINUTE, 59);
        dayEnd.set(Calendar.SECOND, 59);

        Timestamp tsStart = new Timestamp(dayStart.getTime());
        Timestamp tsEnd   = new Timestamp(dayEnd.getTime());

        db.collection("appointments")
                .whereEqualTo("doctorId", doctorId)
                .whereGreaterThanOrEqualTo("appointmentTime", tsStart)
                .whereLessThanOrEqualTo("appointmentTime", tsEnd)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> bookedSlots = new ArrayList<>();
                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());

                    for (DocumentSnapshot doc : querySnapshot) {
                        Timestamp ts = doc.getTimestamp("appointmentTime");
                        if (ts != null) {
                            bookedSlots.add(sdf.format(ts.toDate()).toUpperCase(Locale.getDefault()));
                        }
                    }

                    List<String> available = new ArrayList<>();
                    for (String slot : allSlots) {
                        if (!bookedSlots.contains(slot)) available.add(slot);
                    }

                    showLoading(false);
                    if (available.isEmpty()) {
                        showNoSlots("All slots are booked for this day.\nPlease choose another date.");
                    } else {
                        showSlots(available);
                    }
                })
                .addOnFailureListener(e -> {
                    // Non-critical — still show all slots
                    Log.w(TAG, "Could not check booked slots", e);
                    showLoading(false);
                    showSlots(allSlots);
                });
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void showLoading(boolean loading) {
        if (progressBar != null)
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            timeSlotRecyclerView.setVisibility(View.GONE);
            if (tvNoSlots != null) tvNoSlots.setVisibility(View.GONE);
        }
    }

    private void showNoSlots(String message) {
        showLoading(false);
        timeSlotRecyclerView.setVisibility(View.GONE);
        if (tvNoSlots != null) {
            tvNoSlots.setText(message);
            tvNoSlots.setVisibility(View.VISIBLE);
        }
    }

    private void showSlots(List<String> slots) {
        if (tvNoSlots != null) tvNoSlots.setVisibility(View.GONE);
        timeSlotRecyclerView.setVisibility(View.VISIBLE);
        timeSlotAdapter = new TimeSlotAdapter(this, slots, this);
        timeSlotRecyclerView.setAdapter(timeSlotAdapter);
    }

    @Override
    public void onTimeSlotClick(String timeSlot) {
        this.selectedTimeSlot = timeSlot;
    }

    // ── Save Appointment ──────────────────────────────────────────────────────

    private void saveAppointmentToFirestore() {
        String userId = mAuth.getCurrentUser().getUid();

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            Calendar parsed = Calendar.getInstance();
            parsed.setTime(sdf.parse(selectedTimeSlot));

            selectedDate.set(Calendar.HOUR_OF_DAY, parsed.get(Calendar.HOUR_OF_DAY));
            selectedDate.set(Calendar.MINUTE, parsed.get(Calendar.MINUTE));
            selectedDate.set(Calendar.SECOND, 0);
            selectedDate.set(Calendar.MILLISECOND, 0);
        } catch (Exception e) {
            Toast.makeText(this, "Invalid time format.", Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp appointmentTimestamp = new Timestamp(selectedDate.getTime());

        Map<String, Object> appointment = new HashMap<>();
        appointment.put("userId",          userId);
        appointment.put("doctorId",        doctorId);
        appointment.put("doctorName",      doctorName);
        appointment.put("doctorSpecialty", doctorSpecialty);
        appointment.put("appointmentTime", appointmentTimestamp);
        appointment.put("type",            "Video Consultation");
        appointment.put("status",          "Scheduled");
        appointment.put("createdAt",       Timestamp.now());

        confirmBookingButton.setEnabled(false);
        confirmBookingButton.setText("Booking...");

        db.collection("appointments")
                .add(appointment)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "Appointment saved: " + ref.getId());
                    Toast.makeText(this, "Booking Confirmed! ✅", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error saving appointment", e);
                    Toast.makeText(this, "Booking failed. Try again.", Toast.LENGTH_LONG).show();
                    confirmBookingButton.setEnabled(true);
                    confirmBookingButton.setText("Confirm Booking");
                });
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private int safeInt(DocumentSnapshot doc, String field, int def) {
        Long val = doc.getLong(field);
        return val != null ? val.intValue() : def;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}