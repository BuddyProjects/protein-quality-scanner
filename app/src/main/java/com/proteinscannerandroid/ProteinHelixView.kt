package com.proteinscannerandroid

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

class ProteinHelixView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    
    private val renderer: MolecularProteinHelixRenderer
    private var previousX: Float = 0f
    private var previousY: Float = 0f
    
    init {
        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2)
        
        // IMPORTANT: Set EGL config BEFORE setting renderer
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        
        // Set transparent background
        setZOrderOnTop(true)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        
        // Set the renderer for drawing on the GLSurfaceView
        renderer = MolecularProteinHelixRenderer()
        setRenderer(renderer)
        
        // Render the view continuously (for smooth animation)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Simple touch control for rotation
        val x: Float = e.x
        val y: Float = e.y
        
        when (e.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx: Float = x - previousX
                val dy: Float = y - previousY
                
                // Simple rotation control
                renderer.setRotationSpeed(dx * 0.01f, dy * 0.01f)
            }
        }
        
        previousX = x
        previousY = y
        return true
    }
    
    override fun onPause() {
        super.onPause()
        renderer.onPause()
    }
    
    override fun onResume() {
        super.onResume()
        renderer.onResume()
    }
}