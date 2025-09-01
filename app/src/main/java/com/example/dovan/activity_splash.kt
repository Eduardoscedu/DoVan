package com.example.dovan

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.dovan.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animação no logo (fade-in)
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.logo.startAnimation(fadeIn)

        // Configura barra de progresso
        binding.progress.isIndeterminate = false
        binding.progress.progress = 0

        val anim = ObjectAnimator.ofInt(binding.progress, "progress", 0, 100).apply {
            duration = 6000L // 6 segundos
            interpolator = DecelerateInterpolator()
        }

        // Atualiza o texto de porcentagem junto com o progresso
        anim.addUpdateListener { valueAnimator ->
            val progressValue = valueAnimator.animatedValue as Int
            binding.progressText.text = "$progressValue%"
        }

        anim.start()

        // Vai para MainActivity após 6s
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 6000L)
    }
}
