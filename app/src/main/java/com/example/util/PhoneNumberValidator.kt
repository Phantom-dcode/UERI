package com.example.util

import android.util.Log
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import java.util.Locale

/**
 * Country metadata for the international country calling code selector.
 */
data class CountryInfo(
    val isoCode: String,           // e.g. "IN", "US", "GB"
    val callingCode: String,       // e.g. "+91", "+1", "+44"
    val countryName: String,       // e.g. "India", "United States"
    val flagEmoji: String,         // e.g. "🇮🇳", "🇺🇸"
    val exampleNational: String    // e.g. "98765 43210"
)

/**
 * Result of phone number validation using international numbering metadata.
 */
sealed class PhoneValidationResult {
    data class Success(
        val countryIsoCode: String,
        val countryCallingCode: String,
        val nationalPhoneNumber: String,
        val normalizedPhoneNumber: String, // International E.164 format, e.g. "+919876543210"
        val formattedNational: String
    ) : PhoneValidationResult()

    data class Failure(
        val errorMessage: String,
        val errorCode: String = "INVALID_PHONE"
    ) : PhoneValidationResult()
}

/**
 * Production-grade international phone number parser, formatter, and validator
 * powered by Google's libphonenumber metadata library.
 *
 * Enforces strict country-specific validation rules:
 * - Reject arbitrary digits or incomplete numbers
 * - Validate length and prefixes strictly according to selected country
 * - Normalize into E.164 format (+[countryCode][nationalNumber])
 * - Support international dialing codes dynamically
 */
object PhoneNumberValidator {
    private const val TAG = "PhoneNumberValidator"

    private val phoneUtil: PhoneNumberUtil by lazy {
        PhoneNumberUtil.getInstance()
    }

    /**
     * Converts a 2-letter ISO country code into Unicode flag emoji.
     * e.g. "IN" -> 🇮🇳, "US" -> 🇺🇸, "GB" -> 🇬🇧
     */
    fun getFlagEmoji(countryIso: String): String {
        val trimmed = countryIso.trim().uppercase(Locale.ROOT)
        if (trimmed.length != 2) return "🌐"
        val firstChar = trimmed[0]
        val secondChar = trimmed[1]
        if (firstChar !in 'A'..'Z' || secondChar !in 'A'..'Z') return "🌐"
        val firstCodePoint = 0x1F1E6 + (firstChar - 'A')
        val secondCodePoint = 0x1F1E6 + (secondChar - 'A')
        return String(Character.toChars(firstCodePoint)) + String(Character.toChars(secondCodePoint))
    }

    /**
     * Returns a curated list of supported countries sorted with common countries first,
     * followed alphabetically by name.
     */
    fun getSupportedCountries(): List<CountryInfo> {
        val priorityIsoList = listOf("IN", "US", "GB", "CA", "AU", "DE", "FR", "JP", "SG", "AE", "SA", "BR", "ZA", "MX", "IT", "ES", "NL", "NZ", "PH", "MY")
        val allRegions = phoneUtil.supportedRegions

        val list = mutableListOf<CountryInfo>()
        for (region in allRegions) {
            val callingCodeInt = phoneUtil.getCountryCodeForRegion(region)
            if (callingCodeInt <= 0) continue
            val callingCode = "+$callingCodeInt"
            val countryName = Locale(Locale.getDefault().language, region).getDisplayCountry(Locale.ENGLISH).ifBlank { region }
            val flagEmoji = getFlagEmoji(region)

            val exampleNational = try {
                val example = phoneUtil.getExampleNumberForType(region, PhoneNumberType.MOBILE)
                    ?: phoneUtil.getExampleNumber(region)
                if (example != null) {
                    phoneUtil.format(example, PhoneNumberFormat.NATIONAL)
                } else {
                    "123456789"
                }
            } catch (_: Exception) {
                "123456789"
            }

            list.add(
                CountryInfo(
                    isoCode = region,
                    callingCode = callingCode,
                    countryName = countryName,
                    flagEmoji = flagEmoji,
                    exampleNational = exampleNational
                )
            )
        }

        // Sort: priority countries first, then alphabetically by country name
        return list.sortedWith { a, b ->
            val indexA = priorityIsoList.indexOf(a.isoCode)
            val indexB = priorityIsoList.indexOf(b.isoCode)
            when {
                indexA != -1 && indexB != -1 -> indexA.compareTo(indexB)
                indexA != -1 -> -1
                indexB != -1 -> 1
                else -> a.countryName.compareTo(b.countryName, ignoreCase = true)
            }
        }
    }

    /**
     * Default selected country: India (+91) or device default if available.
     */
    fun getDefaultCountry(): CountryInfo {
        val userRegion = try {
            Locale.getDefault().country.uppercase(Locale.ROOT)
        } catch (_: Exception) {
            "IN"
        }
        val targetRegion = if (userRegion.isNotBlank() && phoneUtil.supportedRegions.contains(userRegion)) {
            userRegion
        } else {
            "IN"
        }
        val callingCodeInt = phoneUtil.getCountryCodeForRegion(targetRegion)
        val example = try {
            val ex = phoneUtil.getExampleNumberForType(targetRegion, PhoneNumberType.MOBILE)
            if (ex != null) phoneUtil.format(ex, PhoneNumberFormat.NATIONAL) else "98765 43210"
        } catch (_: Exception) {
            "98765 43210"
        }

        return CountryInfo(
            isoCode = targetRegion,
            callingCode = "+$callingCodeInt",
            countryName = Locale(Locale.getDefault().language, targetRegion).getDisplayCountry(Locale.ENGLISH).ifBlank { "India" },
            flagEmoji = getFlagEmoji(targetRegion),
            exampleNational = example
        )
    }

    /**
     * Strictly validates a user-provided national phone number against the selected country's
     * international numbering metadata.
     *
     * @param countryIso 2-letter uppercase ISO code (e.g. "IN", "US")
     * @param rawNationalInput The national phone number entered by the user
     */
    fun validatePhoneNumber(countryIso: String, rawNationalInput: String): PhoneValidationResult {
        val normalizedCountryIso = countryIso.trim().uppercase(Locale.ROOT)

        if (normalizedCountryIso.isBlank() || !phoneUtil.supportedRegions.contains(normalizedCountryIso)) {
            Log.w(TAG, "Validation failed: invalid or unselected country ($countryIso)")
            return PhoneValidationResult.Failure("Select a country")
        }

        val expectedCallingCode = "+${phoneUtil.getCountryCodeForRegion(normalizedCountryIso)}"

        // Clean user input: strip accidental leading + or duplicate calling code if user typed it
        var cleanInput = rawNationalInput.trim()
        if (cleanInput.isBlank()) {
            return PhoneValidationResult.Failure("Enter your phone number")
        }

        // If user typed the calling code into the national number field, safely strip it
        if (cleanInput.startsWith(expectedCallingCode)) {
            cleanInput = cleanInput.removePrefix(expectedCallingCode).trim()
        } else if (cleanInput.startsWith("+")) {
            // User typed a different calling code
            return PhoneValidationResult.Failure("This phone number is invalid for the selected country ($normalizedCountryIso)")
        }

        // Strip non-digit characters except for standard formatting symbols for parsing
        val digitsOnly = cleanInput.filter { it.isDigit() }
        if (digitsOnly.length < 4) {
            Log.w(TAG, "Validation failed: digits too short (${digitsOnly.length}) for $normalizedCountryIso")
            return PhoneValidationResult.Failure("Phone number length/format is not valid for this country")
        }

        return try {
            // Parse with country context
            val proto: PhoneNumber = phoneUtil.parse(cleanInput, normalizedCountryIso)

            // 1. Verify country code matches selected country
            val parsedCountryCode = "+${proto.countryCode}"
            if (parsedCountryCode != expectedCallingCode) {
                Log.w(TAG, "Validation failed: calling code mismatch ($parsedCountryCode vs $expectedCallingCode)")
                return PhoneValidationResult.Failure("This phone number is invalid for the selected country")
            }

            // 2. Validate plausibility with libphonenumber metadata
            val isPossible = phoneUtil.isPossibleNumber(proto)
            val possibilityReason = phoneUtil.isPossibleNumberWithReason(proto)
            if (!isPossible) {
                val reasonMsg = when (possibilityReason) {
                    PhoneNumberUtil.ValidationResult.TOO_SHORT -> "Phone number length is too short for this country"
                    PhoneNumberUtil.ValidationResult.TOO_LONG -> "Phone number length is too long for this country"
                    PhoneNumberUtil.ValidationResult.INVALID_LENGTH -> "Phone number length/format is not valid for this country"
                    else -> "This phone number is invalid for the selected country"
                }
                Log.w(TAG, "Validation failed: not possible ($possibilityReason) for $normalizedCountryIso")
                return PhoneValidationResult.Failure(reasonMsg)
            }

            // 3. Strict validity check for region
            val isValid = phoneUtil.isValidNumber(proto)
            val isValidForRegion = phoneUtil.isValidNumberForRegion(proto, normalizedCountryIso)
            val expectedCallingCodeInt = phoneUtil.getCountryCodeForRegion(normalizedCountryIso)
            val isValidForCallingCode = isValid && (proto.countryCode == expectedCallingCodeInt)

            if (!isValid || (!isValidForRegion && !isValidForCallingCode)) {
                Log.w(TAG, "Validation failed: isValid=$isValid, isValidForRegion=$isValidForRegion for $normalizedCountryIso")
                return PhoneValidationResult.Failure("This phone number is invalid for the selected country")
            }

            // 4. Extract normalized E.164 and national representations
            val e164 = phoneUtil.format(proto, PhoneNumberFormat.E164)
            val nationalFormatted = phoneUtil.format(proto, PhoneNumberFormat.NATIONAL)
            val nationalDigits = proto.nationalNumber.toString()

            // Useful debug logs (without exposing sensitive user info)
            Log.d(TAG, "Phone validated successfully: country=$normalizedCountryIso, callingCode=$expectedCallingCode, valid=true, E164=$e164")

            PhoneValidationResult.Success(
                countryIsoCode = normalizedCountryIso,
                countryCallingCode = expectedCallingCode,
                nationalPhoneNumber = nationalDigits,
                normalizedPhoneNumber = e164,
                formattedNational = nationalFormatted
            )
        } catch (e: NumberParseException) {
            Log.w(TAG, "NumberParseException for $normalizedCountryIso: ${e.errorType} - ${e.message}")
            val msg = when (e.errorType) {
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE -> "Select a country"
                NumberParseException.ErrorType.NOT_A_NUMBER -> "Phone number contains invalid characters"
                NumberParseException.ErrorType.TOO_SHORT_NSN -> "Phone number is too short for the selected country"
                NumberParseException.ErrorType.TOO_LONG -> "Phone number is too long for the selected country"
                else -> "Phone number length/format is not valid for this country"
            }
            PhoneValidationResult.Failure(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected validation error for $normalizedCountryIso: ${e.message}", e)
            PhoneValidationResult.Failure("This phone number is invalid for the selected country")
        }
    }
}
