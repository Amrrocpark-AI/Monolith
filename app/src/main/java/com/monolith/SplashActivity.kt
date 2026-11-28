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

                try {
                    WallpaperUserIdentityRepository(applicationContext)
                        .getOrCreateCurrentUser()
                } catch (error: Exception) {
                    Log.e(
                        "MonolithIdentity",
                        "Local user identity initialization failed",
                        error
                    )
                }

                val installer = BuiltInWallpaperInstaller(this)

                try {
                    installer.install()
                    installer.install()
                                } catch (error: Exception) {
                    Log.e(
                        "MonolithWallpapers",
                        "Built-in wallpaper installation failed",
                        error
                    )

                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@SplashActivity,
                            "Wallpaper installation failed: ${error.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
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
