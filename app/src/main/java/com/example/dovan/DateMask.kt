package com.example.dovan

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

// ALTERAÇÃO: máscara que formata dd/MM/yyyy durante a digitação
class DateMask(private val et: EditText) : TextWatcher {
    private var isUpdating = false
    private var old = ""

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        old = s?.toString() ?: ""
    }
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {
        if (isUpdating) return
        isUpdating = true

        val digits = s.toString().filter { it.isDigit() }.take(8) // ddMMyyyy
        val sb = StringBuilder()
        for (i in digits.indices) {
            sb.append(digits[i])
            if (i == 1 || i == 3) sb.append('/')
        }
        val formatted = sb.toString()
        val cursor = formatted.length

        et.setText(formatted)
        et.setSelection(cursor.coerceAtMost(formatted.length))
        isUpdating = false
    }
}

// ALTERAÇÃO: valida se a string está no formato e representa uma data válida
fun isValidDateDMY(input: String): Boolean {
    if (input.length != 10) return false // dd/MM/yyyy
    val parts = input.split("/")
    if (parts.size != 3) return false
    val d = parts[0].toIntOrNull() ?: return false
    val m = parts[1].toIntOrNull() ?: return false
    val y = parts[2].toIntOrNull() ?: return false
    if (y !in 1900..2100) return false
    val daysInMonth = intArrayOf(31, if (isLeap(y)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    if (m !in 1..12) return false
    return d in 1..daysInMonth[m - 1]
}
private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)
