package com.proteinscannerandroid

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.proteinscannerandroid.databinding.ActivityIngredientCameraBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class IngredientCameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIngredientCameraBinding
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var maxZoomRatio: Float = 1f
    private var minZoomRatio: Float = 1f

    companion object {
        private const val TAG = "IngredientCamera"

        // Scanning frame size as percentage of visible preview (centered)
        private const val FRAME_WIDTH_PERCENT = 0.85f   // 85% of width
        private const val FRAME_HEIGHT_PERCENT = 0.30f  // 30% of height

        // Extra padding around crop to ensure we don't cut off text (50% extra)
        private const val CROP_PADDING_PERCENT = 0.50f

        // Set to false to disable cropping for debugging
        private const val ENABLE_CROPPING = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIngredientCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ML Kit Text Recognition
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize pinch-to-zoom gesture detector
        scaleGestureDetector = ScaleGestureDetector(this, ZoomGestureListener())

        setupUI()
        setupTouchControls()
        setupScanningFrame()
        startCamera()
    }

    /**
     * Sets the scanning frame size based on FRAME_WIDTH_PERCENT and FRAME_HEIGHT_PERCENT.
     * This ensures the visual frame matches the crop region exactly.
     */
    private fun setupScanningFrame() {
        binding.scanningFrame.post {
            // Get the preview view dimensions
            val previewWidth = binding.previewView.width
            val previewHeight = binding.previewView.height

            if (previewWidth > 0 && previewHeight > 0) {
                // Calculate frame size using the same percentages as cropping
                val frameWidth = (previewWidth * FRAME_WIDTH_PERCENT).toInt()
                val frameHeight = (previewHeight * FRAME_HEIGHT_PERCENT).toInt()

                // Update frame size
                val params = binding.scanningFrame.layoutParams
                params.width = frameWidth
                params.height = frameHeight
                binding.scanningFrame.layoutParams = params

                Log.d(TAG, "Scanning frame set to: ${frameWidth}x${frameHeight} " +
                        "(${FRAME_WIDTH_PERCENT * 100}% x ${FRAME_HEIGHT_PERCENT * 100}% of preview)")
            }
        }
    }

    private fun setupUI() {
        binding.btnCapture.setOnClickListener {
            captureIngredientPhoto()
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.btnFlashlight.setOnClickListener {
            toggleFlashlight()
        }

        // Setup zoom slider
        binding.zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val linearZoom = progress / 100f
                    camera?.cameraControl?.setLinearZoom(linearZoom)
                    updateZoomLevelText(linearZoom)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupTouchControls() {
        binding.previewView.setOnTouchListener { view, event ->
            // Handle pinch-to-zoom
            scaleGestureDetector.onTouchEvent(event)

            // Handle tap-to-focus
            if (event.action == MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress) {
                handleTapToFocus(event.x, event.y)
            }

            true
        }
    }

    private fun handleTapToFocus(x: Float, y: Float) {
        val camera = camera ?: return

        val meteringPointFactory = binding.previewView.meteringPointFactory
        val focusPoint = meteringPointFactory.createPoint(x, y)

        val focusAction = FocusMeteringAction.Builder(focusPoint, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        camera.cameraControl.startFocusAndMetering(focusAction)
        showFocusRing(x, y)
    }

    private fun showFocusRing(x: Float, y: Float) {
        val focusRing = binding.focusRing

        // Position the focus ring at tap location
        focusRing.x = x - focusRing.width / 2
        focusRing.y = y - focusRing.height / 2

        // Reset state
        focusRing.scaleX = 1.5f
        focusRing.scaleY = 1.5f
        focusRing.alpha = 1f
        focusRing.visibility = View.VISIBLE

        // Animate: scale down and fade out
        val scaleX = ObjectAnimator.ofFloat(focusRing, "scaleX", 1.5f, 1f)
        val scaleY = ObjectAnimator.ofFloat(focusRing, "scaleY", 1.5f, 1f)
        val fadeOut = ObjectAnimator.ofFloat(focusRing, "alpha", 1f, 0f)
        fadeOut.startDelay = 500

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, fadeOut)
        animatorSet.duration = 300

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                focusRing.visibility = View.GONE
            }
        })

        animatorSet.start()
    }

    private fun updateZoomLevelText(linearZoom: Float) {
        // Convert linear zoom (0-1) to actual zoom ratio for display
        val zoomRatio = minZoomRatio + (maxZoomRatio - minZoomRatio) * linearZoom
        binding.tvZoomLevel.text = String.format("%.1fx", zoomRatio)
    }

    private fun updateZoomSlider(linearZoom: Float) {
        binding.zoomSlider.progress = (linearZoom * 100).toInt()
        updateZoomLevelText(linearZoom)
    }

    // Inner class for pinch-to-zoom gesture handling
    private inner class ZoomGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val camera = camera ?: return false
            val zoomState = camera.cameraInfo.zoomState.value ?: return false

            val currentZoomRatio = zoomState.zoomRatio
            val newZoomRatio = currentZoomRatio * detector.scaleFactor

            // Clamp to valid range
            val clampedZoomRatio = newZoomRatio.coerceIn(minZoomRatio, maxZoomRatio)
            camera.cameraControl.setZoomRatio(clampedZoomRatio)

            // Update slider to match
            val linearZoom = (clampedZoomRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio)
            runOnUiThread { updateZoomSlider(linearZoom) }

            return true
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // Image capture - optimized for high quality text recognition
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetResolution(Size(4032, 3024)) // High resolution for better OCR
                .build()

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                // Get zoom limits from camera
                camera?.cameraInfo?.zoomState?.observe(this) { zoomState ->
                    minZoomRatio = zoomState.minZoomRatio
                    maxZoomRatio = zoomState.maxZoomRatio
                    Log.d(TAG, "Zoom range: ${minZoomRatio}x - ${maxZoomRatio}x")
                }

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                showError("Camera initialization failed")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureIngredientPhoto() {
        val imageCapture = imageCapture ?: return

        showProgress(true)

        // Capture the image
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            createTempImageFile()
        ).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    showProgress(false)
                    showError("Photo capture failed")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        processImageWithOCR(uri.path!!)
                    }
                }
            }
        )
    }

    private fun createTempImageFile(): java.io.File {
        return java.io.File(cacheDir, "ingredient_scan_${System.currentTimeMillis()}.jpg")
    }

    private fun processImageWithOCR(imagePath: String) {
        try {
            val fullBitmap = android.graphics.BitmapFactory.decodeFile(imagePath)

            // Crop to scanning frame region (can be disabled for debugging)
            val bitmapToAnalyze = if (ENABLE_CROPPING) {
                val cropped = cropToScanningFrame(fullBitmap)
                Log.d(TAG, "CROPPING ENABLED - Original: ${fullBitmap.width}x${fullBitmap.height}, Cropped: ${cropped.width}x${cropped.height}")
                cropped
            } else {
                Log.d(TAG, "CROPPING DISABLED - Using full image: ${fullBitmap.width}x${fullBitmap.height}")
                fullBitmap
            }

            val image = InputImage.fromBitmap(bitmapToAnalyze, 0)

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    showProgress(false)

                    val extractedText = visionText.text
                    Log.d(TAG, "Extracted text: $extractedText")

                    if (extractedText.isNotBlank()) {
                        // Process with existing protein analysis algorithm
                        analyzeExtractedIngredients(extractedText)
                    } else {
                        showError("No text detected. Try positioning the ingredients in the frame.")
                    }

                    // Clean up bitmaps
                    if (ENABLE_CROPPING && bitmapToAnalyze != fullBitmap) {
                        fullBitmap.recycle()
                    }
                }
                .addOnFailureListener { e ->
                    showProgress(false)
                    Log.e(TAG, "Text recognition failed", e)
                    showError("Text recognition failed. Please try again.")
                    if (ENABLE_CROPPING && bitmapToAnalyze != fullBitmap) {
                        fullBitmap.recycle()
                        bitmapToAnalyze.recycle()
                    }
                }
        } catch (e: Exception) {
            showProgress(false)
            Log.e(TAG, "Image processing failed", e)
            showError("Image processing failed")
        }
    }

    /**
     * Crops the captured image to match the visual scanning frame.
     *
     * This accounts for the aspect ratio difference between:
     * - The captured image (typically 4:3)
     * - The PreviewView (matches phone screen, typically taller)
     *
     * The PreviewView shows only a portion of the captured image (FILL_CENTER scaling).
     * We first calculate what portion is visible, then apply the frame percentages to that.
     */
    private fun cropToScanningFrame(bitmap: Bitmap): Bitmap {
        try {
            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()
            val viewWidth = binding.previewView.width.toFloat()
            val viewHeight = binding.previewView.height.toFloat()

            if (viewWidth == 0f || viewHeight == 0f) {
                Log.w(TAG, "Preview dimensions not available, returning full bitmap")
                return bitmap
            }

            // Calculate aspect ratios
            val imageAspect = imageWidth / imageHeight
            val viewAspect = viewWidth / viewHeight

            // Calculate what portion of the image is visible in the PreviewView (FILL_CENTER)
            val visibleWidthPercent: Float
            val visibleHeightPercent: Float

            if (imageAspect > viewAspect) {
                // Image is wider than view - sides are cropped
                visibleHeightPercent = 1.0f
                visibleWidthPercent = viewAspect / imageAspect
            } else {
                // Image is taller than view - top/bottom are cropped
                visibleWidthPercent = 1.0f
                visibleHeightPercent = imageAspect / viewAspect
            }

            Log.d(TAG, "=== CROP CALCULATION ===")
            Log.d(TAG, "Image: ${imageWidth.toInt()}x${imageHeight.toInt()} (aspect: ${"%.2f".format(imageAspect)})")
            Log.d(TAG, "Preview: ${viewWidth.toInt()}x${viewHeight.toInt()} (aspect: ${"%.2f".format(viewAspect)})")
            Log.d(TAG, "Visible portion of image: ${"%.1f".format(visibleWidthPercent * 100)}% width, ${"%.1f".format(visibleHeightPercent * 100)}% height")

            // The frame percentages are relative to the VISIBLE area, not the full image
            // So we need to apply them to the visible portion only
            // Add padding to ensure we don't cut off text at the edges
            val cropWidthPercent = visibleWidthPercent * (FRAME_WIDTH_PERCENT + CROP_PADDING_PERCENT)
            val cropHeightPercent = visibleHeightPercent * (FRAME_HEIGHT_PERCENT + CROP_PADDING_PERCENT)

            // Clamp to maximum of 100%
            val finalWidthPercent = cropWidthPercent.coerceAtMost(1.0f)
            val finalHeightPercent = cropHeightPercent.coerceAtMost(1.0f)

            val cropWidth = (imageWidth * finalWidthPercent).toInt()
            val cropHeight = (imageHeight * finalHeightPercent).toInt()

            // Center the crop region
            val cropLeft = ((imageWidth - cropWidth) / 2).toInt()
            val cropTop = ((imageHeight - cropHeight) / 2).toInt()

            Log.d(TAG, "Frame: ${FRAME_WIDTH_PERCENT * 100}% x ${FRAME_HEIGHT_PERCENT * 100}% of visible area (+ ${CROP_PADDING_PERCENT * 100}% padding)")
            Log.d(TAG, "Actual crop: ${"%.1f".format(finalWidthPercent * 100)}% x ${"%.1f".format(finalHeightPercent * 100)}% of image")
            Log.d(TAG, "Crop region: left=$cropLeft, top=$cropTop, size=${cropWidth}x${cropHeight}")

            return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop bitmap, returning full image", e)
            return bitmap
        }
    }

    private fun analyzeExtractedIngredients(ingredientText: String) {
        try {
            // Use existing protein analysis algorithm
            val result = ProteinDatabase.analyzeProteinQuality(ingredientText, null)

            // Navigate to results activity
            val intent = Intent(this, ResultsActivity::class.java).apply {
                putExtra("INGREDIENT_TEXT", ingredientText)
                putExtra("IS_OCR_RESULT", true)
            }
            
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            Log.e(TAG, "Protein analysis failed", e)
            showError("Failed to analyze ingredients. Please try again.")
        }
    }

    private fun toggleFlashlight() {
        camera?.let { cam ->
            cam.cameraControl.enableTorch(
                cam.cameraInfo.torchState.value == TorchState.OFF
            )
        }
    }

    private fun showProgress(show: Boolean) {
        runOnUiThread {
            binding.progressBar.visibility = if (show) 
                android.view.View.VISIBLE else android.view.View.GONE
            binding.btnCapture.isEnabled = !show
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        textRecognizer.close()
    }
}