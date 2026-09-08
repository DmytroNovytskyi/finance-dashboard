# Adding a Bank / Format Parser

Parsers are format-agnostic: a parser may consume PDF, CSV, XLSX, or anything else. The
registry picks a parser from the document **content**, not its file extension.

## The interface

```java
public interface BankStatementParser {
    boolean supports(MediaType mediaType);   // e.g. application/pdf, text/csv
    boolean canParse(byte[] content);        // content sniffing: bank signature / layout
    ParsedStatement parse(byte[] content);
}
```

- `ParsedStatement` → period (start/end), bank, `List<ParsedTransaction>`.
- `ParsedTransaction` → date, amount, currency, description, merchant, raw fields.

## How selection works

`BankStatementParserRegistry` receives all parsers (Spring injects `List<BankStatementParser>`).
For an uploaded document it (1) narrows by the detected media type, then (2) calls `canParse`
on each candidate (Chain of Responsibility) until one matches; it throws a typed error if none
do. Detection is content-based, not extension-based.

## Runbook

1. Implement `BankStatementParser` in `infrastructure/parser` (one class per bank/format).
   PDF text extraction uses Apache PDFBox `PDFTextStripper`.
2. Declare `supports(MediaType)` for the media types the document can be delivered as.
3. Implement `canParse` — sniff for the bank's signature / distinctive layout.
4. Implement `parse` — build a `ParsedStatement` with `ParsedTransaction` rows. Sign amounts
   expense-negative / income-positive; set `nature` by sign (transfers are never auto-detected).
5. Register the bean so Spring injects it into the registry (auto via component scan).
6. Add a fixture document and a unit test asserting `canParse`, `parse` output, and period.

## Notes

- Javadoc on the parser documents the bank, the exact layout handled, and a sample.
- Idempotency is handled at import time (file SHA-256 + per-transaction dedup hash), not in
  the parser.
- PDFs are just the first format; CSV/XLSX/scanned-image adapters plug into the same
  interface without touching the domain.
