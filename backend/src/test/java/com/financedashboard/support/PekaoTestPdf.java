package com.financedashboard.support;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Builds small synthetic Bank Pekao-like statement PDFs for tests. Contains only fake data so it
 * is safe to commit; real statements never enter the repository (see the optional real-statement
 * test that reads from the ignored {@code local/} directory).
 */
public final class PekaoTestPdf {

    private PekaoTestPdf() {
    }

    /** A synthetic statement with 3 transactions (debits -83,75, credits 1200,50). */
    public static byte[] threeTransactions() {
        return render(List.of(
                "Bank Pekao S.A.",
                "Za okres od 01/03/2026 do 25/03/2026",
                "KONTO PRZEKORZYSTNE 00 0000 0000 0000 0000 0000 0002 PLN",
                "Data waluty Kwota Opis operacji",
                "02/03/2026 -50,00 FAKE MERCHANT WROCLAW PL",
                "CARD PAYMENT",
                "10/03/2026 1200,50 SMART SHOP",
                "PRZELEW",
                "20/03/2026 -33,75 BLIK MERCHANT",
                "BLIK PAYMENT",
                "Suma obrotów -83,75 1200,50"));
    }

    private static byte[] render(List<String> lines) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.setLeading(14f);
                content.newLineAtOffset(50, 760);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build synthetic PDF", e);
        }
    }
}
