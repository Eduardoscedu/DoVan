package com.example.dovan

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val emailEt = findViewById<EditText>(R.id.editTextEmail)
        val passEt  = findViewById<EditText>(R.id.editTextPassword)
        val loginBt = findViewById<Button>(R.id.buttonLogin)
        val txtRegister = findViewById<TextView>(R.id.textViewRegister)

        // 👉 Clique no "Registre-se"
        txtRegister.setOnClickListener {
            // abre a tela de escolha de papel (driver/family)
            startActivity(Intent(this, ChooseRoleActivity::class.java))
        }

        loginBt.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val pass  = passEt.text.toString()

            if (email.isEmpty()) { emailEt.error = "Informe o e-mail"; emailEt.requestFocus(); return@setOnClickListener }
            if (pass.isEmpty())  { passEt.error  = "Informe a senha";  passEt.requestFocus();  return@setOnClickListener }

            loginBt.isEnabled = false

            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        loginBt.isEnabled = true
                        Toast.makeText(this, task.exception?.localizedMessage ?: "Falha no login", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    val uid = auth.currentUser?.uid ?: run {
                        loginBt.isEnabled = true
                        Toast.makeText(this, "Usuário inválido", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    validateAfterLogin(uid,
                        onCompleteOk = {
                            // navegação quando já está tudo completo
                            startActivity(Intent(this, MenuFamiliesActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                            finish()
                        },
                        onNeedCompleteDriver = { missing ->
                            showMissingDialog(missing) {
                                startActivity(Intent(this, FullRegisterDriver::class.java))
                            }
                        },
                        onNeedCompleteFamily = { missing ->
                            showMissingDialog(missing) {
                                startActivity(Intent(this, FullRegisterFamily::class.java))
                            }
                        },
                        onNoProfile = {
                            AlertDialog.Builder(this)
                                .setTitle("Complete seu cadastro")
                                .setMessage("Não encontramos seu perfil. Selecione um papel para começar.")
                                .setPositiveButton("Escolher") { _, _ ->
                                    startActivity(Intent(this, ChooseRoleActivity::class.java))
                                }
                                .setNegativeButton("Agora não", null)
                                .show()
                        },
                        finallyEnable = { loginBt.isEnabled = true }
                    )
                }
        }
    }

    // ------- Core: ler Firestore e montar pendências com texto enxuto --------
    private fun validateAfterLogin(
        uid: String,
        onCompleteOk: () -> Unit,
        onNeedCompleteDriver: (List<String>) -> Unit,
        onNeedCompleteFamily: (List<String>) -> Unit,
        onNoProfile: () -> Unit,
        finallyEnable: () -> Unit
    ) {
        val db = Firebase.firestore

        // Primeiro tenta DRIVERS
        db.collection("drivers").document(uid).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val data = snap.data ?: emptyMap<String, Any?>()
                    val missing = computeMissingForDriver(data) // CHANGED: função robusta (dot/aninhado)
                    if (missing.isEmpty()) onCompleteOk() else onNeedCompleteDriver(missing)
                } else {
                    // Tenta FAMILIES
                    db.collection("families").document(uid).get()
                        .addOnSuccessListener { fsnap ->
                            if (fsnap.exists()) {
                                val data = fsnap.data ?: emptyMap<String, Any?>()
                                val missing = computeMissingForFamily(data) // CHANGED
                                if (missing.isEmpty()) onCompleteOk() else onNeedCompleteFamily(missing)
                            } else {
                                onNoProfile()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Erro ao ler perfil: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        .addOnCompleteListener { finallyEnable() }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao ler perfil: ${e.message}", Toast.LENGTH_LONG).show()
                finallyEnable()
            }
    }

    // ---------------- Helpers para ler tanto dot-path quanto aninhado ----------------
    // CHANGED: estes helpers permitem que funcione mesmo se os campos estiverem salvos
    // como "address.cep" (flat) ou como { address: { cep: ... } } (aninhado).
    private fun getNestedOrFlat(m: Map<String, Any?>, path: String): Any? {
        var cur: Any? = m
        for (k in path.split(".")) {
            cur = (cur as? Map<*, *>)?.get(k)
            if (cur == null) break
        }
        if (cur != null) return cur
        return m[path]
    }
    private fun str(m: Map<String, Any?>, path: String): String =
        (getNestedOrFlat(m, path) as? String).orEmpty()
    private fun num(m: Map<String, Any?>, path: String): Int =
        (getNestedOrFlat(m, path) as? Number)?.toInt() ?: 0

    // ---------------- Validações ----------------

    // CHANGED: Driver verifica Documentos + Veículo + Endereço + Contato
    private fun computeMissingForDriver(data: Map<String, Any?>): List<String> {
        val falta = mutableListOf<String>()

        // Documentos
        if (str(data, "docs.cnhNumber").isBlank()) falta += "Número da CNH"

        // Veículo
        if (str(data, "vehicle.marca").isBlank()) falta += "Marca do veículo"
        if (str(data, "vehicle.placa").isBlank()) falta += "Placa do veículo"

        // Endereço
        if (str(data, "address.cep").isBlank())    falta += "CEP"
        if (str(data, "address.cidade").isBlank()) falta += "Cidade"
        if (str(data, "address.uf").isBlank())     falta += "UF"

        // Contato de Emergência
        if (str(data, "emergency.nome").isBlank())     falta += "Nome do contato de emergência"
        if (str(data, "emergency.telefone").isBlank()) falta += "Telefone do contato de emergência"

        return falta
    }

    // CHANGED: Família NÃO valida Documentos
    private fun computeMissingForFamily(data: Map<String, Any?>): List<String> {
        val falta = mutableListOf<String>()

        // Endereço
        if (str(data, "address.cep").isBlank())    falta += "CEP"
        if (str(data, "address.cidade").isBlank()) falta += "Cidade"
        if (str(data, "address.uf").isBlank())     falta += "UF"

        // Contato de Emergência
        if (str(data, "emergency.nome").isBlank())     falta += "Nome do contato de emergência"
        if (str(data, "emergency.telefone").isBlank()) falta += "Telefone do contato de emergência"

        // (Opcional) Exigir ao menos 1 filho:
        // val children = getNestedOrFlat(data, "children") as? List<*> ?: emptyList<Any>()
        // if (children.isEmpty()) falta += "Pelo menos 1 filho"

        return falta
    }

    // ---------------- Diálogo enxuto ----------------
    private fun showMissingDialog(missing: List<String>, goAction: () -> Unit) {
        val bullets = missing.joinToString(separator = "\n") { "• $it" }

        val title = when (missing.size) {
            0 -> "Tudo certo!"
            1 -> "Falta 1 item"
            else -> "Faltam ${missing.size} Itens a serem Cadastrados"
        }

        val positive = if (missing.size <= 1) "Preencher agora" else "Completar agora"

        val message = if (missing.isEmpty()) {
            "Seu cadastro já está completo."
        } else {
            bullets // apenas o que falta
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Depois", null)
            .setPositiveButton(positive) { _, _ -> goAction() }
            .show()
    }
}
