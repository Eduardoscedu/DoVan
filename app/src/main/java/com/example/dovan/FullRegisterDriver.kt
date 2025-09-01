package com.example.dovan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class FullRegisterDriver : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    private var rgUri: Uri? = null
    private var cnhUri: Uri? = null

    private val pickRg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> rgUri = uri }
    private val pickCnh = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> cnhUri = uri }

    private lateinit var adapter: SectionAdapter
    private lateinit var progress: CircularProgressIndicator
    private var firstSnapshotArrived = false

    private val items = listOf(
        Section(R.drawable.ic_badge, "Documentos", SectionType.DOCUMENTOS),
        Section(R.drawable.ic_car, "Veículo", SectionType.VEICULO),
        Section(R.drawable.ic_location, "Endereço", SectionType.ENDERECO),
        Section(R.drawable.ic_check, "Contato de Emergência", SectionType.CONTATO)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_register_common)

        val toolbar =
            requireViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = requireViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvSections)
        val meuBotao = findViewById<Button>(R.id.BtnAvancar)
        rv.layoutManager = LinearLayoutManager(this)

        progress = requireViewById(R.id.progress)
        progress.visibility = View.VISIBLE

        adapter = SectionAdapter(
            sections = items,
            collectionName = "drivers",
            uidProvider = { auth.currentUser?.uid ?: "" },
            pickImage = { isRg -> if (isRg) pickRg.launch("image/*") else pickCnh.launch("image/*") },
            getSelectedUris = { Pair(rgUri, cnhUri) },
            initialData = emptyMap()
        )
        rv.adapter = adapter

        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Log.e("FullRegisterDriver", "UID nulo: usuário não logado")
            finish()
            return
        }

        Firebase.firestore.collection("drivers").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("FullRegisterDriver", "Snapshot error", err)
                    if (!firstSnapshotArrived) progress.visibility = View.GONE
                    return@addSnapshotListener
                }
                val map = snap?.data ?: emptyMap<String, Any?>()
                Log.d("FullRegisterDriver", "Snapshot keys: ${map.keys}")
                Log.d("FullRegisterDriver", "address.cep = ${map["address.cep"]}")
                Log.d("FullRegisterDriver", "docs.cnhNumber = ${map["docs.cnhNumber"]}")

                adapter.updateData(map)

                if (!firstSnapshotArrived) {
                    firstSnapshotArrived = true
                    progress.visibility = View.GONE
                }
            }
        meuBotao.setOnClickListener {
            val intent = Intent(this, DriverRouteActivity::class.java)
            startActivity(intent)
        }
    }
}
