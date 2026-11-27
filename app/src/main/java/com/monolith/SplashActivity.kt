package com.monolith

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class SplashActivity : AppCompatActivity() {

    private val installerExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({

            installerExecutor.execute {

                val installer = BuiltInWallpaperInstaller(this)

                try {
                    installer.install()
                } catch (error: Exception) {
                    Log.e(
                        "MonolithWallpapers",
                        "Built-in wallpaper installation failed",
                        error
                    )
                } finally {
                    installer.close()
                    installerExecutor.shutdown()
                }

                runOnUiThread {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                    )
                    finish()
                }
            }

        }, 2000)
    }
}
