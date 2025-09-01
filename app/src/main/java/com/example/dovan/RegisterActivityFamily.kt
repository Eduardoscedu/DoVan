package com.example.dovan

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

class RegisterActivityFamily : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_family)

        auth = FirebaseAuth.getInstance()

        val root = findViewById<View>(R.id.register_family_root)
        val nameEt = findViewById<EditText>(R.id.editTextRegisterName)
        val cpfEt = findViewById<EditText>(R.id.editTextCPF)
        val emailEt = findViewById<EditText>(R.id.editTextRegisterEmail)
        val telefoneEt = findViewById<EditText>(R.id.editTextDriverPhone) // confirme id no XML
        val passEt = findViewById<EditText>(R.id.editTextRegisterPassword)
        val pass2Et = findViewById<EditText>(R.id.editTextRegisterConfirmPassword)
        val dataNascEt = findViewById<EditText>(R.id.editTextDataNasc)
        val registerBtn = findViewById<Button>(R.id.buttonRegister)

        // Insets (se usar)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        // === MÁSCARAS VISUAIS (repostas) ===
        cpfEt.addTextChangedListener(CpfMask(cpfEt))             // 000.000.000-00
        telefoneEt.addTextChangedListener(PhoneMask(telefoneEt)) // (11) 90000-0000
        dataNascEt.addTextChangedListener(DateMask(dataNascEt))  // dd/MM/yyyy

        findViewById<View>(R.id.btnClose)?.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        registerBtn.setOnClickListener {
            val name = nameEt.text.toString().trim()
            val cpfRaw = cpfEt.text.toString().filter { it.isDigit() }
            val email = emailEt.text.toString().trim()
            val phoneRaw = telefoneEt.text.toString().filter { it.isDigit() }
            val pass = passEt.text.toString()
            val pass2 = pass2Et.text.toString()
            val dataStr = dataNascEt.text.toString().trim()

            // Validações
            when {
                name.isEmpty() -> { nameEt.error = "Informe o nome"; nameEt.requestFocus(); return@setOnClickListener }
                cpfRaw.length != 11 -> { cpfEt.error = "CPF deve ter 11 dígitos"; cpfEt.requestFocus(); return@setOnClickListener }
                !isValidCpf(cpfRaw) -> { cpfEt.error = "CPF inválido"; cpfEt.requestFocus(); return@setOnClickListener }
                email.isEmpty() -> { emailEt.error = "Informe o e-mail"; emailEt.requestFocus(); return@setOnClickListener }
                phoneRaw.length !in setOf(11,13) -> { telefoneEt.error = "Telefone inválido (use DDD). Ex.: (11) 91234-5678"; telefoneEt.requestFocus(); return@setOnClickListener }
                !isValidDateDMY(dataStr) -> { dataNascEt.error = "Data inválida (dd/MM/yyyy)"; dataNascEt.requestFocus(); return@setOnClickListener }
                pass.length < 6 -> { passEt.error = "Senha deve ter pelo menos 6 caracteres"; passEt.requestFocus(); return@setOnClickListener }
                pass != pass2 -> { pass2Et.error = "As senhas não conferem"; pass2Et.requestFocus(); return@setOnClickListener }
            }

            // Quebra data para { dia, mes, ano }
            val (dia, mes, ano) = dataStr.split("/").let {
                Triple(it[0].toIntOrNull() ?: 0, it[1].toIntOrNull() ?: 0, it[2].toIntOrNull() ?: 0)
            }
            val nascimentoMap = mapOf("nascimento.dia" to dia, "nascimento.mes" to mes, "nascimento.ano" to ano)

            registerBtn.isEnabled = false

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: ""
                        val profile = FamilyProfile(
                            uid = uid,
                            name = name,
                            cpf = cpfRaw,
                            email = email,
                            telefone = phoneRaw
                        )

                        Firebase.firestore.collection("families").document(uid)
                            .set(profile)
                            .addOnSuccessListener {
                                // salva nascimento (merge)
                                Firebase.firestore.collection("families").document(uid)
                                    .set(nascimentoMap, SetOptions.merge())
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Cadastro realizado com sucesso!", Toast.LENGTH_LONG).show()
                                        startActivity(
                                            Intent(this, FullRegisterFamily::class.java)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        )
                                    }
                                    .addOnFailureListener { e ->
                                        registerBtn.isEnabled = true
                                        Toast.makeText(this, "Erro ao salvar nascimento: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                registerBtn.isEnabled = true
                                Toast.makeText(this, "Erro ao salvar no banco: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        registerBtn.isEnabled = true
                        Snackbar.make(root, "Erro no cadastro: ${task.exception?.localizedMessage}", Snackbar.LENGTH_LONG).show()
                    }
                }
        }
    }

    // ======= UTILITÁRIOS: MÁSCARAS E VALIDAÇÕES =======

    private class CpfMask(private val et: EditText) : TextWatcher {
        private var updating = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (updating) return
            updating = true
            val d = s.toString().filter { it.isDigit() }.take(11)
            val out = StringBuilder()
            for (i in d.indices) {
                out.append(d[i])
                if (i == 2 || i == 5) out.append('.')
                if (i == 8) out.append('-')
            }
            et.setText(out.toString())
            et.setSelection(et.text?.length ?: 0)
            updating = false
        }
    }

    private class PhoneMask(private val et: EditText) : TextWatcher {
        private var updating = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (updating) return
            updating = true
            var d = s.toString().filter { it.isDigit() }.take(13)
            if (d.startsWith("55") && d.length > 11) d = d.drop(2)
            val out = when {
                d.length >= 11 -> "(${d.substring(0,2)}) ${d.substring(2,7)}-${d.substring(7,11)}"
                d.length >= 10 -> "(${d.substring(0,2)}) ${d.substring(2,6)}-${d.substring(6,10)}"
                d.length >= 3 -> "(${d.substring(0,2)}) ${d.substring(2)}"
                d.length >= 1 -> d
                else -> ""
            }
            et.setText(out)
            et.setSelection(et.text?.length ?: 0)
            updating = false
        }
    }

    private class DateMask(private val et: EditText) : TextWatcher {
        private var updating = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (updating) return
            updating = true
            val d = s.toString().filter { it.isDigit() }.take(8)
            val out = StringBuilder()
            for (i in d.indices) {
                out.append(d[i])
                if (i == 1 || i == 3) out.append('/')
            }
            et.setText(out.toString())
            et.setSelection(et.text?.length ?: 0)
            updating = false
        }
    }

    private fun isValidDateDMY(input: String): Boolean {
        if (input.length != 10) return false
        val p = input.split("/")
        val d = p.getOrNull(0)?.toIntOrNull() ?: return false
        val m = p.getOrNull(1)?.toIntOrNull() ?: return false
        val y = p.getOrNull(2)?.toIntOrNull() ?: return false
        if (y !in 1900..2100) return false
        val dim = intArrayOf(31, if (isLeap(y)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return m in 1..12 && d in 1..dim[m - 1]
    }
    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)

    private fun isValidCpf(cpf: String): Boolean {
        val digits = cpf.filter { it.isDigit() }
        if (digits.length != 11 || digits.all { it == digits[0] }) return false
        fun calc(slice: String, start: Int): Int {
            var sum = 0; var factor = start
            for (c in slice) sum += (c - '0') * factor--
            val mod = sum % 11
            return if (mod < 2) 0 else 11 - mod
        }
        val d1 = calc(digits.substring(0, 9), 10)
        val d2 = calc(digits.substring(0, 10), 11)
        return digits.endsWith("$d1$d2")
    }
}
