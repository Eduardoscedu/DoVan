package com.example.dovan

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity

class ChooseRoleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_role)

        // animação de entrada da tela
        findViewById<View>(R.id.root).startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.slide_in_up)
        )

        val cardDriver  = findViewById<View>(R.id.cardDriver)
        val cardParents = findViewById<View>(R.id.cardParents)

        fun pulse(v: View) {
            val a = AlphaAnimation(0.85f, 1f).apply { duration = 120 }
            v.startAnimation(a)
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        cardDriver.setOnClickListener {
            pulse(it)
            startActivity(Intent(this, RegisterDriverActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        cardParents.setOnClickListener {
            pulse(it)
            startActivity(Intent(this, RegisterActivityFamily::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // botão "voltar"
        findViewById<View>(R.id.btnClose).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }
}
