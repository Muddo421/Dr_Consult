package com.example.drconsult;

import com.google.firebase.Timestamp;

public class Appointment {
    private String documentId; // To store the Firestore ID
    private String userId;
    private String doctorId;
    private String doctorName;
    private String doctorSpecialty;
    private Timestamp appointmentTime;
    private String type;
    private String status;

    // Empty constructor required for Firestore
    public Appointment() {}

    public Appointment(String userId, String doctorId, String doctorName, String doctorSpecialty, Timestamp appointmentTime, String type, String status) {
        this.userId = userId;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.doctorSpecialty = doctorSpecialty;
        this.appointmentTime = appointmentTime;
        this.type = type;
        this.status = status;
    }

    // Getters
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getDoctorName() { return doctorName; }
    public String getDoctorSpecialty() { return doctorSpecialty; }
    public Timestamp getAppointmentTime() { return appointmentTime; }
    public String getType() { return type; }
    public String getStatus() { return status; }
}