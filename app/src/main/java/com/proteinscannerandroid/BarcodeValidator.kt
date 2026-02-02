package com.proteinscannerandroid

/**
 * Validates barcode checksums to filter out misread barcodes.
 * Supports EAN-13, EAN-8, UPC-A, and UPC-E formats.
 */
object BarcodeValidator {

    /**
     * Validates a barcode's checksum.
     * Returns true if valid or if format doesn't have checksum validation.
     */
    fun isValid(barcode: String): Boolean {
        val cleaned = barcode.trim()
        
        // Must be all digits
        if (!cleaned.all { it.isDigit() }) {
            return false
        }
        
        return when (cleaned.length) {
            13 -> isValidEan13(cleaned)
            12 -> isValidUpcA(cleaned)
            8 -> isValidEan8(cleaned)
            6 -> true // UPC-E is complex, accept for now
            else -> true // Unknown format, accept
        }
    }

    /**
     * EAN-13 checksum validation.
     * Algorithm: 
     * 1. Sum digits at odd positions (1,3,5,7,9,11)
     * 2. Sum digits at even positions (2,4,6,8,10,12) and multiply by 3
     * 3. Add sums together
     * 4. Check digit = (10 - (sum % 10)) % 10
     */
    private fun isValidEan13(barcode: String): Boolean {
        if (barcode.length != 13) return false
        
        var sumOdd = 0
        var sumEven = 0
        
        for (i in 0 until 12) {
            val digit = barcode[i].digitToInt()
            if (i % 2 == 0) {
                sumOdd += digit
            } else {
                sumEven += digit
            }
        }
        
        val total = sumOdd + (sumEven * 3)
        val expectedCheckDigit = (10 - (total % 10)) % 10
        val actualCheckDigit = barcode[12].digitToInt()
        
        return expectedCheckDigit == actualCheckDigit
    }

    /**
     * UPC-A checksum validation.
     * Same algorithm as EAN-13 but for 12 digits.
     */
    private fun isValidUpcA(barcode: String): Boolean {
        if (barcode.length != 12) return false
        
        var sumOdd = 0
        var sumEven = 0
        
        for (i in 0 until 11) {
            val digit = barcode[i].digitToInt()
            if (i % 2 == 0) {
                sumOdd += digit
            } else {
                sumEven += digit
            }
        }
        
        val total = (sumOdd * 3) + sumEven
        val expectedCheckDigit = (10 - (total % 10)) % 10
        val actualCheckDigit = barcode[11].digitToInt()
        
        return expectedCheckDigit == actualCheckDigit
    }

    /**
     * EAN-8 checksum validation.
     */
    private fun isValidEan8(barcode: String): Boolean {
        if (barcode.length != 8) return false
        
        var sumOdd = 0
        var sumEven = 0
        
        for (i in 0 until 7) {
            val digit = barcode[i].digitToInt()
            if (i % 2 == 0) {
                sumOdd += digit
            } else {
                sumEven += digit
            }
        }
        
        val total = (sumOdd * 3) + sumEven
        val expectedCheckDigit = (10 - (total % 10)) % 10
        val actualCheckDigit = barcode[7].digitToInt()
        
        return expectedCheckDigit == actualCheckDigit
    }

    /**
     * Get validation result with reason.
     */
    fun validate(barcode: String): ValidationResult {
        val cleaned = barcode.trim()
        
        if (cleaned.isEmpty()) {
            return ValidationResult(false, "Empty barcode")
        }
        
        if (!cleaned.all { it.isDigit() }) {
            return ValidationResult(false, "Barcode contains non-numeric characters")
        }
        
        val isValid = isValid(cleaned)
        return if (isValid) {
            ValidationResult(true, "Valid")
        } else {
            ValidationResult(false, "Invalid checksum - barcode may have been misread")
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val reason: String
    )
}
