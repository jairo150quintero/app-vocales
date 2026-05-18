package com.example.myapplication

import android.Manifest
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.ViewNode
import com.google.ar.sceneform.rendering.ViewAttachmentManager
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var sceneView: ARSceneView
    private lateinit var vowelInstruction: TextView
    private lateinit var vowelIcon: TextView
    private lateinit var nextButton: Button
    private lateinit var backHomeButton: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var loadingBar: ProgressBar
    private lateinit var viewAttachmentManager: ViewAttachmentManager
    
    private val vowels = listOf("A", "E", "I", "O", "U")
    private val vowelColors = listOf(
        R.color.vowel_a_color,
        R.color.vowel_e_color,
        R.color.vowel_i_color,
        R.color.vowel_o_color,
        R.color.vowel_u_color,
    )
    
    private var targetVowelIndex = 0
    private var isSearching = true
    private var mediaPlayer: MediaPlayer? = null
    
    private var lastErrorTime: Long = 0
    private val errorCooldown: Long = 4000 
    private var missionStartTime: Long = 0
    private val gracePeriod: Long = 8000

    private var currentModelNode: ModelNode? = null
    private var currentImageNode: AugmentedImageNode? = null
    private var danceAnimator: ValueAnimator? = null
    private var lastTouchX: Float = 0f

    private val markerBitmaps = mutableMapOf<String, Bitmap>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startAppProcess() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sceneView = findViewById(R.id.sceneView)
        vowelInstruction = findViewById(R.id.vowelInstruction)
        vowelIcon = findViewById(R.id.vowelIcon)
        nextButton = findViewById(R.id.nextButton)
        backHomeButton = findViewById(R.id.backHomeButton)
        loadingBar = findViewById(R.id.loadingBar)
        
        sceneView.lifecycle = lifecycle
        viewAttachmentManager = ViewAttachmentManager(this, sceneView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startAppProcess()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        nextButton.setOnClickListener { nextMission() }
        backHomeButton.setOnClickListener { finish() }

        sceneView.setOnTouchListener { _, event ->
            if (currentModelNode != null) {
                danceAnimator?.pause()
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> lastTouchX = event.x
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.x - lastTouchX
                        val rotationY = currentModelNode!!.rotation.y + deltaX * 0.5f
                        currentModelNode!!.rotation = io.github.sceneview.math.Rotation(0f, rotationY, 0f)
                        lastTouchX = event.x
                    }
                }
                true
            } else false
        }
    }

    private fun startAppProcess() {
        loadingBar.visibility = View.VISIBLE
        thread {
            preloadMarkers()
            Handler(Looper.getMainLooper()).post {
                setupGame()
                setupAR()
                loadingBar.visibility = View.GONE
            }
        }
    }

    private fun preloadMarkers() {
        vowels.forEach { v ->
            try {
                val inputStream = assets.open("markers/${v.lowercase()}.jpg")
                BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
                    markerBitmaps[v] = bitmap
                }
            } catch (e: Exception) { Log.e("VocalesRA", "Error cargando $v", e) }
        }
    }

    private fun setupGame() {
        targetVowelIndex = vowels.indices.random()
        updateUI()
        Handler(Looper.getMainLooper()).postDelayed({
            playVowelSound()
            missionStartTime = System.currentTimeMillis()
        }, 1500)
    }

    private fun setupAR() {
        sceneView.apply {
            planeRenderer.isVisible = false
            
            onSessionCreated = { session ->
                val config = Config(session)
                config.focusMode = Config.FocusMode.AUTO
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                
                val database = AugmentedImageDatabase(session)
                var count = 0
                markerBitmaps.forEach { (name, bitmap) ->
                    try {
                        database.addImage(name, bitmap)
                        count++
                    } catch (e: Exception) { Log.e("VocalesRA", "Fallo $name", e) }
                }
                config.augmentedImageDatabase = database
                session.configure(config)
                
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Base de datos: $count imágenes", Toast.LENGTH_SHORT).show()
                }
            }

            onSessionUpdated = { _, frame ->
                if (isSearching) {
                    // Escaneo exhaustivo de todas las imágenes trackeables
                    val images = frame.getUpdatedTrackables(AugmentedImage::class.java)
                    for (image in images) {
                        if (image.trackingState == TrackingState.TRACKING) {
                            if (image.name == vowels[targetVowelIndex]) {
                                checkMatch(image)
                                break
                            } else {
                                val timeInMission = System.currentTimeMillis() - missionStartTime
                                if (timeInMission > gracePeriod) playErrorSound()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkMatch(image: AugmentedImage) {
        isSearching = false
        runOnUiThread {
            Toast.makeText(this, "¡ENCONTRADA!", Toast.LENGTH_SHORT).show()
            showSuccess(image)
        }
    }

    private fun showSuccess(image: AugmentedImage) {
        val vowel = vowels[targetVowelIndex]
        val color = ContextCompat.getColor(this, vowelColors[targetVowelIndex])
        
        val imageNode = AugmentedImageNode(sceneView.engine, image)
        currentImageNode = imageNode
        
        try {
            val fileName = if (vowel == "A") "vocales_A.glb" else "vocal_$vowel.glb"
            sceneView.modelLoader.createModelInstance("models/$fileName")?.let { instance ->
                val modelNode = ModelNode(instance).apply {
                    scale = io.github.sceneview.math.Scale(0.08f) 
                    rotation = io.github.sceneview.math.Rotation(0f, 0f, 0f)
                    position = io.github.sceneview.math.Position(0f, 0f, 0.15f)
                    
                    if (vowel == "A") {
                        isTouchable = true
                        onSingleTapConfirmed = { _ ->
                            playVideoAsset()
                            true
                        }
                    }
                }
                currentModelNode = modelNode
                imageNode.addChildNode(modelNode)
                startDancing(modelNode)
            }
        } catch (e: Exception) { Log.e("VocalesRA", "Error modelo", e) }
        
        val viewNode = ViewNode(sceneView.engine, sceneView.modelLoader, viewAttachmentManager).apply {
            loadView(this@MainActivity, R.layout.vowel_card) { _, view ->
                val textView = view.findViewById<TextView>(R.id.vowelText)
                textView.text = if (vowel == "A") "¡TÓCAME!" else getString(R.string.success_message)
                textView.setBackgroundColor(color)
            }
            position = io.github.sceneview.math.Position(0f, 0.25f, 0.05f)
        }
        
        imageNode.addChildNode(viewNode)
        sceneView.addChildNode(imageNode)
        playFeedbackSound(R.raw.exito_voz)
    }

    private fun playVideoAsset() {
        danceAnimator?.pause()
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val videoView = VideoView(this)
        dialog.setContentView(videoView)
        val videoPath = "android.resource://$packageName/${R.raw.agua}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setOnCompletionListener {
            dialog.dismiss()
            danceAnimator?.resume()
        }
        dialog.show()
        videoView.start()
    }

    private fun startDancing(node: ModelNode) {
        danceAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 10000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val rotationY = animation.animatedValue as Float
                node.rotation = io.github.sceneview.math.Rotation(0f, rotationY, 0f)
                val bobbing = (kotlin.math.sin(rotationY * Math.PI / 180 * 2) * 0.02f).toFloat()
                node.position = io.github.sceneview.math.Position(0f, bobbing, 0.15f)
            }
            start()
        }
    }

    private fun nextMission() {
        danceAnimator?.cancel()
        currentImageNode?.let { sceneView.removeChildNode(it) }
        currentImageNode = null
        currentModelNode = null
        
        var newIndex: Int
        do { newIndex = vowels.indices.random() } while (newIndex == targetVowelIndex)
        
        targetVowelIndex = newIndex
        isSearching = true
        missionStartTime = System.currentTimeMillis()
        updateUI()
        playVowelSound()
    }

    private fun updateUI() {
        val vowel = vowels[targetVowelIndex]
        val color = ContextCompat.getColor(this, vowelColors[targetVowelIndex])
        vowelIcon.text = vowel
        vowelIcon.background.setTint(color)
        vowelInstruction.text = getString(R.string.instruction_step, vowel)
    }

    private fun playVowelSound() {
        val resourceName = "vocal_${vowels[targetVowelIndex].lowercase()}"
        val resId = resources.getIdentifier(resourceName, "raw", packageName)
        if (resId != 0) playFeedbackSound(resId)
    }

    private fun playErrorSound() {
        if (System.currentTimeMillis() - lastErrorTime > errorCooldown) {
            lastErrorTime = System.currentTimeMillis()
            playFeedbackSound(R.raw.error_casi)
        }
    }

    private fun playFeedbackSound(resId: Int) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onResume() {
        super.onResume()
        viewAttachmentManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewAttachmentManager.onPause()
        danceAnimator?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        markerBitmaps.values.forEach { it.recycle() }
    }
}
