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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Optional real-statement validation. Reads the user's own Bank Pekao statement PDFs from the
 * git-ignored {@code local/} directory (never committed) and checks that the parser reconciles
 * each one to its own printed "Suma obrotów" debit/credit totals. The test is skipped when no
 * statements are present, so fresh clones still pass.
 */
class PekaoPdfParserMonthlyStatementsTest {

    private final PekaoPdfParser parser = new PekaoPdfParser();

    private static final Pattern SUMMARY = Pattern.compile(
            "obrotów\\s+(-?[0-9][0-9 .]*,[0-9]{2})\\s+([0-9][0-9 .]*,[0-9]{2})");

    @Test
    void reconcilesEveryLocalStatementToItsOwnTotals() throws Exception {
        Path local = Paths.get("..", "local").toAbsolutePath().normalize();
        assumeTrue(Files.isDirectory(local), "local/ statements directory not present");

        List<Path> unique = uniquePdfs(local);
        assumeTrue(!unique.isEmpty(), "no statement PDFs found under local/");

        for (Path pdf : unique) {
            String name = pdf.getFileName().toString();
            byte[] bytes = Files.readAllBytes(pdf);

            assertThat(parser.canParse(bytes)).as(name).isTrue();
            ParsedStatement statement = parser.parse(bytes);

            Matcher summary = SUMMARY.matcher(extractText(bytes));
            assertThat(summary.find())
                    .as(name + " has a Suma obrotów line")
                    .isTrue();
            BigDecimal expectedDebits = parsePlAmount(summary.group(1));
            BigDecimal expectedCredits = parsePlAmount(summary.group(2));

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

    private static List<Path> uniquePdfs(Path dir) throws Exception {
        Map<String, Path> byHash = new LinkedHashMap<>();
        try (var stream = Files.list(dir)) {
            List<Path> pdfs = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted()
                    .toList();
            for (Path pdf : pdfs) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String hash = HexFormat.of().formatHex(digest.digest(Files.readAllBytes(pdf)));
                byHash.putIfAbsent(hash, pdf);
            }
        }
        return new ArrayList<>(byHash.values());
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
