package com.telemed.platform.prescription.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.telemed.platform.prescription.domain.Prescription;
import com.telemed.platform.common.exception.InvalidRequestException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

@Service
public class PrescriptionPdfService {

    private static final float LEFT = 54;
    private static final float TOP = 750;
    private static final float LINE_HEIGHT = 16;
    private static final float MAX_WIDTH = 500;
    private final PrescriptionSignatureService signatureService;

    public PrescriptionPdfService(PrescriptionSignatureService signatureService) {
        this.signatureService = signatureService;
    }

    public byte[] createSignedPdf(Prescription prescription) {
        String actualContentHash = sha256(prescription.getContent());
        if (!actualContentHash.equals(prescription.getContentSha256())
                || !signatureService.verify(
                        prescription.getId(),
                        prescription.getSignedByUserId(),
                        prescription.getSignedAt().toString(),
                        prescription.getContentSha256(),
                        prescription.getDigitalSignature())) {
            throw new InvalidRequestException("La signature de cette ordonnance ne peut pas être vérifiée.");
        }
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            List<String> lines = new ArrayList<>();
            lines.add("ORDONNANCE MEDICALE — SIGNEE");
            lines.add("");
            lines.add("Rendez-vous : " + prescription.getAppointment().getId());
            lines.add("Date de prescription : " + prescription.getDate());
            lines.add("Medecin signataire (identifiant interne) : " + prescription.getSignedByUserId());
            lines.add("Signe le : " + prescription.getSignedAt());
            lines.add("Empreinte SHA-256 du contenu signe : " + prescription.getContentSha256());
            lines.add("Signature HMAC-SHA256 : " + prescription.getDigitalSignature());
            lines.add("");
            lines.add("Contenu :");
            lines.addAll(wrap(prescription.getContent(), regular, 10));

            PDPageContentStream stream = null;
            float y = TOP;
            try {
                for (String line : lines) {
                    if (stream == null || y < 60) {
                        if (stream != null) {
                            stream.close();
                        }
                        PDPage page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        y = TOP;
                    }
                    stream.beginText();
                    PDType1Font font = line.startsWith("ORDONNANCE") ? bold : regular;
                    stream.setFont(font, line.startsWith("ORDONNANCE") ? 13 : 10);
                    stream.newLineAtOffset(LEFT, y);
                    stream.showText(toWinAnsi(line, font));
                    stream.endText();
                    y -= LINE_HEIGHT;
                }
            } finally {
                if (stream != null) {
                    stream.close();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Le PDF de l'ordonnance n'a pas pu être créé.", exception);
        }
    }

    private List<String> wrap(String text, PDType1Font font, float fontSize) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringWidth(toWinAnsi(candidate, font)) / 1000 * fontSize > MAX_WIDTH && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private String toWinAnsi(String text, PDType1Font font) {
        StringBuilder safe = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
                safe.append(' ');
                return;
            }
            if (Character.isISOControl(codePoint)) {
                return;
            }
            String character = new String(Character.toChars(codePoint));
            try {
                font.encode(character);
                safe.append(character);
            } catch (IOException | IllegalArgumentException exception) {
                safe.append('?');
            }
        });
        return safe.toString();
    }

    private String sha256(String content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
