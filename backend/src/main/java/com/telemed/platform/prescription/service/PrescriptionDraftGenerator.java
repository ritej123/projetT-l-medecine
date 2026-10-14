package com.telemed.platform.prescription.service;

public interface PrescriptionDraftGenerator {

    String generateDraft(String anonymizedNotes);
}
