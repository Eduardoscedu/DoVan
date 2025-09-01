package com.example.dovan

import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore

// NOTE: Section / SectionType devem estar definidos em outro arquivo do seu projeto.
// data class Section(val iconRes: Int, val title: String, val type: SectionType)
// enum class SectionType { DOCUMENTOS, VEICULO, ENDERECO, CONTATO }

class SectionAdapter(
    private val sections: List<Section>,
    private val collectionName: String,
    private val uidProvider: () -> String,
    private val pickImage: (isRg: Boolean) -> Unit,
    private val getSelectedUris: () -> Pair<Uri?, Uri?>,
    initialData: Map<String, Any?> = emptyMap()
) : RecyclerView.Adapter<SectionAdapter.VH>() {

    private val expanded = mutableSetOf<Int>()
    private var data: Map<String, Any?> = initialData

    // CHANGED: rebind total para refletir estado (travado/aberto) corretamente
    fun updateData(newData: Map<String, Any?>) {
        data = newData
        notifyDataSetChanged()
    }

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val card: MaterialCardView = root.findViewById(R.id.rootCard)
        val header: View = root.findViewById(R.id.header)
        val content: ViewGroup = root.findViewById(R.id.content)
        val ivIcon: ImageView = root.findViewById(R.id.ivIcon)
        val tvTitle: TextView = root.findViewById(R.id.tvTitle)
        val ivEndIcon: ImageView = root.findViewById(R.id.ivEndIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_section_card, parent, false)
        return VH(v)
    }

    override fun getItemCount() = sections.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = sections[position]
        holder.ivIcon.setImageResource(item.iconRes)
        holder.tvTitle.text = item.title
        holder.content.removeAllViews()

        val inflater = LayoutInflater.from(holder.root.context)

        // Monta conteúdo sempre (inputs serão travados conforme valores existentes)
        when (item.type) {
            SectionType.DOCUMENTOS -> bindDocs(inflater, holder.content)
            SectionType.VEICULO    -> bindVeiculo(inflater, holder.content)
            SectionType.ENDERECO   -> bindEndereco(inflater, holder.content)
            SectionType.CONTATO    -> bindContato(inflater, holder.content)
            SectionType.FILHOS     -> bindFilhos(inflater, holder.content)
        }

        // Estado da seção
        val done = isSectionComplete(item.type)

        if (done) {
            // CHANGED: visual cinza e impede expansão
            setCardDone(holder.card, true)
            holder.ivEndIcon.setImageResource(R.drawable.ic_check)
            holder.content.visibility = View.GONE
            setEnabledRecursively(holder.content, false)
            holder.header.isClickable = false
            holder.header.setOnClickListener(null)
            // opcional: também pode bloquear o card todo:
            holder.card.isClickable = false
            holder.card.isFocusable = false
        } else {
            setCardDone(holder.card, false)
            val isOpen = expanded.contains(position)
            holder.content.visibility = if (isOpen) View.VISIBLE else View.GONE
            holder.ivEndIcon.setImageResource(
                if (isOpen) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
            )
            holder.header.isClickable = true
            holder.header.setOnClickListener {
                if (expanded.contains(position)) expanded.remove(position) else expanded.add(position)
                notifyItemChanged(position)
            }
        }
    }


    private fun childrenList(): List<Map<String, Any?>> {
        val raw = getNestedOrFlat(data, "children")
        @Suppress("UNCHECKED_CAST")
        return when (raw) {
            is List<*> -> raw.filterIsInstance<Map<String, Any?>>()
            else -> emptyList()
        }
    }

    // Máscara simples dd/mm/aaaa
    private fun attachBirthMask(et: EditText) {
        et.addTextChangedListener(object : android.text.TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isEditing) return
                isEditing = true
                val digits = s.toString().filter { it.isDigit() }.take(8)
                val sb = StringBuilder()
                for ((i, c) in digits.withIndex()) {
                    sb.append(c)
                    if (i == 1 || i == 3) sb.append('/')
                }
                et.setText(sb.toString())
                et.setSelection(et.text?.length ?: 0)
                isEditing = false
            }
        })
    }

    private fun bindFilhos(inflater: LayoutInflater, parent: ViewGroup) {
        val v = inflater.inflate(R.layout.section_filhos, parent, false)
        val container = v.findViewById<LinearLayout>(R.id.containerFilhos)
        val etNome = v.findViewById<EditText>(R.id.etFilhoNome)
        val etNasc = v.findViewById<EditText>(R.id.etFilhoNascimento)
        val etEscola = v.findViewById<EditText>(R.id.etFilhoEscola)
        val etObs = v.findViewById<EditText>(R.id.etFilhoObs)
        val btnAdd = v.findViewById<Button>(R.id.btnAdicionarFilho)

        // máscara dd/mm/aaaa no campo de nascimento
        attachBirthMask(etNasc)

        // Renderiza filhos já cadastrados (somente leitura)
        container.removeAllViews()
        val filhos = childrenList()
        for (child in filhos) {
            val nome = (child["nome"] as? String).orEmpty()
            val dia  = (child["nascimentoDia"] as? Number)?.toInt()
            val mes  = (child["nascimentoMes"] as? Number)?.toInt()
            val ano  = (child["nascimentoAno"] as? Number)?.toInt()
            val escola = (child["escola"] as? String).orEmpty()
            val obs = (child["obs"] as? String).orEmpty()

            val item = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
            }
            val tv = TextView(parent.context).apply {
                text = buildString {
                    append("• ").append(nome)
                    if (dia != null && mes != null && ano != null) {
                        append("  (").append(String.format("%02d/%02d/%04d", dia, mes, ano)).append(")")
                    }
                    if (escola.isNotBlank()) append("\nEscola: ").append(escola)
                    if (obs.isNotBlank()) append("\nObs.: ").append(obs)
                }
            }
            // “cinza” leve
            item.alpha = 0.75f
            item.isEnabled = false

            item.addView(tv)
            container.addView(item)
        }

        // Adicionar novo filho
        btnAdd.setOnClickListener {
            val nome = etNome.text.toString().trim()
            val nasc = etNasc.text.toString().trim() // dd/mm/aaaa
            val escola = etEscola.text.toString().trim()
            val obs = etObs.text.toString().trim()

            if (nome.isEmpty()) { etNome.error = "Informe o nome"; etNome.requestFocus(); return@setOnClickListener }
            val digits = nasc.filter { it.isDigit() }
            if (digits.length != 8) { etNasc.error = "Data inválida"; etNasc.requestFocus(); return@setOnClickListener }
            val dia = digits.substring(0,2).toInt()
            val mes = digits.substring(2,4).toInt()
            val ano = digits.substring(4,8).toInt()

            // monta o objeto filho
            val child = hashMapOf(
                "nome" to nome,
                "nascimentoDia" to dia,
                "nascimentoMes" to mes,
                "nascimentoAno" to ano,
                "escola" to escola,
                "obs" to obs,
                "createdAt" to System.currentTimeMillis()
            )

            val uid = uidProvider()
            val ref = Firebase.firestore.collection(collectionName).document(uid)

            // usamos arrayUnion para empilhar sem sobrescrever os existentes
            ref.update("children", FieldValue.arrayUnion(child))
                .addOnSuccessListener {
                    toast(parent, "Filho adicionado!")
                    // limpa inputs
                    etNome.text?.clear()
                    etNasc.text?.clear()
                    etEscola.text?.clear()
                    etObs.text?.clear()
                }
                .addOnFailureListener { e ->
                    // se o campo não existir ainda, 'update' pode falhar; fazemos um merge criando o array
                    ref.set(mapOf("children" to listOf(child)), SetOptions.merge())
                        .addOnSuccessListener { toast(parent, "Filho adicionado!") }
                        .addOnFailureListener { ex -> toast(parent, "Erro: ${ex.message}") }
                }
        }

        parent.addView(v)
    }

    // ----------------- helpers para ler mapa (aninhado ou dot-path) -----------------

    // CHANGED: versão sem 'break' em lambda e com fallback para chave plana "a.b"
    private fun getNestedOrFlat(m: Map<String, Any?>, path: String): Any? {
        // tenta aninhado: a -> b -> c
        var cur: Any? = m
        for (k in path.split(".")) {
            cur = (cur as? Map<*, *>)?.get(k)
            if (cur == null) break
        }
        if (cur != null) return cur
        // fallback: chave plana "a.b.c"
        return m[path]
    }

    private fun str(path: String) = (getNestedOrFlat(data, path) as? String).orEmpty()
    private fun num(path: String) = (getNestedOrFlat(data, path) as? Number)?.toInt() ?: 0

    // ----------------- Seções -----------------
    private fun bindDocs(inflater: LayoutInflater, parent: ViewGroup) {
        val v = inflater.inflate(R.layout.section_documentos, parent, false)
        val etCnh = v.findViewById<EditText>(R.id.etCnh)
        val ivRg = v.findViewById<ImageView>(R.id.ivRg)
        val ivCnh = v.findViewById<ImageView>(R.id.ivCnh)
        val btnRg = v.findViewById<Button>(R.id.btnRg)
        val btnCnh = v.findViewById<Button>(R.id.btnCnhFoto)
        val btnSalvar = v.findViewById<Button>(R.id.btnSalvarDocs)

        val cnhNumber = str("docs.cnhNumber")
        val rgUrl = str("docs.rgPhotoUrl")
        val cnhUrl = str("docs.cnhPhotoUrl")

        if (cnhNumber.isNotEmpty()) etCnh.setText(cnhNumber)
        if (rgUrl.isNotEmpty()) Glide.with(parent.context).load(rgUrl).into(ivRg)
        if (cnhUrl.isNotEmpty()) Glide.with(parent.context).load(cnhUrl).into(ivCnh)

        // CHANGED: trava cada input já preenchido
        lockIfHasText(etCnh)

        btnRg.setOnClickListener { pickImage(true) }
        btnCnh.setOnClickListener { pickImage(false) }

        btnSalvar.setOnClickListener {
            val uid = uidProvider()
            val map = mapOf("docs.cnhNumber" to etCnh.text.toString().trim())
            Firebase.firestore.collection(collectionName).document(uid)
                .set(map, SetOptions.merge())
                .addOnSuccessListener { toast(parent, "Documentos salvos!") }
                .addOnFailureListener { e -> toast(parent, "Erro: ${e.message}") }

            val (rgUri, cnhUri) = getSelectedUris()
            rgUri?.let { ivRg.setImageURI(it) }
            cnhUri?.let { ivCnh.setImageURI(it) }
        }
        parent.addView(v)
    }

    private fun bindVeiculo(inflater: LayoutInflater, parent: ViewGroup) {
        val v = inflater.inflate(R.layout.section_veiculo, parent, false)
        val marca = v.findViewById<EditText>(R.id.etMarca)
        val cap = v.findViewById<EditText>(R.id.etCapacidade)
        val placa = v.findViewById<EditText>(R.id.etPlaca)
        val cor = v.findViewById<EditText>(R.id.etCor)
        val ano = v.findViewById<EditText>(R.id.etAno)
        val btn = v.findViewById<Button>(R.id.btnSalvarVeiculo)

        val vMarca = str("vehicle.marca")
        val vCap = num("vehicle.capacidadeMaxima")
        val vPlaca = str("vehicle.placa")
        val vCor = str("vehicle.cor")
        val vAno = num("vehicle.ano")

        if (vMarca.isNotEmpty()) marca.setText(vMarca)
        if (vCap > 0) cap.setText(vCap.toString())
        if (vPlaca.isNotEmpty()) placa.setText(vPlaca)
        if (vCor.isNotEmpty()) cor.setText(vCor)
        if (vAno > 0) ano.setText(vAno.toString())

        // CHANGED: trava o que já tem valor
        lockIfHasText(marca)
        if (vCap > 0) lock(cap)
        lockIfHasText(placa)
        lockIfHasText(cor)
        if (vAno > 0) lock(ano)

        btn.setOnClickListener {
            val uid = uidProvider()
            val map = mapOf(
                "vehicle.marca" to marca.text.toString().trim(),
                "vehicle.capacidadeMaxima" to (cap.text.toString().toIntOrNull() ?: 0),
                "vehicle.placa" to placa.text.toString().trim().uppercase(),
                "vehicle.cor" to cor.text.toString().trim(),
                "vehicle.ano" to (ano.text.toString().toIntOrNull() ?: 0)
            )
            Firebase.firestore.collection(collectionName).document(uid)
                .set(map, SetOptions.merge())
                .addOnSuccessListener { toast(parent, "Veículo salvo!") }
                .addOnFailureListener { e -> toast(parent, "Erro: ${e.message}") }
        }
        parent.addView(v)
    }

    private fun bindEndereco(inflater: LayoutInflater, parent: ViewGroup) {
        val v = inflater.inflate(R.layout.section_endereco, parent, false)
        val cep = v.findViewById<EditText>(R.id.etCep)
        val log = v.findViewById<EditText>(R.id.etLogradouro)
        val num = v.findViewById<EditText>(R.id.etNumero)
        val bai = v.findViewById<EditText>(R.id.etBairro)
        val cid = v.findViewById<EditText>(R.id.etCidade)
        val uf = v.findViewById<EditText>(R.id.etUf)
        val btn = v.findViewById<Button>(R.id.btnSalvarEndereco)

        val aCep = str("address.cep")
        val aLog = str("address.logradouro")
        val aNum = num("address.numero")
        val aBai = str("address.bairro")
        val aCid = str("address.cidade")
        val aUf  = str("address.uf")

        if (aCep.isNotEmpty()) cep.setText(aCep)
        if (aLog.isNotEmpty()) log.setText(aLog)
        if (aNum > 0) num.setText(aNum.toString())
        if (aBai.isNotEmpty()) bai.setText(aBai)
        if (aCid.isNotEmpty()) cid.setText(aCid)
        if (aUf.isNotEmpty()) uf.setText(aUf)

        // CHANGED: trava cada input que já possui valor
        lockIfHasText(cep)
        lockIfHasText(log)
        if (aNum > 0) lock(num)
        lockIfHasText(bai)
        lockIfHasText(cid)
        lockIfHasText(uf)

        btn.setOnClickListener {
            val uid = uidProvider()
            val map = mapOf(
                "address.cep" to cep.text.toString().filter { it.isDigit() },
                "address.logradouro" to log.text.toString().trim(),
                "address.numero" to (num.text.toString().toIntOrNull() ?: 0),
                "address.bairro" to bai.text.toString().trim(),
                "address.cidade" to cid.text.toString().trim(),
                "address.uf" to uf.text.toString().trim().uppercase()
            )
            Firebase.firestore.collection(collectionName).document(uid)
                .set(map, SetOptions.merge())
                .addOnSuccessListener { toast(parent, "Endereço salvo!") }
                .addOnFailureListener { e -> toast(parent, "Erro: ${e.message}") }
        }
        parent.addView(v)
    }

    private fun bindContato(inflater: LayoutInflater, parent: ViewGroup) {
        val v = inflater.inflate(R.layout.section_contato, parent, false)
        val nome = v.findViewById<EditText>(R.id.etContatoNome)
        val fone = v.findViewById<EditText>(R.id.etContatoFone)
        val btn = v.findViewById<Button>(R.id.btnSalvarContato)

        val eNome = str("emergency.nome")
        val eFone = str("emergency.telefone")

        if (eNome.isNotEmpty()) nome.setText(eNome)
        if (eFone.isNotEmpty()) fone.setText(eFone)

        // CHANGED: travas individuais
        lockIfHasText(nome)
        lockIfHasText(fone)

        btn.setOnClickListener {
            val uid = uidProvider()
            val map = mapOf(
                "emergency.nome" to nome.text.toString().trim(),
                "emergency.telefone" to fone.text.toString().filter { it.isDigit() }
            )
            Firebase.firestore.collection(collectionName).document(uid)
                .set(map, SetOptions.merge())
                .addOnSuccessListener { toast(parent, "Contato salvo!") }
                .addOnFailureListener { e -> toast(parent, "Erro: ${e.message}") }
        }
        parent.addView(v)
    }

    // ----------------- estado do card -----------------
    private fun isSectionComplete(type: SectionType): Boolean = when (type) {
        SectionType.DOCUMENTOS -> str("docs.cnhNumber").isNotBlank()
        SectionType.VEICULO    -> str("vehicle.marca").isNotBlank() && str("vehicle.placa").isNotBlank()
        SectionType.ENDERECO   -> str("address.cep").isNotBlank() &&
                str("address.cidade").isNotBlank() &&
                str("address.uf").isNotBlank()
        SectionType.CONTATO    -> str("emergency.nome").isNotBlank() &&
                str("emergency.telefone").isNotBlank()
        SectionType.FILHOS     -> childrenList().isNotEmpty()             // <-- NOVO (>= 1 filho)
    }

    private fun setCardDone(card: MaterialCardView, done: Boolean) {
        if (done) {
            card.alpha = 1f
            card.strokeColor = 0xFFE0E0E0.toInt()
            card.setCardBackgroundColor(ColorStateList.valueOf(0xFFF2F2F2.toInt())) // cinza claro
        } else {
            card.setCardBackgroundColor(ColorStateList.valueOf(0xFFFFFFFF.toInt()))
            card.strokeColor = 0xFFE0E0E0.toInt()
        }
    }

    // ----------------- utilidades visuais -----------------
    private fun lockIfHasText(et: EditText) {
        if (et.text?.isNotEmpty() == true) lock(et)
    }

    private fun lock(et: EditText) {
        et.isEnabled = false
        et.alpha = 0.6f
    }

    private fun setEnabledRecursively(view: View?, enabled: Boolean) {
        view ?: return
        view.isEnabled = enabled
        if (view is ViewGroup) view.children.forEach { setEnabledRecursively(it, enabled) }
    }

    private fun toast(parent: ViewGroup, msg: String) =
        Toast.makeText(parent.context, msg, Toast.LENGTH_SHORT).show()
}
