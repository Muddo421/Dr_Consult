package com.example.drconsult;

public class Doctor {

    private String doctorId;
    private String name;
    private String specialty;
    private String rating;
    private String experience;
    private String price;

    // Default constructor (required for Firestore data mapping, if you ever fetch this as a list)
    public Doctor() {}

    // Constructor used in MainActivity
    public Doctor(String doctorId, String name, String specialty, String rating, String experience, String price) {
        this.doctorId = doctorId;
        this.name = name;
        this.specialty = specialty;
        this.rating = rating;
        this.experience = experience;
        this.price = price;
    }

    // --- NEW SETTER ---
    // This allows us to set the document ID after creating the object from Firestore data
    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }
    // ------------------

    // Getters
    public String getDoctorId() { return doctorId; }
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }
    public String getRating() { return rating; }
    public String getExperience() { return experience; }
    public String getPrice() { return price; }
}