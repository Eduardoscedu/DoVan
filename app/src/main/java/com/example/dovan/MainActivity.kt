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
                            startActivity(Intent(this, MenuActivity::class.java)
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
                    val missing = computeMissingForDriver(data)
                    if (missing.isEmpty()) onCompleteOk() else onNeedCompleteDriver(missing)
                } else {
                    // Tenta FAMILIES
                    db.collection("families").document(uid).get()
                        .addOnSuccessListener { fsnap ->
                            if (fsnap.exists()) {
                                val data = fsnap.data ?: emptyMap<String, Any?>()
                                val missing = computeMissingForFamily(data)
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

    private fun computeMissingForDriver(data: Map<String, Any?>): List<String> {
        val falta = mutableListOf<String>()
        val docs = data["docs"] as? Map<*, *>
        val cnhNumber = (docs?.get("cnhNumber") as? String).orEmpty()
        if (cnhNumber.isBlank()) falta.add("Número da CNH")

        val vehicle = data["vehicle"] as? Map<*, *>
        if ((vehicle?.get("marca") as? String).isNullOrBlank()) falta.add("Marca do veículo")
        if ((vehicle?.get("placa") as? String).isNullOrBlank()) falta.add("Placa do veículo")

        val addr = data["address"] as? Map<*, *>
        if ((addr?.get("cep") as? String).isNullOrBlank()) falta.add("CEP")
        if ((addr?.get("cidade") as? String).isNullOrBlank()) falta.add("Cidade")
        if ((addr?.get("uf") as? String).isNullOrBlank()) falta.add("UF")

        val em = data["emergency"] as? Map<*, *>
        if ((em?.get("nome") as? String).isNullOrBlank()) falta.add("Nome do contato de emergência")
        if ((em?.get("telefone") as? String).isNullOrBlank()) falta.add("Telefone do contato de emergência")

        return falta
    }

    private fun computeMissingForFamily(data: Map<String, Any?>): List<String> {
        val falta = mutableListOf<String>()
        val docs = data["docs"] as? Map<*, *>
        if ((docs?.get("cnhNumber") as? String).isNullOrBlank()) falta.add("Número da CNH")

        val addr = data["address"] as? Map<*, *>
        if ((addr?.get("cep") as? String).isNullOrBlank()) falta.add("CEP")
        if ((addr?.get("cidade") as? String).isNullOrBlank()) falta.add("Cidade")
        if ((addr?.get("uf") as? String).isNullOrBlank()) falta.add("UF")

        val em = data["emergency"] as? Map<*, *>
        if ((em?.get("nome") as? String).isNullOrBlank()) falta.add("Nome do contato de emergência")
        if ((em?.get("telefone") as? String).isNullOrBlank()) falta.add("Telefone do contato de emergência")

        return falta
    }

    private fun showMissingDialog(missing: List<String>, goAction: () -> Unit) {
        val msg = "Você ainda precisa preencher:\n\n• " + missing.joinToString("\n• ")
        AlertDialog.Builder(this)
            .setTitle("Finalize seu cadastro")
            .setMessage(msg)
            .setPositiveButton("Completar agora") { _, _ -> goAction() }
            .setNegativeButton("Depois", null)
            .show()
    }
}
