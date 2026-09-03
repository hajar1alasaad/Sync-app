package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun walletRow_defaultValues_areZero() {
    val defaultWallet = com.example.data.model.WalletRow()
    assertEquals(0.0, defaultWallet.balance, 0.001)
    assertEquals("USD", defaultWallet.currency)
  }

  @Test
  fun profileRow_defaultValues_areSafe() {
    val defaultProfile = com.example.data.model.ProfileRow()
    assertEquals("", defaultProfile.id)
    assertNull(defaultProfile.email)
    assertNull(defaultProfile.fullName)
  }
}
