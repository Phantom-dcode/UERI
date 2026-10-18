package com.example.util

import java.util.Locale
import java.util.regex.Pattern

/**
 * Result of email syntax validation.
 */
sealed class EmailValidationResult {
    data class Success(
        val normalizedEmail: String,
        val localPart: String,
        val domain: String,
        val typoSuggestion: String? = null
    ) : EmailValidationResult()

    data class Failure(
        val errorMessage: String,
        val typoSuggestion: String? = null
    ) : EmailValidationResult()
}

/**
 * Centralized, RFC-compliant email syntax validator for UERI.
 * Enforces strict structural rules across all client screens, authentication flows, and data layers:
 * - Trims surrounding whitespace and rejects empty input
 * - Enforces exactly one '@' delimiter
 * - Enforces maximum length constraints (RFC 5321: total <= 254, local-part <= 64, domain label <= 63)
 * - Prohibits whitespace within the address
 * - Disallows leading, trailing, or consecutive dots in both local and domain parts
 * - Requires syntactically valid domain with valid TLD (>= 2 characters, non-numeric)
 * - Provides generic domain typo suggestions for common mistyped providers without rejecting unfamiliar domains
 * - Normalizes capitalization (lowercase domain)
 */
object EmailValidator {

    const val ERROR_MALFORMED = "Please enter a valid email address."

    // Local-part regex according to RFC 5322 dot-atom format:
    // Alphanumeric and allowable special characters, dot-separated, no consecutive dots, no leading/trailing dot
    private val LOCAL_PART_PATTERN: Pattern = Pattern.compile(
        "^[a-zA-Z0-9!#\$%&'*+/=?^_`{|}~-]+(\\.[a-zA-Z0-9!#\$%&'*+/=?^_`{|}~-]+)*\$"
    )

    // Domain label regex according to RFC 1035:
    // 1-63 chars, alphanumeric with optional interior hyphens, no leading or trailing hyphen
    private val DOMAIN_LABEL_PATTERN: Pattern = Pattern.compile(
        "^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\$"
    )

    // Top-Level Domain (TLD):
    // Must be at least 2 alphabetic characters, or valid internationalized punycode prefix (xn--)
    private val TLD_PATTERN: Pattern = Pattern.compile(
        "^([a-zA-Z]{2,}|xn--[a-zA-Z0-9-]+)\$"
    )

    // Common domain typo dictionary for non-blocking suggestions
    private val DOMAIN_TYPOS = mapOf(
        "gmial.com" to "gmail.com",
        "gamil.com" to "gmail.com",
        "gmai.com" to "gmail.com",
        "gmaill.com" to "gmail.com",
        "gemail.com" to "gmail.com",
        "hotmial.com" to "hotmail.com",
        "hotmaill.com" to "hotmail.com",
        "outlok.com" to "outlook.com",
        "outloo.com" to "outlook.com",
        "yaho.com" to "yahoo.com",
        "yahooo.com" to "yahoo.com",
        "yahu.com" to "yahoo.com",
        "iclod.com" to "icloud.com",
        "protonmail.co" to "protonmail.com"
    )

    /**
     * Validates raw email input string against RFC syntax rules.
     */
    fun validate(rawEmail: String?): EmailValidationResult {
        if (rawEmail == null) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        val trimmed = rawEmail.trim()
        if (trimmed.isEmpty()) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        // Total length check (RFC 5321)
        if (trimmed.length > 254) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        // Must not contain interior whitespace (spaces, tabs, newlines)
        if (trimmed.any { it.isWhitespace() }) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        // Must contain exactly one '@' character
        val atCount = trimmed.count { it == '@' }
        if (atCount != 1) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        val atIndex = trimmed.indexOf('@')
        val localPart = trimmed.substring(0, atIndex)
        val domain = trimmed.substring(atIndex + 1)

        // Local-part checks
        if (localPart.isEmpty() || localPart.length > 64) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        if (localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        if (!LOCAL_PART_PATTERN.matcher(localPart).matches()) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        // Domain checks
        if (domain.isEmpty() || domain.length > 253) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        if (domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        val domainLabels = domain.split('.')
        if (domainLabels.size < 2) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        for (label in domainLabels) {
            if (label.isEmpty() || label.length > 63) {
                return EmailValidationResult.Failure(ERROR_MALFORMED)
            }
            if (!DOMAIN_LABEL_PATTERN.matcher(label).matches()) {
                return EmailValidationResult.Failure(ERROR_MALFORMED)
            }
        }

        // TLD validation (last label)
        val tld = domainLabels.last()
        if (!TLD_PATTERN.matcher(tld).matches()) {
            return EmailValidationResult.Failure(ERROR_MALFORMED)
        }

        val domainLower = domain.lowercase(Locale.ROOT)
        val typoSuggestion = DOMAIN_TYPOS[domainLower]?.let { suggestedDomain ->
            "$localPart@$suggestedDomain"
        }

        val normalizedEmail = "$localPart@$domainLower"
        return EmailValidationResult.Success(
            normalizedEmail = normalizedEmail,
            localPart = localPart,
            domain = domainLower,
            typoSuggestion = typoSuggestion
        )
    }

    /**
     * Returns true if raw email is syntactically valid.
     */
    fun isValid(rawEmail: String?): Boolean {
        return validate(rawEmail) is EmailValidationResult.Success
    }

    /**
     * Normalizes the email (trims and lowercases domain) if valid, or null if invalid.
     */
    fun normalize(rawEmail: String?): String? {
        return (validate(rawEmail) as? EmailValidationResult.Success)?.normalizedEmail
    }

    /**
     * Checks if a domain typo suggestion exists for the given email.
     */
    fun getTypoSuggestion(rawEmail: String?): String? {
        return (validate(rawEmail) as? EmailValidationResult.Success)?.typoSuggestion
    }
}
