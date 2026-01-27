package com.proteinscannerandroid

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class EnhancedProteinHelixRenderer : GLSurfaceView.Renderer {
    
    companion object {
        private const val TAG = "EnhancedHelixRenderer"
        private const val COORDS_PER_VERTEX = 3
    }
    
    // Shader programs
    private var helixProgram: Int = 0
    private var sphereProgram: Int = 0
    
    // Matrix arrays
    private val mVMatrix = FloatArray(16)
    private val mPMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private val mNormalMatrix = FloatArray(16)
    
    // Animation variables
    private var angle = 0f
    private var animationSpeed = 1.5f
    
    // Enhanced helix geometry
    private lateinit var leftHelixVertices: FloatBuffer
    private lateinit var leftHelixNormals: FloatBuffer
    private lateinit var rightHelixVertices: FloatBuffer
    private lateinit var rightHelixNormals: FloatBuffer
    private lateinit var helixIndices: ShortBuffer
    
    // Amino acid spheres
    private lateinit var sphereVertices: FloatBuffer
    private lateinit var sphereNormals: FloatBuffer
    private lateinit var sphereIndices: ShortBuffer
    private val aminoAcidPositions = mutableListOf<FloatArray>()
    private val aminoAcidColors = mutableListOf<FloatArray>()
    
    // Geometry parameters (1.5x larger)
    private val helixRadius = 1.1f  // Increased from 0.75f
    private val helixHeight = 4.5f  // Increased from 3.0f
    private val helixTurns = 2.5f
    private val tubeRadius = 0.15f  // Thickness of helix strands
    private val sphereRadius = 0.3f // Amino acid sphere size
    
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        try {
            Log.d(TAG, "Starting enhanced helix renderer")
            
            // Set background to transparent
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            
            // Generate enhanced geometry
            generateTubularHelixGeometry()
            generateSphereGeometry()
            generateAminoAcidPositions()
            
            // Create enhanced shaders
            helixProgram = createShaderProgram(enhancedVertexShader, enhancedFragmentShader)
            sphereProgram = createShaderProgram(sphereVertexShader, sphereFragmentShader)
            
            Log.d(TAG, "Enhanced helix renderer initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing enhanced renderer: ${e.message}", e)
        }
    }
    
    override fun onDrawFrame(unused: GL10) {
        try {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            
            // Update rotation
            angle += animationSpeed
            
            // Set up camera (moved back for larger view)
            Matrix.setLookAtM(mVMatrix, 0, 0f, 0f, 8f, 0f, 0f, 0f, 0f, 1f, 0f)
            
            // Apply rotation
            Matrix.setIdentityM(mModelMatrix, 0)
            Matrix.rotateM(mModelMatrix, 0, angle, 0f, 1f, 0f)
            Matrix.rotateM(mModelMatrix, 0, -10f, 1f, 0f, 0f) // Slight tilt for better view
            
            // Calculate MVP matrix
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, mModelMatrix, 0)
            Matrix.multiplyMM(mMVPMatrix, 0, mPMatrix, 0, tempMatrix, 0)
            
            // Calculate normal matrix for lighting
            Matrix.invertM(mNormalMatrix, 0, mModelMatrix, 0)
            Matrix.transposeM(mNormalMatrix, 0, mNormalMatrix, 0)
            
            // Draw enhanced tubular helix
            drawTubularHelix()
            
            // Draw realistic amino acid spheres
            drawAminoAcidSpheres()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in draw frame: ${e.message}", e)
        }
    }
    
    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(mPMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 15f)
    }
    
    private fun generateTubularHelixGeometry() {
        val segments = 120 // Higher resolution for smooth curves
        val tubeSegments = 12 // Circular cross-section segments
        
        val vertexCount = segments * tubeSegments
        val leftVertices = mutableListOf<Float>()
        val leftNormals = mutableListOf<Float>()
        val rightVertices = mutableListOf<Float>()
        val rightNormals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        
        // Generate tubular helix geometry
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val theta = t * helixTurns * 2 * PI.toFloat()
            val y = (t - 0.5f) * helixHeight
            
            // Left strand center
            val leftCenterX = helixRadius * cos(theta)
            val leftCenterZ = helixRadius * sin(theta)
            
            // Right strand center (180° offset)
            val rightCenterX = helixRadius * cos(theta + PI.toFloat())
            val rightCenterZ = helixRadius * sin(theta + PI.toFloat())
            
            // Generate tube cross-section for both strands
            for (j in 0 until tubeSegments) {
                val phi = j.toFloat() / tubeSegments * 2 * PI.toFloat()
                
                // Calculate tangent and normal vectors for proper tube orientation
                val tangentX = -helixRadius * sin(theta)
                val tangentZ = helixRadius * cos(theta)
                val tangentLength = sqrt(tangentX * tangentX + tangentZ * tangentZ)
                
                val normalizedTangentX = tangentX / tangentLength
                val normalizedTangentZ = tangentZ / tangentLength
                
                // Binormal vector (perpendicular to tangent and Y axis)
                val binormalX = normalizedTangentZ
                val binormalZ = -normalizedTangentX
                
                // Tube surface point
                val surfaceOffsetX = tubeRadius * (cos(phi) * binormalX)
                val surfaceOffsetY = tubeRadius * sin(phi)
                val surfaceOffsetZ = tubeRadius * (cos(phi) * binormalZ)
                
                // Left strand vertices
                leftVertices.addAll(listOf(
                    leftCenterX + surfaceOffsetX,
                    y + surfaceOffsetY,
                    leftCenterZ + surfaceOffsetZ
                ))
                
                // Left strand normals
                leftNormals.addAll(listOf(
                    cos(phi) * binormalX,
                    sin(phi),
                    cos(phi) * binormalZ
                ))
                
                // Right strand vertices
                rightVertices.addAll(listOf(
                    rightCenterX + surfaceOffsetX,
                    y + surfaceOffsetY,
                    rightCenterZ + surfaceOffsetZ
                ))
                
                // Right strand normals
                rightNormals.addAll(listOf(
                    cos(phi) * binormalX,
                    sin(phi),
                    cos(phi) * binormalZ
                ))
            }
        }
        
        // Generate indices for triangle strips
        for (i in 0 until segments - 1) {
            for (j in 0 until tubeSegments) {
                val current = (i * tubeSegments + j).toShort()
                val next = (i * tubeSegments + (j + 1) % tubeSegments).toShort()
                val nextRow = ((i + 1) * tubeSegments + j).toShort()
                val nextRowNext = ((i + 1) * tubeSegments + (j + 1) % tubeSegments).toShort()
                
                // Two triangles per quad
                indices.addAll(listOf(current, nextRow, next))
                indices.addAll(listOf(next, nextRow, nextRowNext))
            }
        }
        
        // Convert to buffers
        leftHelixVertices = createFloatBuffer(leftVertices.toFloatArray())
        leftHelixNormals = createFloatBuffer(leftNormals.toFloatArray())
        rightHelixVertices = createFloatBuffer(rightVertices.toFloatArray())
        rightHelixNormals = createFloatBuffer(rightNormals.toFloatArray())
        helixIndices = createShortBuffer(indices.toShortArray())
    }
    
    private fun generateSphereGeometry() {
        val stacks = 20 // Higher resolution for smooth spheres
        val slices = 24
        
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        
        // Generate sphere vertices
        for (i in 0..stacks) {
            val phi = PI.toFloat() * i / stacks
            for (j in 0..slices) {
                val theta = 2 * PI.toFloat() * j / slices
                
                val x = sphereRadius * sin(phi) * cos(theta)
                val y = sphereRadius * cos(phi)
                val z = sphereRadius * sin(phi) * sin(theta)
                
                vertices.addAll(listOf(x, y, z))
                
                // Normals are the same as normalized vertex positions for a sphere
                val length = sqrt(x * x + y * y + z * z)
                normals.addAll(listOf(x / length, y / length, z / length))
            }
        }
        
        // Generate sphere indices
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
    
    private fun generateAminoAcidPositions() {
        // Scientific amino acid positions along the helix (1.5x scale)
        val positions = listOf(
            // Left strand amino acids
            floatArrayOf(1.65f, -1.8f, 0.75f),   // Leucine (green) - hydrophobic
            floatArrayOf(-1.2f, -0.9f, 1.95f),   // Valine (orange) - branched chain
            floatArrayOf(1.5f, 0.0f, -1.65f),    // Phenylalanine (purple) - aromatic
            floatArrayOf(-1.95f, 0.9f, 1.05f),   // Glycine (blue) - flexible
            floatArrayOf(1.8f, 1.8f, 0.45f),     // Isoleucine (green) - hydrophobic
            
            // Right strand amino acids
            floatArrayOf(-1.65f, -1.5f, -0.75f), // Leucine (green)
            floatArrayOf(1.2f, -0.3f, -1.95f),   // Valine (orange)
            floatArrayOf(-1.5f, 0.6f, 1.65f),    // Phenylalanine (purple)
            floatArrayOf(1.95f, 1.5f, -1.05f),   // Glycine (blue)
            floatArrayOf(-1.8f, 2.1f, -0.45f)    // Isoleucine (green)
        )
        
        // Scientific color coding based on amino acid properties
        val colors = listOf(
            // Left strand colors
            floatArrayOf(0.2f, 0.9f, 0.4f, 1.0f),  // Leucine - green (hydrophobic)
            floatArrayOf(1.0f, 0.6f, 0.1f, 1.0f),  // Valine - orange (branched)
            floatArrayOf(0.8f, 0.3f, 0.9f, 1.0f),  // Phenylalanine - purple (aromatic)
            floatArrayOf(0.3f, 0.7f, 1.0f, 1.0f),  // Glycine - blue (flexible)
            floatArrayOf(0.1f, 0.8f, 0.3f, 1.0f),  // Isoleucine - green (hydrophobic)
            
            // Right strand colors
            floatArrayOf(0.2f, 0.9f, 0.4f, 1.0f),  // Leucine
            floatArrayOf(1.0f, 0.6f, 0.1f, 1.0f),  // Valine
            floatArrayOf(0.8f, 0.3f, 0.9f, 1.0f),  // Phenylalanine
            floatArrayOf(0.3f, 0.7f, 1.0f, 1.0f),  // Glycine
            floatArrayOf(0.1f, 0.8f, 0.3f, 1.0f)   // Isoleucine
        )
        
        aminoAcidPositions.clear()
        aminoAcidColors.clear()
        aminoAcidPositions.addAll(positions)
        aminoAcidColors.addAll(colors)
    }
    
    private fun drawTubularHelix() {
        GLES20.glUseProgram(helixProgram)
        
        // Get shader handles
        val positionHandle = GLES20.glGetAttribLocation(helixProgram, "aPosition")
        val normalHandle = GLES20.glGetAttribLocation(helixProgram, "aNormal")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(helixProgram, "uMVPMatrix")
        val normalMatrixHandle = GLES20.glGetUniformLocation(helixProgram, "uNormalMatrix")
        val lightPosHandle = GLES20.glGetUniformLocation(helixProgram, "uLightPos")
        val colorHandle = GLES20.glGetUniformLocation(helixProgram, "uColor")
        
        // Set lighting
        GLES20.glUniform3f(lightPosHandle, 3.0f, 3.0f, 5.0f)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(normalMatrixHandle, 1, false, mNormalMatrix, 0)
        
        // Draw left helix strand (blue)
        GLES20.glUniform4f(colorHandle, 0.2f, 0.5f, 1.0f, 0.9f)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, leftHelixVertices)
        
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, leftHelixNormals)
        
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, helixIndices.capacity(), GLES20.GL_UNSIGNED_SHORT, helixIndices)
        
        // Draw right helix strand (purple)
        GLES20.glUniform4f(colorHandle, 0.6f, 0.3f, 1.0f, 0.9f)
        
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, rightHelixVertices)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, rightHelixNormals)
        
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, helixIndices.capacity(), GLES20.GL_UNSIGNED_SHORT, helixIndices)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
    
    private fun drawAminoAcidSpheres() {
        GLES20.glUseProgram(sphereProgram)
        
        val positionHandle = GLES20.glGetAttribLocation(sphereProgram, "aPosition")
        val normalHandle = GLES20.glGetAttribLocation(sphereProgram, "aNormal")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(sphereProgram, "uMVPMatrix")
        val modelMatrixHandle = GLES20.glGetUniformLocation(sphereProgram, "uModelMatrix")
        val lightPosHandle = GLES20.glGetUniformLocation(sphereProgram, "uLightPos")
        val colorHandle = GLES20.glGetUniformLocation(sphereProgram, "uColor")
        
        GLES20.glUniform3f(lightPosHandle, 3.0f, 3.0f, 5.0f)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, sphereVertices)
        
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, sphereNormals)
        
        // Draw each amino acid sphere at its position
        for (i in aminoAcidPositions.indices) {
            val position = aminoAcidPositions[i]
            val color = aminoAcidColors[i]
            
            // Create transformation matrix for this sphere
            val sphereMatrix = FloatArray(16)
            Matrix.setIdentityM(sphereMatrix, 0)
            Matrix.translateM(sphereMatrix, 0, position[0], position[1], position[2])
            
            // Apply base rotation
            val finalMatrix = FloatArray(16)
            Matrix.multiplyMM(finalMatrix, 0, mModelMatrix, 0, sphereMatrix, 0)
            
            // Calculate MVP for this sphere
            val sphereMVP = FloatArray(16)
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, finalMatrix, 0)
            Matrix.multiplyMM(sphereMVP, 0, mPMatrix, 0, tempMatrix, 0)
            
            // Set uniforms
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, sphereMVP, 0)
            GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, finalMatrix, 0)
            GLES20.glUniform4f(colorHandle, color[0], color[1], color[2], color[3])
            
            // Draw sphere
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIndices.capacity(), GLES20.GL_UNSIGNED_SHORT, sphereIndices)
        }
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
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
    
    // Enhanced shaders with realistic lighting
    private val enhancedVertexShader = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uNormalMatrix;
        uniform vec3 uLightPos;
        uniform vec4 uColor;
        
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        
        varying vec3 vLighting;
        varying vec4 vColor;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            
            // Transform normal
            vec3 transformedNormal = normalize((uNormalMatrix * vec4(aNormal, 0.0)).xyz);
            
            // Calculate lighting
            vec3 lightDirection = normalize(uLightPos - aPosition.xyz);
            float directional = max(dot(transformedNormal, lightDirection), 0.0);
            
            // Add ambient + diffuse lighting
            vLighting = vec3(0.3) + vec3(0.7) * directional;
            vColor = uColor;
        }
    """.trimIndent()
    
    private val enhancedFragmentShader = """
        precision mediump float;
        
        varying vec3 vLighting;
        varying vec4 vColor;
        
        void main() {
            gl_FragColor = vec4(vColor.rgb * vLighting, vColor.a);
        }
    """.trimIndent()
    
    private val sphereVertexShader = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        uniform vec3 uLightPos;
        uniform vec4 uColor;
        
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        varying vec4 vColor;
        
        void main() {
            vec4 worldPos = uModelMatrix * aPosition;
            gl_Position = uMVPMatrix * aPosition;
            
            vWorldPos = worldPos.xyz;
            vNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
            vColor = uColor;
        }
    """.trimIndent()
    
    private val sphereFragmentShader = """
        precision mediump float;
        
        uniform vec3 uLightPos;
        
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        varying vec4 vColor;
        
        void main() {
            // Calculate realistic sphere lighting
            vec3 lightDir = normalize(uLightPos - vWorldPos);
            vec3 normal = normalize(vNormal);
            
            // Diffuse lighting
            float diffuse = max(dot(normal, lightDir), 0.0);
            
            // Specular lighting for glossy spheres
            vec3 viewDir = normalize(-vWorldPos);
            vec3 reflectDir = reflect(-lightDir, normal);
            float specular = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
            
            // Combine ambient, diffuse, and specular
            vec3 ambient = vColor.rgb * 0.3;
            vec3 finalColor = ambient + vColor.rgb * diffuse + vec3(1.0) * specular * 0.4;
            
            gl_FragColor = vec4(finalColor, vColor.a);
        }
    """.trimIndent()
    
    // Control methods
    fun setRotationSpeed(speedX: Float, speedY: Float) {
        animationSpeed = speedX * 0.5f + 1.0f
    }
    
    fun onPause() {
        // Can pause animation if needed
    }
    
    fun onResume() {
        // Resume animation
    }
}