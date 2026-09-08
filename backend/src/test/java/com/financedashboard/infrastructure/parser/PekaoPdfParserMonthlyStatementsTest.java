package com.financedashboard.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.financedashboard.domain.statement.ParsedStatement;
import com.financedashboard.domain.statement.ParsedTransaction;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Optional real-statement validation. Reads the user's own Bank Pekao statement PDFs from the
 * git-ignored {@code local/} directory (never committed) and checks that the parser reconciles
 * each unique statement (deduped by content) to its own printed "Suma obrotów" totals and detects
 * the right account currency. Skipped when no statements are present, so fresh clones pass.
 */
class PekaoPdfParserMonthlyStatementsTest {

    private static final Set<String> ISO = Set.of(
            "PLN", "EUR", "USD", "GBP", "CHF", "CZK", "SEK", "NOK", "DKK", "HUF", "JPY",
            "CAD", "AUD", "RON", "BGN");

    private final PekaoPdfParser parser = new PekaoPdfParser();

    private static final Pattern SUMMARY = Pattern.compile(
            "obrotów\\s+(-?[0-9][0-9 .]*,[0-9]{2})\\s+([0-9][0-9 .]*,[0-9]{2})");

    @Test
    void reconcilesEveryLocalStatementToItsOwnTotals() throws Exception {
        Path local = Paths.get("..", "local").toAbsolutePath().normalize();
        assumeTrue(Files.isDirectory(local), "local/ statements directory not present");

        List<PdfContent> unique = uniqueByContent(local);
        assumeTrue(!unique.isEmpty(), "no statement PDFs found under local/");
        System.out.println("[statements] validating " + unique.size() + " unique statements");

        for (PdfContent pdf : unique) {
            String name = pdf.path.getFileName().toString();
            assertThat(parser.canParse(pdf.bytes)).as(name).isTrue();
            ParsedStatement statement = parser.parse(pdf.bytes);
            System.out.println("[statements] " + name + " | " + statement.currency()
                    + " | tx=" + statement.transactions().size());

            Matcher summary = SUMMARY.matcher(pdf.text);
            assertThat(summary.find())
                    .as(name + " has a Suma obrotów line")
                    .isTrue();
            BigDecimal expectedDebits = parsePlAmount(summary.group(1));
            BigDecimal expectedCredits = parsePlAmount(summary.group(2));

            // currency detected in the header must appear among ISO codes in that header
            assertThat(headerIsoCodes(pdf.text)).as(name + " header ISO codes").contains(statement.currency());

            BigDecimal debits = BigDecimal.ZERO;
            BigDecimal credits = BigDecimal.ZERO;
            for (ParsedTransaction tx : statement.transactions()) {
                // value dates may sit just outside the stated period bounds
                assertThat(tx.date()).as(name).isAfterOrEqualTo(statement.periodStart().minusDays(7));
                if (tx.amount().signum() < 0) {
                    debits = debits.add(tx.amount());
                } else {
                    credits = credits.add(tx.amount());
                }
            }
            assertThat(statement.transactions()).as(name).isNotEmpty();
            assertThat(debits).as(name + " debits").isEqualByComparingTo(expectedDebits);
            assertThat(credits).as(name + " credits").isEqualByComparingTo(expectedCredits);
        }
    }

    private record PdfContent(Path path, byte[] bytes, String text) {
    }

    private static List<PdfContent> uniqueByContent(Path dir) throws Exception {
        Map<String, PdfContent> byText = new LinkedHashMap<>();
        try (var stream = Files.list(dir)) {
            List<Path> pdfs = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted()
                    .toList();
            for (Path pdf : pdfs) {
                byte[] bytes = Files.readAllBytes(pdf);
                String text = extractText(bytes);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String hash = HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                byText.putIfAbsent(hash, new PdfContent(pdf, bytes, text));
            }
        }
        return new ArrayList<>(byText.values());
    }

    /** ISO codes appearing in the statement header (before the transaction table). */
    private static Set<String> headerIsoCodes(String text) {
        int sectionStart = text.indexOf("Wyszczególnienie transakcji");
        String header = sectionStart >= 0 ? text.substring(0, sectionStart) : text;
        Set<String> found = new TreeSet<>();
        Matcher m = Pattern.compile("\\b([A-Z]{3})\\b").matcher(header);
        while (m.find()) {
            if (ISO.contains(m.group(1))) {
                found.add(m.group(1));
            }
        }
        return found;
    }

    private static BigDecimal parsePlAmount(String raw) {
        String normalized = raw.trim().replace(" ", "").replace("-", "");
        normalized = normalized.replace(".", "").replace(",", ".");
        BigDecimal amount = new BigDecimal(normalized).setScale(2, java.math.RoundingMode.HALF_UP);
        return raw.trim().startsWith("-") ? amount.negate() : amount;
    }

    private static String extractText(byte[] content) throws Exception {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
}
