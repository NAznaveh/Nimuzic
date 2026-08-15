package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testLrcTimestampCalculation() {
    val minutes = 1L
    val seconds = 25L
    val fractionMs = 450L
    val timeMs = minutes * 60_000L + seconds * 1_000L + fractionMs
    assertEquals(85450L, timeMs)
  }
}

