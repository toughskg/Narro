package com.narro.app.feature

import org.junit.Assert.assertEquals
import org.junit.Test

class PinEntryTest {
    @Test
    fun pinEntryAcceptsOnlyFourDigits() {
        var pin = ""
        listOf(1, 2, 3, 4, 5).forEach { pin = appendPinDigit(pin, it) }

        assertEquals("1234", pin)
    }

    @Test
    fun backspaceRemovesOnlyLastDigit() {
        assertEquals("123", deletePinDigit("1234"))
        assertEquals("", deletePinDigit(""))
    }

    @Test
    fun enablingAppLockAlwaysRegistersANewPin() {
        assertEquals(LockToggleAction.REGISTER_NEW_PIN, lockToggleAction(enabled = true))
    }

    @Test
    fun disablingAppLockClearsTheExistingPin() {
        assertEquals(LockToggleAction.CLEAR_PIN, lockToggleAction(enabled = false))
    }
}
