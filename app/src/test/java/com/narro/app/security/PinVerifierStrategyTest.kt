package com.narro.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinVerifierStrategyTest {
    @Test
    fun newRecordsUseKeystoreHmacVerifier() {
        assertEquals(
            PinVerifierStrategy.KEYSTORE_HMAC,
            pinVerifierStrategy(iterations = 0),
        )
    }

    @Test
    fun existingPbkdf2RecordsRemainMigratable() {
        assertEquals(
            PinVerifierStrategy.LEGACY_PBKDF2,
            pinVerifierStrategy(iterations = 600_000),
        )
    }
}
