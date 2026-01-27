package com.proteinscannerandroid

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class ProteinHelixRenderer : GLSurfaceView.Renderer {
    
    companion object {
        private const val TAG = "ProteinHelixRenderer"
    }
    
    private var helixProgram: Int = 0
    private var sphereProgram: Int = 0
    private var laserProgram: Int = 0
    
    private val mVMatrix = FloatArray(16)
    private val mPMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private val mRotationMatrix = FloatArray(16)
    
    // 3D Helix geometry
    private lateinit var leftHelixVertices: FloatArray
    private lateinit var rightHelixVertices: FloatArray
    private lateinit var helixColors: FloatArray
    private lateinit var helixNormals: FloatArray
    
    // Amino acid spheres
    private lateinit var sphereVertices: FloatArray
    private lateinit var sphereIndices: ShortArray
    private lateinit var aminoAcidPositions: FloatArray
    private lateinit var aminoAcidColors: FloatArray
    
    // Scanning laser
    private lateinit var laserVertices: FloatArray
    private var scanPosition = 0f
    
    // Animation variables
    private var angle = 0f
    private var scanDirection = 1f
    private var rotationSpeedX = 1f
    private var rotationSpeedY = 0f
    private var isAnimating = true
    
    // Buffer IDs
    private val buffers = IntArray(10)
    
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        try {
            Log.d(TAG, "onSurfaceCreated started")
            
            // Set the background to transparent
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            
            Log.d(TAG, "OpenGL state configured")
            
            // Generate 3D geometry
            generateHelixGeometry()
            Log.d(TAG, "Helix geometry generated")
            
            generateSphereGeometry()
            Log.d(TAG, "Sphere geometry generated")
            
            generateLaserGeometry()
            Log.d(TAG, "Laser geometry generated")
            
            // Create shader programs
            helixProgram = createShaderProgram(vertexShaderCode, fragmentShaderCode)
            Log.d(TAG, "Helix shader program created: $helixProgram")
            
            sphereProgram = createShaderProgram(sphereVertexShaderCode, sphereFragmentShaderCode)
            Log.d(TAG, "Sphere shader program created: $sphereProgram")
            
            laserProgram = createShaderProgram(laserVertexShaderCode, laserFragmentShaderCode)
            Log.d(TAG, "Laser shader program created: $laserProgram")
            
            // Create buffers
            GLES20.glGenBuffers(buffers.size, buffers, 0)
            uploadGeometryToBuffers()
            Log.d(TAG, "Buffers created and uploaded")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onSurfaceCreated: ${e.message}", e)
            throw e
        }
    }
    
    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Update animation
        if (isAnimating) {
            angle += rotationSpeedX
            scanPosition += scanDirection * 0.02f
            if (scanPosition > 1.0f || scanPosition < 0.0f) {
                scanDirection *= -1
            }
        }
        
        // Set up camera
        Matrix.setLookAtM(mVMatrix, 0,
            0f, 0f, 8f,  // eye
            0f, 0f, 0f,  // center
            0f, 1f, 0f   // up
        )
        
        // Create rotation
        Matrix.setIdentityM(mModelMatrix, 0)
        Matrix.setRotateM(mRotationMatrix, 0, angle, 0f, 1f, 0f)
        Matrix.multiplyMM(mModelMatrix, 0, mModelMatrix, 0, mRotationMatrix, 0)
        
        // Calculate MVP matrix
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mPMatrix, 0, tempMatrix, 0)
        
        // Draw helix strands
        drawHelix()
        
        // Draw amino acid spheres
        drawAminoAcids()
        
        // Draw scanning laser
        drawScanningLaser()
    }
    
    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(mPMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 15f)
    }
    
    private fun generateHelixGeometry() {
        val segments = 100
        val helixHeight = 4.0f
        val helixRadius = 1.5f
        val helixTurns = 2.0f
        
        leftHelixVertices = FloatArray(segments * 3)
        rightHelixVertices = FloatArray(segments * 3)
        helixColors = FloatArray(segments * 4 * 2) // Two strands
        helixNormals = FloatArray(segments * 3 * 2)
        
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val y = (t - 0.5f) * helixHeight
            val theta = t * helixTurns * 2 * PI.toFloat()
            
            // Left strand
            val leftX = helixRadius * cos(theta)
            val leftZ = helixRadius * sin(theta)
            
            leftHelixVertices[i * 3] = leftX
            leftHelixVertices[i * 3 + 1] = y
            leftHelixVertices[i * 3 + 2] = leftZ
            
            // Right strand (180 degrees offset)
            val rightX = helixRadius * cos(theta + PI.toFloat())
            val rightZ = helixRadius * sin(theta + PI.toFloat())
            
            rightHelixVertices[i * 3] = rightX
            rightHelixVertices[i * 3 + 1] = y
            rightHelixVertices[i * 3 + 2] = rightZ
            
            // Colors (blue gradient for strands)
            val colorIntensity = 0.3f + 0.7f * (sin(theta * 2) * 0.5f + 0.5f)
            
            // Left strand color
            helixColors[i * 4] = 0.1f * colorIntensity     // R
            helixColors[i * 4 + 1] = 0.3f * colorIntensity // G
            helixColors[i * 4 + 2] = 0.8f * colorIntensity // B
            helixColors[i * 4 + 3] = 0.9f                  // A
            
            // Right strand color
            helixColors[(segments + i) * 4] = 0.2f * colorIntensity
            helixColors[(segments + i) * 4 + 1] = 0.5f * colorIntensity
            helixColors[(segments + i) * 4 + 2] = 1.0f * colorIntensity
            helixColors[(segments + i) * 4 + 3] = 0.9f
            
            // Normals for lighting
            helixNormals[i * 3] = cos(theta)
            helixNormals[i * 3 + 1] = 0f
            helixNormals[i * 3 + 2] = sin(theta)
            
            helixNormals[(segments + i) * 3] = cos(theta + PI.toFloat())
            helixNormals[(segments + i) * 3 + 1] = 0f
            helixNormals[(segments + i) * 3 + 2] = sin(theta + PI.toFloat())
        }
    }
    
    private fun generateSphereGeometry() {
        val stacks = 12
        val slices = 16
        val radius = 0.15f
        
        val vertexCount = (stacks + 1) * (slices + 1)
        sphereVertices = FloatArray(vertexCount * 3)
        
        var index = 0
        for (i in 0..stacks) {
            val phi = PI.toFloat() * i / stacks
            for (j in 0..slices) {
                val theta = 2 * PI.toFloat() * j / slices
                
                val x = radius * sin(phi) * cos(theta)
                val y = radius * cos(phi)
                val z = radius * sin(phi) * sin(theta)
                
                sphereVertices[index * 3] = x
                sphereVertices[index * 3 + 1] = y
                sphereVertices[index * 3 + 2] = z
                index++
            }
        }
        
        // Generate indices for triangle strips
        val indexCount = stacks * slices * 6
        sphereIndices = ShortArray(indexCount)
        
        index = 0
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val first = (i * (slices + 1) + j).toShort()
                val second = (first + slices + 1).toShort()
                
                sphereIndices[index++] = first
                sphereIndices[index++] = second
                sphereIndices[index++] = (first + 1).toShort()
                
                sphereIndices[index++] = second
                sphereIndices[index++] = (second + 1).toShort()
                sphereIndices[index++] = (first + 1).toShort()
            }
        }
        
        // Amino acid positions along the helix
        aminoAcidPositions = floatArrayOf(
            // Left strand positions
            1.2f, -1.5f, 0.5f,   // Leucine (green)
            -0.8f, -0.5f, 1.3f,  // Valine (orange)
            1.0f, 0.5f, -1.1f,   // Phenylalanine (purple)
            -1.3f, 1.5f, 0.7f,   // Glycine (blue)
            
            // Right strand positions
            -1.2f, -1.0f, -0.5f, // Leucine
            0.8f, 0.0f, -1.3f,   // Valine
            -1.0f, 1.0f, 1.1f,   // Phenylalanine
            1.3f, 2.0f, -0.7f    // Glycine
        )
        
        // Amino acid colors (realistic biochemical colors)
        aminoAcidColors = floatArrayOf(
            // Left strand
            0.2f, 0.8f, 0.3f, 1.0f,  // Leucine (green)
            1.0f, 0.6f, 0.1f, 1.0f,  // Valine (orange)
            0.8f, 0.2f, 0.9f, 1.0f,  // Phenylalanine (purple)
            0.3f, 0.7f, 1.0f, 1.0f,  // Glycine (blue)
            
            // Right strand
            0.2f, 0.8f, 0.3f, 1.0f,  // Leucine
            1.0f, 0.6f, 0.1f, 1.0f,  // Valine
            0.8f, 0.2f, 0.9f, 1.0f,  // Phenylalanine
            0.3f, 0.7f, 1.0f, 1.0f   // Glycine
        )
    }
    
    private fun generateLaserGeometry() {
        laserVertices = floatArrayOf(
            // Scanning plane
            -3.0f, 0f, -3.0f,
            3.0f, 0f, -3.0f,
            3.0f, 0f, 3.0f,
            -3.0f, 0f, 3.0f
        )
    }
    
    private fun uploadGeometryToBuffers() {
        // Upload helix vertices
        val leftHelixBuffer = createFloatBuffer(leftHelixVertices)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, leftHelixVertices.size * 4, leftHelixBuffer, GLES20.GL_STATIC_DRAW)
        
        val rightHelixBuffer = createFloatBuffer(rightHelixVertices)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[1])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, rightHelixVertices.size * 4, rightHelixBuffer, GLES20.GL_STATIC_DRAW)
        
        val sphereVertexBuffer = createFloatBuffer(sphereVertices)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[2])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, sphereVertices.size * 4, sphereVertexBuffer, GLES20.GL_STATIC_DRAW)
        
        val sphereIndexBuffer = createShortBuffer(sphereIndices)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[3])
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereIndices.size * 2, sphereIndexBuffer, GLES20.GL_STATIC_DRAW)
        
        val laserBuffer = createFloatBuffer(laserVertices)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[4])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, laserVertices.size * 4, laserBuffer, GLES20.GL_STATIC_DRAW)
    }
    
    private fun drawHelix() {
        GLES20.glUseProgram(helixProgram)
        
        // Get shader uniforms
        val mvpMatrixHandle = GLES20.glGetUniformLocation(helixProgram, "uMVPMatrix")
        val lightPosHandle = GLES20.glGetUniformLocation(helixProgram, "uLightPos")
        
        // Set light position
        GLES20.glUniform3f(lightPosHandle, 2.0f, 2.0f, 5.0f)
        
        // Pass transformation matrix
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        
        // Draw left strand
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0])
        val positionHandle = GLES20.glGetAttribLocation(helixProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0)
        
        GLES20.glLineWidth(8.0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, leftHelixVertices.size / 3)
        
        // Draw right strand similarly...
    }
    
    private fun drawAminoAcids() {
        GLES20.glUseProgram(sphereProgram)
        
        val mvpMatrixHandle = GLES20.glGetUniformLocation(sphereProgram, "uMVPMatrix")
        val lightPosHandle = GLES20.glGetUniformLocation(sphereProgram, "uLightPos")
        val colorHandle = GLES20.glGetUniformLocation(sphereProgram, "uColor")
        
        GLES20.glUniform3f(lightPosHandle, 2.0f, 2.0f, 5.0f)
        
        // Draw each amino acid sphere
        for (i in 0 until 8) {
            val sphereMatrix = FloatArray(16)
            Matrix.setIdentityM(sphereMatrix, 0)
            
            val x = aminoAcidPositions[i * 3]
            val y = aminoAcidPositions[i * 3 + 1] + scanPosition * 0.1f * sin(angle * 0.1f + i)
            val z = aminoAcidPositions[i * 3 + 2]
            
            Matrix.translateM(sphereMatrix, 0, x, y, z)
            
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, mModelMatrix, 0, sphereMatrix, 0)
            Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, tempMatrix, 0)
            Matrix.multiplyMM(tempMatrix, 0, mPMatrix, 0, tempMatrix, 0)
            
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, tempMatrix, 0)
            
            // Set amino acid color
            val r = aminoAcidColors[i * 4]
            val g = aminoAcidColors[i * 4 + 1]
            val b = aminoAcidColors[i * 4 + 2]
            val a = aminoAcidColors[i * 4 + 3]
            GLES20.glUniform4f(colorHandle, r, g, b, a)
            
            // Draw sphere
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[2])
            val positionHandle = GLES20.glGetAttribLocation(sphereProgram, "aPosition")
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0)
            
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[3])
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIndices.size, GLES20.GL_UNSIGNED_SHORT, 0)
        }
    }
    
    private fun drawScanningLaser() {
        GLES20.glUseProgram(laserProgram)
        
        val mvpMatrixHandle = GLES20.glGetUniformLocation(laserProgram, "uMVPMatrix")
        val alphaHandle = GLES20.glGetUniformLocation(laserProgram, "uAlpha")
        
        // Animated scanning plane
        val laserMatrix = FloatArray(16)
        Matrix.setIdentityM(laserMatrix, 0)
        Matrix.translateM(laserMatrix, 0, 0f, (scanPosition - 0.5f) * 4f, 0f)
        
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, mModelMatrix, 0, laserMatrix, 0)
        Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, tempMatrix, 0)
        Matrix.multiplyMM(tempMatrix, 0, mPMatrix, 0, tempMatrix, 0)
        
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, tempMatrix, 0)
        
        // Pulsing alpha
        val alpha = 0.3f + 0.4f * sin(SystemClock.uptimeMillis() * 0.01f)
        GLES20.glUniform1f(alphaHandle, alpha)
        
        // Draw laser plane
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[4])
        val positionHandle = GLES20.glGetAttribLocation(laserProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
    }
    
    // Utility functions
    private fun createFloatBuffer(array: FloatArray): java.nio.FloatBuffer {
        val buffer = java.nio.ByteBuffer.allocateDirect(array.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(array)
        buffer.position(0)
        return buffer
    }
    
    private fun createShortBuffer(array: ShortArray): java.nio.ShortBuffer {
        val buffer = java.nio.ByteBuffer.allocateDirect(array.size * 2)
            .order(java.nio.ByteOrder.nativeOrder())
            .asShortBuffer()
        buffer.put(array)
        buffer.position(0)
        return buffer
    }
    
    private fun createShaderProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            Log.e(TAG, "Failed to create vertex shader")
            return 0
        }
        
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            Log.e(TAG, "Failed to create fragment shader")
            return 0
        }
        
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Failed to create shader program")
            return 0
        }
        
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        // Check if linking succeeded
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val error = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Could not link program: $error")
            GLES20.glDeleteProgram(program)
            return 0
        }
        
        return program
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader")
            return 0
        }
        
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        // Check if compilation succeeded
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES20.GL_TRUE) {
            val error = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Could not compile shader: $error")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    // Shader source code
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec4 aColor;
        attribute vec3 aNormal;
        uniform vec3 uLightPos;
        varying vec4 vColor;
        varying float vLightIntensity;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            
            // Calculate lighting
            vec3 lightVector = normalize(uLightPos - aPosition.xyz);
            vLightIntensity = max(dot(aNormal, lightVector), 0.3);
            
            vColor = aColor;
        }
    """.trimIndent()
    
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 vColor;
        varying float vLightIntensity;
        
        void main() {
            gl_FragColor = vColor * vLightIntensity;
        }
    """.trimIndent()
    
    private val sphereVertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        uniform vec3 uLightPos;
        varying vec3 vPosition;
        varying vec3 vNormal;
        
        void main() {
            vPosition = aPosition.xyz;
            vNormal = normalize(aPosition.xyz);
            gl_Position = uMVPMatrix * aPosition;
        }
    """.trimIndent()
    
    private val sphereFragmentShaderCode = """
        precision mediump float;
        uniform vec4 uColor;
        uniform vec3 uLightPos;
        varying vec3 vPosition;
        varying vec3 vNormal;
        
        void main() {
            // Realistic sphere shading
            vec3 lightDir = normalize(uLightPos - vPosition);
            float diffuse = max(dot(vNormal, lightDir), 0.0);
            
            // Add specular highlight
            vec3 reflectDir = reflect(-lightDir, vNormal);
            vec3 viewDir = normalize(-vPosition);
            float specular = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
            
            vec3 ambient = uColor.rgb * 0.3;
            vec3 color = ambient + uColor.rgb * diffuse + vec3(1.0) * specular * 0.5;
            
            gl_FragColor = vec4(color, uColor.a);
        }
    """.trimIndent()
    
    private val laserVertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
        }
    """.trimIndent()
    
    private val laserFragmentShaderCode = """
        precision mediump float;
        uniform float uAlpha;
        
        void main() {
            gl_FragColor = vec4(0.0, 0.9, 0.3, uAlpha);
        }
    """.trimIndent()
    
    // Control methods
    fun setRotationSpeed(speedX: Float, speedY: Float) {
        rotationSpeedX = speedX * 0.5f + 1.0f // Keep base rotation + user input
        rotationSpeedY = speedY * 0.5f
    }
    
    fun onPause() {
        isAnimating = false
    }
    
    fun onResume() {
        isAnimating = true
    }
}