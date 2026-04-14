package com.example.drconsult;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class DoctorRegistrationActivity extends AppCompatActivity {

    private TextInputEditText etName, etLicense, etSpecialty, etExperience, etPrice, etAbout;
    private Button submitButton, uploadButton;
    private TextView fileStatusText;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private Uri selectedImageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_registration);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        // Initialize Views
        etName = findViewById(R.id.reg_name);
        etLicense = findViewById(R.id.reg_license_number);
        etSpecialty = findViewById(R.id.reg_specialty);
        etExperience = findViewById(R.id.reg_experience);
        etPrice = findViewById(R.id.reg_price);
        etAbout = findViewById(R.id.reg_about);

        submitButton = findViewById(R.id.submit_profile_button);
        uploadButton = findViewById(R.id.upload_license_button);
        fileStatusText = findViewById(R.id.file_status_text);

        // --- IMAGE PICKER REGISTRATION ---
        ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        fileStatusText.setText("License Image Selected ✅");
                        fileStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                    }
                });

        // --- BUTTON LISTENERS ---
        uploadButton.setOnClickListener(v -> {
            // Open Gallery
            mGetContent.launch("image/*");
        });

        submitButton.setOnClickListener(v -> validateAndSubmit());
    }

    private void validateAndSubmit() {
        String name = etName.getText().toString().trim();
        String license = etLicense.getText().toString().trim();
        String specialty = etSpecialty.getText().toString().trim();
        String experience = etExperience.getText().toString().trim();
        String price = etPrice.getText().toString().trim();
        String about = etAbout.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(specialty) || TextUtils.isEmpty(price) || TextUtils.isEmpty(license)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, "Please upload a photo of your medical license", Toast.LENGTH_SHORT).show();
            return;
        }

        submitButton.setEnabled(false);
        submitButton.setText("Uploading...");

        uploadImageAndSaveProfile(name, license, specialty, experience, price, about);
    }

    private void uploadImageAndSaveProfile(String name, String license, String specialty, String experience, String price, String about) {
        String userId = mAuth.getCurrentUser().getUid();
        // Reference: licenses/USER_ID.jpg
        StorageReference storageRef = storage.getReference().child("licenses/" + userId + ".jpg");

        storageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    // Get URL
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String imageUrl = uri.toString();
                        saveToFirestore(userId, name, license, specialty, experience, price, about, imageUrl);
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DoctorRegistrationActivity.this, "Image Upload Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    submitButton.setEnabled(true);
                    submitButton.setText("Submit Profile");
                });
    }

    private void saveToFirestore(String userId, String name, String license, String specialty, String experience, String price, String about, String licenseImageUrl) {
        Map<String, Object> doctor = new HashMap<>();
        doctor.put("name", name);
        doctor.put("licenseNumber", license);
        doctor.put("licenseImageUrl", licenseImageUrl);
        doctor.put("specialty", specialty);
        doctor.put("experience", experience);
        doctor.put("price", price);
        doctor.put("about", about);
        doctor.put("rating", "5.0");
        doctor.put("patients", "0");
        doctor.put("successRate", "100%");
        doctor.put("reviewCount", 0);
        doctor.put("isVerified", false);

        db.collection("doctors").document(userId)
                .set(doctor)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(DoctorRegistrationActivity.this, "Submitted for Verification!", Toast.LENGTH_LONG).show();
                    // --- CHANGED: Go to MainActivity (instead of Dashboard) ---
                    Intent intent = new Intent(DoctorRegistrationActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DoctorRegistrationActivity.this, "Error saving profile", Toast.LENGTH_SHORT).show();
                    submitButton.setEnabled(true);
                    submitButton.setText("Submit Profile");
                });
    }
}