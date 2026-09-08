package com.financedashboard.infrastructure.parser;

import com.financedashboard.domain.exception.StatementParseException;
import com.financedashboard.domain.port.BankStatementParser;
import com.financedashboard.domain.statement.ParsedStatement;
import com.financedashboard.domain.statement.ParsedTransaction;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Parser for Bank Pekao S.A. account statements (Polish, "Wyciąg", PDF).
 *
 * <p>Layout handled: a transactions table with columns {@code Data waluty | Kwota | Opis operacji}.
 * Each transaction appears as one text line beginning with its value date (DD/MM/YYYY) followed
 * by a signed amount in Polish format (comma decimal, dot thousands) and the start of the
 * description; every following line up to the next date line is that transaction's description.
 * Statements state the period in a "Za okres od ... do ..." header and the account currency in
 * the "KONTO PRZEKORZYSTNE ... &lt;ISO&gt;" row.
 *
 * <p>Sample (abbreviated):
 * <pre>
 * Za okres od 27/07/2026 do 25/08/2026
 * KONTO PRZEKORZYSTNE 81 1240 ... 2515 PLN
 * Data waluty Kwota Opis operacji
 * 27/07/2026 -97,06 TRANSAKCJA KARTĄ PŁATNICZĄ
 * EXAMPLE SHOP WROCLAW PL
 * </pre>
 */
@Component
public class PekaoPdfParser implements BankStatementParser {

    private static final String BANK_ID = "PEKAO";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final Pattern PERIOD = Pattern.compile(
            "Za okres od (\\d{2}/\\d{2}/\\d{4}) do (\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern TRANSACTION_LINE = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{4})\\s+(-?[0-9][0-9 .]*,[0-9]{2})\\s*(.*)$");
    private static final Pattern PAGE_FOOTER = Pattern.compile("^Strona\\s+\\d+/\\d+$");

    @Override
    public boolean supports(String mediaType) {
        return mediaType != null && mediaType.equalsIgnoreCase("application/pdf");
    }

    @Override
    public boolean canParse(byte[] content) {
        if (content == null || content.length < 5
                || !isPdf(content)) {
            return false;
        }
        try {
            String firstPage = extractText(content, 1, 1);
            return firstPage.contains("Bank Pekao S.A.")
                    && firstPage.contains("Za okres od")
                    && firstPage.contains("Data waluty");
        } catch (StatementParseException | IOException e) {
            return false;
        }
    }

    @Override
    public ParsedStatement parse(byte[] content) {
        if (!canParse(content)) {
            throw new StatementParseException("Document is not a recognized Bank Pekao statement");
        }
        try {
            String text = extractText(content, 1, Integer.MAX_VALUE);
            return parseText(text);
        } catch (StatementParseException e) {
            throw e;
        } catch (IOException e) {
            throw new StatementParseException("Failed to read PDF content", e);
        }
    }

    private ParsedStatement parseText(String text) {
        Matcher period = PERIOD.matcher(text);
        if (!period.find()) {
            throw new StatementParseException("Statement period header not found");
        }
        LocalDate periodStart = LocalDate.parse(period.group(1), DATE);
        LocalDate periodEnd = LocalDate.parse(period.group(2), DATE);
        String currency = detectCurrency(text);

        List<ParsedTransaction> transactions = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        boolean inTransactions = false;
        Builder current = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isOperationsHeader(trimmed)) {
                inTransactions = true;
                continue;
            }
            if (isSectionBoundary(trimmed)) {
                inTransactions = false;
                continue;
            }
            if (PAGE_FOOTER.matcher(trimmed).matches()) {
                continue;
            }
            if (!inTransactions) {
                continue;
            }

            Matcher tx = TRANSACTION_LINE.matcher(trimmed);
            if (tx.matches()) {
                if (current != null) {
                    transactions.add(current.build());
                }
                LocalDate date = LocalDate.parse(tx.group(1), DATE);
                BigDecimal amount = parseAmount(tx.group(2));
                List<String> descriptionLines = new ArrayList<>();
                if (!tx.group(3).isBlank()) {
                    descriptionLines.add(tx.group(3).trim());
                }
                current = new Builder(date, amount, descriptionLines);
            } else if (current != null) {
                current.addDescriptionLine(trimmed);
            }
        }
        if (current != null) {
            transactions.add(current.build());
        }
        if (transactions.isEmpty()) {
            throw new StatementParseException("No transactions found in statement");
        }
        return new ParsedStatement(BANK_ID, currency, periodStart, periodEnd, transactions);
    }

    private static boolean isOperationsHeader(String trimmed) {
        return trimmed.startsWith("Data waluty") && trimmed.contains("Kwota");
    }

    private static boolean isSectionBoundary(String trimmed) {
        return trimmed.equals("Wyszczególnienie transakcji")
                || trimmed.startsWith("Suma")
                || trimmed.contains("Oprocentowanie")
                || trimmed.startsWith("W rozliczeniach transgranicznych")
                || trimmed.startsWith("Numer IBAN");
    }

    /** Parses a Polish amount, e.g. "-3.600,00" or "44,90". */
    private static BigDecimal parseAmount(String raw) {
        String normalized = raw.trim().replace(" ", "").replace("-", "");
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replace(".", "").replace(",", ".");
        BigDecimal amount = new BigDecimal(normalized).setScale(2, java.math.RoundingMode.HALF_UP);
        return raw.trim().startsWith("-") ? amount.negate() : amount;
    }

    private static String detectCurrency(String text) {
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (!line.contains("PRZEKORZYSTNE")) {
                continue;
            }
            Matcher m = Pattern.compile("\\b([A-Z]{3})\\b").matcher(line);
            String last = null;
            while (m.find()) {
                last = m.group(1);
            }
            if (last != null) {
                return last;
            }
        }
        return "PLN";
    }

    private static boolean isPdf(byte[] content) {
        return content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
    }

    private static String extractText(byte[] content, int fromPage, int toPage) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(fromPage);
            int last = Math.min(toPage, document.getNumberOfPages());
            stripper.setEndPage(last);
            return stripper.getText(document);
        }
    }

    /** Accumulates the description lines of one transaction and derives a best-effort merchant. */
    private static final class Builder {
        private final LocalDate date;
        private final BigDecimal amount;
        private final List<String> descriptionLines;

        Builder(LocalDate date, BigDecimal amount, List<String> descriptionLines) {
            this.date = date;
            this.amount = amount;
            this.descriptionLines = new ArrayList<>(descriptionLines);
        }

        void addDescriptionLine(String line) {
            descriptionLines.add(line);
        }

        ParsedTransaction build() {
            String description = String.join(" ", descriptionLines).trim();
            return new ParsedTransaction(date, amount, description, pickMerchant(descriptionLines));
        }

        private static String pickMerchant(List<String> lines) {
            for (String line : lines) {
                String candidate = line.trim();
                if (isMerchantCandidate(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private static boolean isMerchantCandidate(String candidate) {
            if (candidate.length() < 3 || candidate.matches("[0-9 ./]+")) {
                return false;
            }
            String upper = candidate.toUpperCase(Locale.ROOT);
            if (upper.startsWith("NR REF")
                    || upper.startsWith("BENF ")
                    || upper.startsWith("SACC ")
                    || upper.startsWith("WWW.")
                    || upper.startsWith("KURS ")
                    || upper.startsWith("KWOTA TRANSAKCJI")
                    || upper.startsWith("BLIK REF")
                    || candidate.contains("*********")) {
                return false;
            }
            if (upper.startsWith("TRANSAKCJA KARTĄ PŁATNICZĄ")
                    || upper.startsWith("PRZELEW")
                    || upper.startsWith("PŁATNOŚĆ BLIK")
                    || upper.startsWith("WYPŁATA KARTĄ")
                    || upper.startsWith("REALIZACJA PŁATNOŚCI PEOPAY")
                    || upper.startsWith("ZWROT NIEROZLICZONEJ")
                    || upper.startsWith("WYMIANA WALUT")
                    || upper.startsWith("PRZELEW ŚRODKÓW")) {
                return false;
            }
            if (upper.startsWith("WYKONANEJ DN")
                    || upper.startsWith("NA KARTE")
                    || upper.startsWith("PRZELEW NA TELEFON")
                    || upper.startsWith("ABONAMENT INTERNETOWY")
                    || upper.startsWith("BANK PEKAO S.A.")
                    || upper.startsWith("CENTRALA-")
                    || upper.startsWith("UL.")
                    || upper.startsWith("GWIAŹDZISTA")
                    || upper.startsWith("53-413")
                    || upper.startsWith("01-066")) {
                return false;
            }
            return true;
        }
    }
}
