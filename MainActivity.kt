package com.example.claritycamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var executor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var originalBitmap: Bitmap? = null
    private var enhancedBitmap: Bitmap? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else status.text = "Camera permission is required."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = Executors.newSingleThreadExecutor()
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 16, 24))
        }
        root.addView(TextView(this).apply {
            text = "CLARITY AI CAMERA"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 20, 8, 6)
        })
        root.addView(TextView(this).apply {
            text = "Capture  •  Enhance  •  Save HD"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 12)
        })
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            text = "Starting camera…"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 12, 10, 8)
        }
        root.addView(status)
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 18)
        }
        row.addView(Button(this).apply {
            text = "TAKE PHOTO"
            setOnClickListener { takePhoto() }
        })
        row.addView(Button(this).apply {
            text = "COMPARE"
            setOnClickListener { comparePhotos() }
        })
        row.addView(Button(this).apply {
            text = "SAVE HD"
            setOnClickListener { saveEnhanced() }
        })
        root.addView(row)
        setContentView(root)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                status.text = "Camera ready. Take a photo to enhance it."
            } catch (e: Exception) {
                status.text = "Camera error: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        status.text = "Taking photo and enhancing…"
        val file = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap == null) {
                        runOnUiThread { status.text = "Could not read captured photo." }
                        return
                    }
                    val improved = enhanceImage(bitmap)
                    runOnUiThread {
                        originalBitmap = bitmap
                        enhancedBitmap = improved
                        status.text = "Photo enhanced. Tap COMPARE or SAVE HD."
                        showPhotoDialog(improved, "Enhanced photo")
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread { status.text = "Capture failed: ${exception.message}" }
                }
            }
        )
    }

    private fun enhanceImage(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.clone()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val c = pixels[i]
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                val ar = (Color.red(pixels[i-1]) + Color.red(pixels[i+1]) +
                    Color.red(pixels[i-w]) + Color.red(pixels[i+w])) / 4
                val ag = (Color.green(pixels[i-1]) + Color.green(pixels[i+1]) +
                    Color.green(pixels[i-w]) + Color.green(pixels[i+w])) / 4
                val ab = (Color.blue(pixels[i-1]) + Color.blue(pixels[i+1]) +
                    Color.blue(pixels[i-w]) + Color.blue(pixels[i+w])) / 4
                fun tune(v: Int, blur: Int): Int {
                    val sharp = v + ((v - blur) * 0.45f).toInt()
                    return min(255, max(0, (((sharp - 128) * 1.06f) + 128).toInt()))
                }
                out[i] = Color.argb(Color.alpha(c), tune(r, ar), tune(g, ag), tune(b, ab))
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    private fun comparePhotos() {
        val original = originalBitmap
        val enhanced = enhancedBitmap
        if (original == null || enhanced == null) {
            Toast.makeText(this, "Pehle photo lo!", Toast.LENGTH_SHORT).show()
            return
        }
        val image = ImageView(this).apply {
            setImageBitmap(original)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        AlertDialog.Builder(this)
            .setTitle("Original photo")
            .setView(image)
            .setPositiveButton("Show enhanced") { _, _ -> showPhotoDialog(enhanced, "Enhanced photo") }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPhotoDialog(bitmap: Bitmap, title: String) {
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        AlertDialog.Builder(this).setTitle(title).setView(image).setPositiveButton("OK", null).show()
    }

    private fun saveEnhanced() {
        val bitmap = enhancedBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Pehle photo lo!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val name = "Clarity_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ClarityAICamera")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create gallery file")
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 97, it) }
            status.text = "Saved to Pictures/ClarityAICamera"
            Toast.makeText(this, "Enhanced photo saved to gallery!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
