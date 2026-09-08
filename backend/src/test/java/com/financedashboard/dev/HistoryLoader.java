package com.financedashboard.dev;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.financedashboard.application.StatementImportService;
import com.financedashboard.application.TransferSuggestionService;
import com.financedashboard.application.TransferSuggestionService.SuggestedTransfer;
import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.infrastructure.parser.PekaoPdfParser;
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dev tool: loads the user's real statement history from {@code local/} into the dev database,
 * auto-creating one account per statement IBAN, then prints detected transfer suggestions.
 * Disabled unless the JVM system property {@code finance.history-loader} is {@code true}
 * (forwarded from Gradle). Never part of normal CI.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "finance.history-loader", matches = "true")
class HistoryLoader {

    @Autowired
    private AccountRepository accounts;
    @Autowired
    private StatementImportService importer;
    @Autowired
    private TransferSuggestionService suggestions;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadAndReport() throws Exception {
        Path local = Paths.get("..", "local").toAbsolutePath().normalize();
        assumeTrue(Files.isDirectory(local), "local/ statements directory not present");

        jdbc.execute("delete from transaction");
        jdbc.execute("delete from bank_statement");
        jdbc.execute("delete from account");
        jdbc.execute("delete from fx_rate");

        PekaoPdfParser parser = new PekaoPdfParser();
        Map<String, Account> accountByIban = new LinkedHashMap<>();
        int importedFiles = 0;
        int importedTx = 0;

        List<byte[]> unique = uniqueContent(local);
        for (byte[] bytes : unique) {
            String text = extractText(bytes);
            String iban = ibanOf(text);
            if (iban == null) {
                System.out.println("[loader] skip: no IBAN in statement");
                continue;
            }
            var parsed = parser.parse(bytes);
            Account account = accountByIban.get(iban);
            if (account == null) {
                account = accounts.save(Account.builder()
                        .name("Pekao …" + last6(iban))
                        .currency(parsed.currency())
                        .accountNumber(iban)
                        .sortOrder(accountByIban.size() + 1)
                        .build());
                accountByIban.put(iban, account);
            }
            StatementImportService.StatementImportResult result =
                    importer.importStatement(bytes, "application/pdf", account.getId(), "history.pdf");
            importedFiles++;
            importedTx += result.imported();
        }

        System.out.println("[loader] accounts:");
        accountByIban.values().forEach(a ->
                System.out.println("[loader]   " + a.getId() + " " + a.getName()
                        + " " + a.getCurrency() + " " + a.getAccountNumber()));
        System.out.println("[loader] imported files=" + importedFiles + " transactions=" + importedTx);

        List<SuggestedTransfer> found = suggestions.suggest();
        System.out.println("[loader] transfer suggestions=" + found.size());
        for (SuggestedTransfer s : found) {
            System.out.println("[loader]   " + s.reason() + " " + s.currency() + " "
                    + s.amount() + "  tx" + s.fromTransactionId() + "@acct" + s.fromAccountId()
                    + "(" + s.fromDate() + ")  <->  tx" + s.toTransactionId()
                    + "@acct" + s.toAccountId() + "(" + s.toDate() + ")");
        }
    }

    private static List<byte[]> uniqueContent(Path dir) throws Exception {
        Map<String, byte[]> byText = new LinkedHashMap<>();
        try (var stream = Files.list(dir)) {
            for (Path p : stream.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".pdf")).sorted().toList()) {
                byte[] bytes = Files.readAllBytes(p);
                String text = extractText(bytes);
                byText.putIfAbsent(sha(text), bytes);
            }
        }
        return new ArrayList<>(byText.values());
    }

    private static String ibanOf(String text) {
        Matcher m = Pattern.compile("Numer IBAN tego rachunku:\\s*([A-Z0-9 ]+)").matcher(text);
        return m.find() ? m.group(1).replaceAll("\\s", "") : null;
    }

    private static String last6(String iban) {
        return iban.substring(iban.length() - 6);
    }

    private static String sha(String s) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(d.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String extractText(byte[] content) throws Exception {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
}
