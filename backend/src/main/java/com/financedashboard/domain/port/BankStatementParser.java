package com.financedashboard.domain.port;

import com.financedashboard.domain.exception.StatementParseException;
import com.financedashboard.domain.statement.ParsedStatement;

/**
 * Strategy for parsing bank statement documents. One implementation exists per bank/format.
 * Selection is content-based: {@link #canParse} sniffs the document, it is not extension-based.
 */
public interface BankStatementParser {

    /** Returns whether this parser can consume documents of the given media type (e.g. {@code application/pdf}). */
    boolean supports(String mediaType);

    /** Returns whether this parser recognizes the document content (bank signature / layout). */
    boolean canParse(byte[] content);

    /** Parses the document into a {@link ParsedStatement}. */
    ParsedStatement parse(byte[] content) throws StatementParseException;
}
