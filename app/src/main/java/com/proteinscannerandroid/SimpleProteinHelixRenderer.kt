package com.proteinscannerandroid

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class SimpleProteinHelixRenderer : GLSurfaceView.Renderer {
    
    companion object {
        private const val TAG = "SimpleHelixRenderer"
        private const val COORDS_PER_VERTEX = 3
    }
    
    private var shaderProgram: Int = 0
    private val mVMatrix = FloatArray(16)
    private val mPMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)
    private var angle = 0f
    
    // Simple helix vertices
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer
    
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        try {
            Log.d(TAG, "Starting simple helix renderer")
            
            // Set background to transparent
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            
            // Create simple helix geometry
            createSimpleHelix()
            
            // Create simple shader
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, simpleFragmentShaderCode)
            
            shaderProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(shaderProgram, vertexShader)
            GLES20.glAttachShader(shaderProgram, fragmentShader)
            GLES20.glLinkProgram(shaderProgram)
            
            Log.d(TAG, "Simple helix renderer initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing renderer: ${e.message}", e)
        }
    }
    
    override fun onDrawFrame(unused: GL10) {
        try {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            
            // Update rotation
            angle += 2.0f
            
            // Set up camera
            Matrix.setLookAtM(mVMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
            
            // Apply rotation
            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.rotateM(modelMatrix, 0, angle, 0f, 1f, 0f)
            
            // Calculate MVP matrix
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mMVPMatrix, 0, mPMatrix, 0, tempMatrix, 0)
            
            // Draw simple helix
            drawSimpleHelix()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in draw frame: ${e.message}", e)
        }
    }
    
    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(mPMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 10f)
    }
    
    private fun createSimpleHelix() {
        val points = 50
        val vertices = mutableListOf<Float>()
        val colors = mutableListOf<Float>()
        
        // Create simple helix curve
        for (i in 0 until points) {
            val t = i.toFloat() / points
            val angle = t * 4 * PI.toFloat()
            val y = (t - 0.5f) * 2f
            
            // Left strand
            val x1 = 0.5f * cos(angle)
            val z1 = 0.5f * sin(angle)
            vertices.addAll(listOf(x1, y, z1))
            colors.addAll(listOf(0.2f, 0.5f, 1.0f, 1.0f)) // Blue
            
            // Right strand
            val x2 = 0.5f * cos(angle + PI.toFloat())
            val z2 = 0.5f * sin(angle + PI.toFloat())
            vertices.addAll(listOf(x2, y, z2))
            colors.addAll(listOf(0.5f, 0.2f, 1.0f, 1.0f)) // Purple
        }
        
        // Convert to buffers
        vertexBuffer = createFloatBuffer(vertices.toFloatArray())
        colorBuffer = createFloatBuffer(colors.toFloatArray())
    }
    
    private fun drawSimpleHelix() {
        GLES20.glUseProgram(shaderProgram)
        
        // Get handles
        val positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        val colorHandle = GLES20.glGetAttribLocation(shaderProgram, "vColor")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
        
        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        
        // Pass MVP matrix
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        
        // Draw as line strip
        GLES20.glLineWidth(5.0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, 100)
        
        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
    
    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data)
        buffer.position(0)
        return buffer
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }
    
    private val simpleVertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec4 vColor;
        varying vec4 fColor;
        
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            fColor = vColor;
        }
    """.trimIndent()
    
    private val simpleFragmentShaderCode = """
        precision mediump float;
        varying vec4 fColor;
        
        void main() {
            gl_FragColor = fColor;
        }
    """.trimIndent()
    
    fun setRotationSpeed(speedX: Float, speedY: Float) {
        // Simple implementation for now
    }
    
    fun onPause() {
        // Pause animation
    }
    
    fun onResume() {
        // Resume animation
    }
}