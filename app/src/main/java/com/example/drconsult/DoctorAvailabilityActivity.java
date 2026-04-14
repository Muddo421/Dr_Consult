package com.example.drconsult;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * DoctorAvailabilityActivity
 *
 * Lets a doctor define their weekly working hours.
 * They toggle which days they work and set start/end times per day.
 *
 * Saved to Firestore under:
 *   doctors/{doctorId}/weeklySchedule/{dayName}
 *     → isWorking: true/false
 *     → startHour: 9
 *     → startMinute: 0
 *     → endHour: 17
 *     → endMinute: 0
 *     → slotDurationMinutes: 30
 *
 * ── ADD TO MANIFEST ──
 * <activity android:name=".DoctorAvailabilityActivity" android:exported="false" />
 *
 * ── HOW TO OPEN THIS SCREEN ──
 * Add a button in UserProfileActivity for doctors:
 *   startActivity(new Intent(this, DoctorAvailabilityActivity.class));
 */
public class DoctorAvailabilityActivity extends AppCompatActivity {

    private static final String TAG = "DoctorAvailability";

    private FirebaseFirestore db;
    private String doctorId;

    // Day checkboxes
    private CheckBox cbMonday, cbTuesday, cbWednesday, cbThursday, cbFriday, cbSaturday, cbSunday;

    // Start time pickers per day
    private TimePicker tpMondayStart, tpTuesdayStart, tpWednesdayStart, tpThursdayStart,
            tpFridayStart, tpSaturdayStart, tpSundayStart;

    // End time pickers per day
    private TimePicker tpMondayEnd, tpTuesdayEnd, tpWednesdayEnd, tpThursdayEnd,
            tpFridayEnd, tpSaturdayEnd, tpSundayEnd;

    // Day containers (shown/hidden based on checkbox)
    private View layoutMonday, layoutTuesday, layoutWednesday, layoutThursday,
            layoutFriday, layoutSaturday, layoutSunday;

    private MaterialButton btnSave;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_availability);

        db = FirebaseFirestore.getInstance();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        doctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        MaterialToolbar toolbar = findViewById(R.id.toolbar_availability);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        bindViews();
        setupDayToggles();
        loadExistingSchedule();

        btnSave.setOnClickListener(v -> saveSchedule());
    }

    private void bindViews() {
        // Checkboxes
        cbMonday    = findViewById(R.id.cb_monday);
        cbTuesday   = findViewById(R.id.cb_tuesday);
        cbWednesday = findViewById(R.id.cb_wednesday);
        cbThursday  = findViewById(R.id.cb_thursday);
        cbFriday    = findViewById(R.id.cb_friday);
        cbSaturday  = findViewById(R.id.cb_saturday);
        cbSunday    = findViewById(R.id.cb_sunday);

        // Start pickers
        tpMondayStart    = findViewById(R.id.tp_monday_start);
        tpTuesdayStart   = findViewById(R.id.tp_tuesday_start);
        tpWednesdayStart = findViewById(R.id.tp_wednesday_start);
        tpThursdayStart  = findViewById(R.id.tp_thursday_start);
        tpFridayStart    = findViewById(R.id.tp_friday_start);
        tpSaturdayStart  = findViewById(R.id.tp_saturday_start);
        tpSundayStart    = findViewById(R.id.tp_sunday_start);

        // End pickers
        tpMondayEnd    = findViewById(R.id.tp_monday_end);
        tpTuesdayEnd   = findViewById(R.id.tp_tuesday_end);
        tpWednesdayEnd = findViewById(R.id.tp_wednesday_end);
        tpThursdayEnd  = findViewById(R.id.tp_thursday_end);
        tpFridayEnd    = findViewById(R.id.tp_friday_end);
        tpSaturdayEnd  = findViewById(R.id.tp_saturday_end);
        tpSundayEnd    = findViewById(R.id.tp_sunday_end);

        // Day layout containers
        layoutMonday    = findViewById(R.id.layout_monday);
        layoutTuesday   = findViewById(R.id.layout_tuesday);
        layoutWednesday = findViewById(R.id.layout_wednesday);
        layoutThursday  = findViewById(R.id.layout_thursday);
        layoutFriday    = findViewById(R.id.layout_friday);
        layoutSaturday  = findViewById(R.id.layout_saturday);
        layoutSunday    = findViewById(R.id.layout_sunday);

        btnSave     = findViewById(R.id.btn_save_availability);
        progressBar = findViewById(R.id.progress_availability);

        // Default times: 9 AM start, 5 PM end
        setDefaultTime(tpMondayStart, 9, 0);    setDefaultTime(tpMondayEnd, 17, 0);
        setDefaultTime(tpTuesdayStart, 9, 0);   setDefaultTime(tpTuesdayEnd, 17, 0);
        setDefaultTime(tpWednesdayStart, 9, 0); setDefaultTime(tpWednesdayEnd, 17, 0);
        setDefaultTime(tpThursdayStart, 9, 0);  setDefaultTime(tpThursdayEnd, 17, 0);
        setDefaultTime(tpFridayStart, 9, 0);    setDefaultTime(tpFridayEnd, 17, 0);
        setDefaultTime(tpSaturdayStart, 9, 0);  setDefaultTime(tpSaturdayEnd, 13, 0);
        setDefaultTime(tpSundayStart, 9, 0);    setDefaultTime(tpSundayEnd, 13, 0);

        // Set all pickers to 24h mode
        setAllPickers24h();
    }

    private void setDefaultTime(TimePicker tp, int hour, int minute) {
        tp.setHour(hour);
        tp.setMinute(minute);
    }

    private void setAllPickers24h() {
        TimePicker[] pickers = {
                tpMondayStart, tpMondayEnd, tpTuesdayStart, tpTuesdayEnd,
                tpWednesdayStart, tpWednesdayEnd, tpThursdayStart, tpThursdayEnd,
                tpFridayStart, tpFridayEnd, tpSaturdayStart, tpSaturdayEnd,
                tpSundayStart, tpSundayEnd
        };
        for (TimePicker tp : pickers) {
            tp.setIs24HourView(false); // Use AM/PM for user clarity
        }
    }

    /** Show/hide the time picker rows based on whether the day is checked */
    private void setupDayToggles() {
        setupToggle(cbMonday, layoutMonday);
        setupToggle(cbTuesday, layoutTuesday);
        setupToggle(cbWednesday, layoutWednesday);
        setupToggle(cbThursday, layoutThursday);
        setupToggle(cbFriday, layoutFriday);
        setupToggle(cbSaturday, layoutSaturday);
        setupToggle(cbSunday, layoutSunday);

        // Default: Mon-Fri checked, Sat-Sun unchecked
        cbMonday.setChecked(true);    layoutMonday.setVisibility(View.VISIBLE);
        cbTuesday.setChecked(true);   layoutTuesday.setVisibility(View.VISIBLE);
        cbWednesday.setChecked(true); layoutWednesday.setVisibility(View.VISIBLE);
        cbThursday.setChecked(true);  layoutThursday.setVisibility(View.VISIBLE);
        cbFriday.setChecked(true);    layoutFriday.setVisibility(View.VISIBLE);
        cbSaturday.setChecked(false); layoutSaturday.setVisibility(View.GONE);
        cbSunday.setChecked(false);   layoutSunday.setVisibility(View.GONE);
    }

    private void setupToggle(CheckBox cb, View layout) {
        cb.setOnCheckedChangeListener((buttonView, isChecked) ->
                layout.setVisibility(isChecked ? View.VISIBLE : View.GONE));
    }

    /** Load existing schedule from Firestore and populate the UI */
    private void loadExistingSchedule() {
        db.collection("doctors").document(doctorId)
                .collection("weeklySchedule")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (var doc : querySnapshot) {
                        String day = doc.getId();
                        boolean isWorking = Boolean.TRUE.equals(doc.getBoolean("isWorking"));
                        Long startHour   = doc.getLong("startHour");
                        Long startMinute = doc.getLong("startMinute");
                        Long endHour     = doc.getLong("endHour");
                        Long endMinute   = doc.getLong("endMinute");

                        applyScheduleToUI(day, isWorking,
                                startHour != null ? startHour.intValue() : 9,
                                startMinute != null ? startMinute.intValue() : 0,
                                endHour != null ? endHour.intValue() : 17,
                                endMinute != null ? endMinute.intValue() : 0);
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Could not load schedule", e));
    }

    private void applyScheduleToUI(String day, boolean isWorking,
                                   int startH, int startM, int endH, int endM) {
        switch (day) {
            case "monday":
                cbMonday.setChecked(isWorking);
                layoutMonday.setVisibility(isWorking ? View.VISIBLE : View.GONE);
                if (isWorking) { tpMondayStart.setHour(startH); tpMondayStart.setMinute(startM); tpMondayEnd.setHour(endH); tpMondayEnd.setMinute(endM); }
                break;
            case "tuesday":
                cbTuesday.setChecked(isWorking);
                layoutTuesday.setVisibility(isWorking ? View.VISIBLE : View.GONE);
                if (isWorking) { tpTuesdayStart.setHour(startH); tpTuesdayStart.setMinute(startM); tpTuesdayEnd.setHour(endH); tpTuesdayEnd.setMinute(endM); }
                break;
            case "wednesday":
                cbWednesday.setChecked(isWorking);
                layoutWednesday.setVisibility(isWorking ? View.VISIBLE : View.GONE);
                if (isWorking) { tpWednesdayStart.setHour(startH); tpWednesdayStart.setMinute(startM); tpWednesdayEnd.setHour(endH); tpWednesdayEnd.setMinute(endM); }
                break;
            case "thursday":
                cbThursday.setChecked(isWorking);
                layoutThursday.setVisibility(isWorking ? View.VISIBLE : View.GONE);
                if (isWorking) { tpThursdayStart.setHour(startH); tpThursdayStart.setMinute(startM); tpThursdayEnd.setHour(endH); tpThursdayEnd.setMinute(endM); }
                break;
            case "friday":
                cbFriday.setChecked(isWorking);
                layoutFriday.setVisibility(isWorking ? View.VISIBLE : View.GONE);
                if (isWorking) { tpFridayStart.setHour(startH); tpFridayStart.setMinute(startM); tpFridayEnd.setHour(endH); tpFridayEnd.setMinute(endM); }
                break;
            case "saturday":
                cbSaturday.setChecked(isWorking);
                layoutSaturday.setVisibility(isWorking ? View.VISIBLE : View.GONE);
                if (isWorking) { tpSaturdayStart.setHour(startH); tpSaturdayStart.setMinute(startM); tpSaturdayEnd.setHour(endH); tpSaturdayEnd.setMinute(endM); }
                break;
            case "sunday":
                cbSunday.setChecked(isWorking);
                layoutSunday.setVisibility(isWorking ? View.VISIBLE : View.GONE);
                if (isWorking) { tpSundayStart.setHour(startH); tpSundayStart.setMinute(startM); tpSundayEnd.setHour(endH); tpSundayEnd.setMinute(endM); }
                break;
        }
    }

    /** Save the entire weekly schedule to Firestore */
    private void saveSchedule() {
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
        CheckBox[] checkboxes = {cbMonday, cbTuesday, cbWednesday, cbThursday, cbFriday, cbSaturday, cbSunday};
        TimePicker[] starts = {tpMondayStart, tpTuesdayStart, tpWednesdayStart, tpThursdayStart, tpFridayStart, tpSaturdayStart, tpSundayStart};
        TimePicker[] ends   = {tpMondayEnd,   tpTuesdayEnd,   tpWednesdayEnd,   tpThursdayEnd,   tpFridayEnd,   tpSaturdayEnd,   tpSundayEnd};

        int[] saved = {0};
        int total = days.length;

        for (int i = 0; i < days.length; i++) {
            boolean isWorking = checkboxes[i].isChecked();

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("isWorking", isWorking);
            dayData.put("startHour",   isWorking ? starts[i].getHour()   : 9);
            dayData.put("startMinute", isWorking ? starts[i].getMinute() : 0);
            dayData.put("endHour",     isWorking ? ends[i].getHour()     : 17);
            dayData.put("endMinute",   isWorking ? ends[i].getMinute()   : 0);
            dayData.put("slotDurationMinutes", 30); // 30-minute slots

            db.collection("doctors").document(doctorId)
                    .collection("weeklySchedule")
                    .document(days[i])
                    .set(dayData)
                    .addOnSuccessListener(aVoid -> {
                        saved[0]++;
                        if (saved[0] == total) {
                            progressBar.setVisibility(View.GONE);
                            btnSave.setEnabled(true);
                            Toast.makeText(this, "Availability saved! ✅", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        btnSave.setEnabled(true);
                        Log.e(TAG, "Error saving schedule", e);
                        Toast.makeText(this, "Error saving. Try again.", Toast.LENGTH_SHORT).show();
                    });
        }
    }
}