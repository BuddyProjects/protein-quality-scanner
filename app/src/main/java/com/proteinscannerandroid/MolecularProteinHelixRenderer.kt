package com.proteinscannerandroid

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class MolecularProteinHelixRenderer : GLSurfaceView.Renderer {
    
    companion object {
        private const val TAG = "MolecularRenderer"
    }
    
    // Shader programs
    private var sphereProgram: Int = 0
    private var bondProgram: Int = 0
    private var laserProgram: Int = 0
    private var glowProgram: Int = 0
    
    // Matrix arrays
    private val mVMatrix = FloatArray(16)
    private val mPMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private val mNormalMatrix = FloatArray(16)
    
    // Animation variables
    private var angle = 0f
    private var animationSpeed = 1.5f
    private var laserPosition = 0f
    private var laserDirection = 1f
    
    // Molecule geometry - like the emoji!
    private lateinit var sphereVertices: FloatBuffer
    private lateinit var sphereNormals: FloatBuffer
    private lateinit var sphereIndices: ShortBuffer
    private lateinit var bondVertices: FloatBuffer
    private lateinit var bondIndices: ShortBuffer
    
    // Laser geometry
    private lateinit var laserPlaneVertices: FloatBuffer
    private lateinit var laserBeamVertices: FloatBuffer
    
    // Atom positions and properties (like DNA emoji structure)
    private val atomPositions = mutableListOf<FloatArray>()
    private val atomColors = mutableListOf<FloatArray>()
    private val atomSizes = mutableListOf<Float>()
    private val bonds = mutableListOf<Pair<Int, Int>>()  // Connections between atoms
    private val bondHighlight = mutableListOf<Float>()
    
    // Molecule structure parameters (1.5x larger)
    private val helixRadius = 1.5f
    private val helixHeight = 6.0f
    private val atomRadius = 0.4f
    private val bondRadius = 0.08f  // Thicker bonds for better visibility
    
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        try {
            Log.d(TAG, "Creating molecular helix renderer")
            
            // Enhanced OpenGL setup
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            
            // Generate molecular structure like the emoji
            generateMolecularStructure()
            generateSphereGeometry()
            generateBondGeometry()
            generateLaserGeometry()
            
            // Create shader programs
            sphereProgram = createShaderProgram(sphereVertexShader, sphereFragmentShader)
            bondProgram = createShaderProgram(bondVertexShader, bondFragmentShader)
            laserProgram = createShaderProgram(laserVertexShader, laserFragmentShader)
            glowProgram = createShaderProgram(glowVertexShader, glowFragmentShader)
            
            Log.d(TAG, "Molecular helix renderer initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing molecular renderer: ${e.message}", e)
        }
    }
    
    override fun onDrawFrame(unused: GL10) {
        try {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            
            val time = SystemClock.uptimeMillis() * 0.001f
            
            // Update animations
            angle += animationSpeed
            laserPosition += laserDirection * 0.008f  // Slow scanning speed
            if (laserPosition > 1.0f || laserPosition < 0.0f) {
                laserDirection *= -1f
                updateBondHighlighting()
            }
            
            // Set up camera
            Matrix.setLookAtM(mVMatrix, 0, 0f, 0f, 10f, 0f, 0f, 0f, 0f, 1f, 0f)
            
            // Apply rotation
            Matrix.setIdentityM(mModelMatrix, 0)
            Matrix.rotateM(mModelMatrix, 0, angle, 0f, 1f, 0f)
            Matrix.rotateM(mModelMatrix, 0, -15f, 1f, 0f, 0f)
            
            // Calculate MVP matrix
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, mModelMatrix, 0)
            Matrix.multiplyMM(mMVPMatrix, 0, mPMatrix, 0, tempMatrix, 0)
            
            // Calculate normal matrix
            Matrix.invertM(mNormalMatrix, 0, mModelMatrix, 0)
            Matrix.transposeM(mNormalMatrix, 0, mNormalMatrix, 0)
            
            // Draw molecular structure
            drawGlowEffect(time)
            drawMolecularBonds(time)
            drawMolecularAtoms(time)
            drawScanningLaser(time)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in draw frame: ${e.message}", e)
        }
    }
    
    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(mPMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 20f)
    }
    
    private fun generateMolecularStructure() {
        atomPositions.clear()
        atomColors.clear()
        atomSizes.clear()
        bonds.clear()
        bondHighlight.clear()
        
        val numLevels = 12  // Number of base pairs in the helix
        
        // Create DNA double helix structure - ensure bonds go INWARD between helixes
        for (level in 0 until numLevels) {
            val y = (level.toFloat() / (numLevels - 1) - 0.5f) * helixHeight
            val angle = level * 30f * PI.toFloat() / 180f  // 30° rotation per level for smooth helix
            
            // First helix strand - RED like the emoji
            val x1 = helixRadius * cos(angle)
            val z1 = helixRadius * sin(angle)
            atomPositions.add(floatArrayOf(x1, y, z1))
            atomColors.add(floatArrayOf(1.0f, 0.2f, 0.2f, 1.0f))  // Bright red
            atomSizes.add(atomRadius)
            val strand1Index = atomPositions.size - 1
            
            // Second helix strand - BLUE like the emoji (EXACTLY opposite - 180° away)
            val x2 = helixRadius * cos(angle + PI.toFloat())
            val z2 = helixRadius * sin(angle + PI.toFloat())
            atomPositions.add(floatArrayOf(x2, y, z2))
            atomColors.add(floatArrayOf(0.2f, 0.4f, 1.0f, 1.0f))  // Bright blue
            atomSizes.add(atomRadius)
            val strand2Index = atomPositions.size - 1
            
            // ALWAYS connect the pair that was just created (they are guaranteed to be opposite)
            bonds.add(Pair(strand1Index, strand2Index))
            bondHighlight.add(0f)
            
            // Debug log to verify positions
            Log.d(TAG, "Level $level: Atom1(${String.format("%.2f", x1)}, ${String.format("%.2f", y)}, ${String.format("%.2f", z1)}) -> Atom2(${String.format("%.2f", x2)}, ${String.format("%.2f", y)}, ${String.format("%.2f", z2)})")
        }
        
        Log.d(TAG, "Generated DNA helix: ${atomPositions.size} atoms, ${bonds.size} bonds")
    }
    
    private fun generateSphereGeometry() {
        val stacks = 16
        val slices = 20
        
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        
        // Generate unit sphere
        for (i in 0..stacks) {
            val phi = PI.toFloat() * i / stacks
            for (j in 0..slices) {
                val theta = 2 * PI.toFloat() * j / slices
                
                val x = sin(phi) * cos(theta)
                val y = cos(phi)
                val z = sin(phi) * sin(theta)
                
                vertices.addAll(listOf(x, y, z))
                normals.addAll(listOf(x, y, z))
            }
        }
        
        // Generate indices
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val first = (i * (slices + 1) + j).toShort()
                val second = (first + slices + 1).toShort()
                
                indices.addAll(listOf(first, second, (first + 1).toShort()))
                indices.addAll(listOf(second, (second + 1).toShort(), (first + 1).toShort()))
            }
        }
        
        sphereVertices = createFloatBuffer(vertices.toFloatArray())
        sphereNormals = createFloatBuffer(normals.toFloatArray())
        sphereIndices = createShortBuffer(indices.toShortArray())
    }
    
    private fun generateBondGeometry() {
        val segments = 8
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        
        // Generate cylinder for bonds oriented along Z-axis (horizontal)
        for (i in 0..segments) {
            val theta = 2 * PI.toFloat() * i / segments
            val x = bondRadius * cos(theta)
            val y = bondRadius * sin(theta)
            
            // Start circle (at z=0)
            vertices.addAll(listOf(x, y, 0f))
            // End circle (at z=1)
            vertices.addAll(listOf(x, y, 1f))
        }
        
        // Generate indices for cylinder
        for (i in 0 until segments) {
            val bottom1 = (i * 2).toShort()
            val top1 = (i * 2 + 1).toShort()
            val bottom2 = ((i + 1) * 2).toShort()
            val top2 = ((i + 1) * 2 + 1).toShort()
            
            // Two triangles per side
            indices.addAll(listOf(bottom1, bottom2, top1))
            indices.addAll(listOf(top1, bottom2, top2))
        }
        
        bondVertices = createFloatBuffer(vertices.toFloatArray())
        bondIndices = createShortBuffer(indices.toShortArray())
    }
    
    private fun generateLaserGeometry() {
        // Scanning plane that exceeds helix boundaries  
        val planeSize = 5.0f  // Original size - 3x larger than helix radius
        val planeVertices = floatArrayOf(
            -planeSize, 0f, -planeSize,  // Bottom-left corner 
            planeSize, 0f, -planeSize,   // Bottom-right corner
            planeSize, 0f, planeSize,    // Top-right corner
            -planeSize, 0f, planeSize    // Top-left corner
        )
        
        laserPlaneVertices = createFloatBuffer(planeVertices)
        
        Log.d(TAG, "Laser plane size: ${planeSize} (helix radius: ${helixRadius})")
    }
    
    private fun updateBondHighlighting() {
        val laserY = (laserPosition - 0.5f) * helixHeight
        
        for (i in bondHighlight.indices) {
            val bond = bonds[i]
            val pos1 = atomPositions[bond.first]
            val pos2 = atomPositions[bond.second]
            
            val avgY = (pos1[1] + pos2[1]) / 2
            val distance = abs(avgY - laserY)
            
            bondHighlight[i] = when {
                distance < 0.5f -> 1.0f - (distance / 0.5f)
                else -> 0f
            }
        }
    }
    
    private fun drawGlowEffect(time: Float) {
        // Subtle molecular glow
        GLES20.glUseProgram(glowProgram)
        
        val intensityHandle = GLES20.glGetUniformLocation(glowProgram, "uIntensity")
        val timeHandle = GLES20.glGetUniformLocation(glowProgram, "uTime")
        
        val intensity = 0.15f + sin(time * 0.8f) * 0.05f
        GLES20.glUniform1f(intensityHandle, intensity)
        GLES20.glUniform1f(timeHandle, time)
        
        // Draw subtle background
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
    
    private fun drawMolecularAtoms(time: Float) {
        GLES20.glUseProgram(sphereProgram)
        
        val positionHandle = GLES20.glGetAttribLocation(sphereProgram, "aPosition")
        val normalHandle = GLES20.glGetAttribLocation(sphereProgram, "aNormal")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(sphereProgram, "uMVPMatrix")
        val modelMatrixHandle = GLES20.glGetUniformLocation(sphereProgram, "uModelMatrix")
        val lightPosHandle = GLES20.glGetUniformLocation(sphereProgram, "uLightPos")
        val colorHandle = GLES20.glGetUniformLocation(sphereProgram, "uColor")
        val sizeHandle = GLES20.glGetUniformLocation(sphereProgram, "uSize")
        
        // Dynamic lighting
        val lightX = sin(time * 0.4f) * 5f
        val lightZ = cos(time * 0.4f) * 5f
        GLES20.glUniform3f(lightPosHandle, lightX, 6f, lightZ)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, sphereVertices)
        
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, sphereNormals)
        
        // Draw each atom
        for (i in atomPositions.indices) {
            val position = atomPositions[i]
            val color = atomColors[i]
            val size = atomSizes[i]
            
            // Add floating animation
            val floatY = position[1] + sin(time * 2f + i * 0.5f) * 0.1f
            
            val atomMatrix = FloatArray(16)
            Matrix.setIdentityM(atomMatrix, 0)
            Matrix.translateM(atomMatrix, 0, position[0], floatY, position[2])
            Matrix.scaleM(atomMatrix, 0, size, size, size)
            
            val finalMatrix = FloatArray(16)
            Matrix.multiplyMM(finalMatrix, 0, mModelMatrix, 0, atomMatrix, 0)
            
            val atomMVP = FloatArray(16)
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, finalMatrix, 0)
            Matrix.multiplyMM(atomMVP, 0, mPMatrix, 0, tempMatrix, 0)
            
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, atomMVP, 0)
            GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, finalMatrix, 0)
            GLES20.glUniform4f(colorHandle, color[0], color[1], color[2], color[3])
            GLES20.glUniform1f(sizeHandle, size)
            
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIndices.capacity(), GLES20.GL_UNSIGNED_SHORT, sphereIndices)
        }
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
    
    private fun drawMolecularBonds(time: Float) {
        // Draw simple lines instead of complex cylinders to debug
        GLES20.glUseProgram(bondProgram)
        
        val positionHandle = GLES20.glGetAttribLocation(bondProgram, "aPosition")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(bondProgram, "uMVPMatrix")
        val colorHandle = GLES20.glGetUniformLocation(bondProgram, "uColor")
        
        // Set MVP matrix for the entire bond drawing
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glUniform4f(colorHandle, 1.0f, 1.0f, 1.0f, 1.0f)  // White bonds
        
        // Draw each bond as a simple line
        for (i in bonds.indices) {
            val bond = bonds[i]
            val pos1 = atomPositions[bond.first]
            val pos2 = atomPositions[bond.second]
            
            // Create line vertices
            val lineVertices = floatArrayOf(
                pos1[0], pos1[1], pos1[2],  // Start point
                pos2[0], pos2[1], pos2[2]   // End point
            )
            
            val lineBuffer = createFloatBuffer(lineVertices)
            
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, lineBuffer)
            
            // Draw thick line
            GLES20.glLineWidth(8.0f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
            
            GLES20.glDisableVertexAttribArray(positionHandle)
        }
    }
    
    private fun drawScanningLaser(time: Float) {
        GLES20.glUseProgram(laserProgram)
        
        val positionHandle = GLES20.glGetAttribLocation(laserProgram, "aPosition")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(laserProgram, "uMVPMatrix")
        val alphaHandle = GLES20.glGetUniformLocation(laserProgram, "uAlpha")
        val timeHandle = GLES20.glGetUniformLocation(laserProgram, "uTime")
        
        // Create horizontal scanning plane matrix
        val laserMatrix = FloatArray(16)
        Matrix.setIdentityM(laserMatrix, 0)
        
        // Position the plane at the current laser Y position (moves up and down)
        val currentY = (laserPosition - 0.5f) * helixHeight
        Matrix.translateM(laserMatrix, 0, 0f, currentY, 0f)
        
        // Keep plane horizontal (no rotation needed - it's already flat on XZ plane)
        
        val finalMatrix = FloatArray(16)
        Matrix.multiplyMM(finalMatrix, 0, mModelMatrix, 0, laserMatrix, 0)
        
        val laserMVP = FloatArray(16)
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, finalMatrix, 0)
        Matrix.multiplyMM(laserMVP, 0, mPMatrix, 0, tempMatrix, 0)
        
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, laserMVP, 0)
        
        // Very bright, highly visible green laser effect
        val alpha = 0.8f + sin(time * 3f) * 0.2f  // Even brighter with gentle pulse
        GLES20.glUniform1f(alphaHandle, alpha)
        GLES20.glUniform1f(timeHandle, time)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, laserPlaneVertices)
        
        // Draw the horizontal plane
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
    
    // Utility functions
    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data)
        buffer.position(0)
        return buffer
    }
    
    private fun createShortBuffer(data: ShortArray): ShortBuffer {
        val buffer = ByteBuffer.allocateDirect(data.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
        buffer.put(data)
        buffer.position(0)
        return buffer
    }
    
    private fun createShaderProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        return program
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }
    
    // Shader programs
    private val sphereVertexShader = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        uniform vec3 uLightPos;
        uniform float uSize;
        
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        varying vec3 vLightPos;
        
        void main() {
            vec4 worldPos = uModelMatrix * aPosition;
            gl_Position = uMVPMatrix * aPosition;
            
            vWorldPos = worldPos.xyz;
            vNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
            vLightPos = uLightPos;
        }
    """.trimIndent()
    
    private val sphereFragmentShader = """
        precision mediump float;
        
        uniform vec4 uColor;
        
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        varying vec3 vLightPos;
        
        void main() {
            vec3 lightDir = normalize(vLightPos - vWorldPos);
            vec3 normal = normalize(vNormal);
            
            // Enhanced lighting for molecular look
            float diffuse = max(dot(normal, lightDir), 0.0);
            
            vec3 viewDir = normalize(-vWorldPos);
            vec3 reflectDir = reflect(-lightDir, normal);
            float specular = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
            
            vec3 ambient = uColor.rgb * 0.4;
            vec3 finalColor = ambient + uColor.rgb * diffuse + vec3(1.0) * specular * 0.6;
            
            gl_FragColor = vec4(finalColor, uColor.a);
        }
    """.trimIndent()
    
    private val bondVertexShader = """
        uniform mat4 uMVPMatrix;
        
        attribute vec4 aPosition;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
        }
    """.trimIndent()
    
    private val bondFragmentShader = """
        precision mediump float;
        
        uniform vec4 uColor;
        uniform float uHighlight;
        
        void main() {
            vec3 bondColor = uColor.rgb + vec3(uHighlight * 0.8);
            gl_FragColor = vec4(bondColor, uColor.a);
        }
    """.trimIndent()
    
    private val laserVertexShader = """
        uniform mat4 uMVPMatrix;
        
        attribute vec4 aPosition;
        
        varying vec2 vTexCoord;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aPosition.xz * 0.5 + 0.5;
        }
    """.trimIndent()
    
    private val laserFragmentShader = """
        precision mediump float;
        
        uniform float uAlpha;
        uniform float uTime;
        
        varying vec2 vTexCoord;
        
        void main() {
            float dist = length(vTexCoord - 0.5);
            float wave = sin(uTime * 3.0 + dist * 6.0) * 0.5 + 0.5;
            
            // Bright green scanning laser
            vec3 laserColor = vec3(0.0, 1.0, 0.2) * (2.0 + wave);
            
            // Sharp square edges with minimal fade-out for clear boundaries
            float fadeOut = 1.0 - smoothstep(0.0, 0.95, dist);
            
            gl_FragColor = vec4(laserColor, uAlpha * fadeOut);
        }
    """.trimIndent()
    
    private val glowVertexShader = """
        attribute vec4 aPosition;
        
        void main() {
            gl_Position = aPosition;
        }
    """.trimIndent()
    
    private val glowFragmentShader = """
        precision mediump float;
        
        uniform float uIntensity;
        uniform float uTime;
        
        void main() {
            vec3 glowColor = vec3(0.1, 0.2, 0.3) * uIntensity;
            gl_FragColor = vec4(glowColor, 0.2);
        }
    """.trimIndent()
    
    // Control methods
    fun setRotationSpeed(speedX: Float, speedY: Float) {
        animationSpeed = 1.5f + speedX * 0.5f
    }
    
    fun onPause() {
        animationSpeed = 0f
    }
    
    fun onResume() {
        animationSpeed = 1.5f
    }
}