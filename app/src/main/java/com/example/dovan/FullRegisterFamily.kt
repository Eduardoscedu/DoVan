package com.example.dovan

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class FullRegisterFamily : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    private var rgUri: Uri? = null
    private var cnhUri: Uri? = null

    private val pickRg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> rgUri = uri }
    private val pickCnh = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> cnhUri = uri }

    private lateinit var adapter: SectionAdapter
    private lateinit var progress: CircularProgressIndicator
    private var firstSnapshotArrived = false

    // Ajuste os ícones conforme os que você já tem no projeto.
    // Importante: use apenas SectionType que EXISTEM no seu enum.
    private val items = listOf(
        Section(R.drawable.ic_location, "Endereço", SectionType.ENDERECO),
        Section(R.drawable.ic_children, "Filhos", SectionType.FILHOS),
        Section(R.drawable.ic_check, "Contato de Emergência", SectionType.CONTATO)

    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_register_family)

        val toolbar = requireViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = requireViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvSections)
        rv.layoutManager = LinearLayoutManager(this)

        progress = requireViewById(R.id.progress)
        progress.visibility = View.VISIBLE

        adapter = SectionAdapter(
            sections = items,
            collectionName = "families", // <- colecao do Firestore para família
            uidProvider = { auth.currentUser?.uid ?: "" },
            pickImage = { isRg -> if (isRg) pickRg.launch("image/*") else pickCnh.launch("image/*") },
            getSelectedUris = { Pair(rgUri, cnhUri) },
            initialData = emptyMap()
        )
        rv.adapter = adapter

        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Log.e("FullRegisterFamily", "UID nulo: usuário não logado")
            finish()
            return
        }

        Firebase.firestore.collection("families").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("FullRegisterFamily", "Snapshot error", err)
                    if (!firstSnapshotArrived) progress.visibility = View.GONE
                    return@addSnapshotListener
                }
                val map = snap?.data ?: emptyMap<String, Any?>()
                Log.d("FullRegisterFamily", "Snapshot keys: ${map.keys}")
                Log.d("FullRegisterFamily", "address.cep = ${map["address.cep"]}")

                adapter.updateData(map)

                if (!firstSnapshotArrived) {
                    firstSnapshotArrived = true
                    progress.visibility = View.GONE
                }
            }
    }
}
