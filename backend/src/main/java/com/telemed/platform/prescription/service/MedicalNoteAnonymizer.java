package com.telemed.platform.prescription.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class MedicalNoteAnonymizer {

    private static final String REDACTED = "[IDENTITÉ MASQUÉE]";
    private static final List<Pattern> PERSONAL_DATA_PATTERNS = List.of(
            Pattern.compile("(?iu)\\b(?:patient(?:e)?|nom|prénom|identité|coordonnées)\\s*(?:[:=]|[-–—])\\s*[^,;\\n]+"),
            Pattern.compile("(?iu)\\b(?:né|née|né\\(e\\)|date de naissance|né[e]? le)\\s*(?:le)?\\s*(?:\\d{1,2}[./ -]\\d{1,2}[./ -]\\d{2,4}|\\d{1,2}\\s+(?:janvier|février|fevrier|mars|avril|mai|juin|juillet|août|aout|septembre|octobre|novembre|décembre|decembre)\\s+\\d{4})\\b"),
            Pattern.compile("(?u)\\b(?:M\\.?|Mme\\.?|Mlle\\.?|Monsieur|Madame|Docteur|Dr\\.?)\\s+[\\p{Lu}][\\p{L}'’-]*(?:\\s+[\\p{Lu}][\\p{L}'’-]*){0,2}"),
            Pattern.compile("(?iu)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"),
            Pattern.compile("(?<!\\w)(?:\\+33|0033|0)[ .-]?[1-9](?:[ .-]?\\d{2}){4}(?!\\w)"),
            Pattern.compile("(?<!\\d)(?:\\d[ .-]?){14,18}(?!\\d)"),
            Pattern.compile("(?iu)\\b\\d{1,4}\\s+(?:rue|avenue|boulevard|impasse|route|chemin)\\s+[^,;\\n]+")
    );

    public String anonymize(String notes) {
        String sanitized = notes;
        for (Pattern pattern : PERSONAL_DATA_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll(REDACTED);
        }
        return sanitized;
    }
}
