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

class SpectacularProteinHelixRenderer : GLSurfaceView.Renderer {
    
    companion object {
        private const val TAG = "SpectacularRenderer"
        private const val COORDS_PER_VERTEX = 3
    }
    
    // Shader programs
    private var helixProgram: Int = 0
    private var sphereProgram: Int = 0
    private var laserProgram: Int = 0
    private var particleProgram: Int = 0
    private var glowProgram: Int = 0
    
    // Matrix arrays
    private val mVMatrix = FloatArray(16)
    private val mPMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private val mNormalMatrix = FloatArray(16)
    
    // Animation variables
    private var angle = 0f
    private var animationSpeed = 1.2f
    private var laserPosition = 0f
    private var laserDirection = 1f
    private var cameraAngle = 0f
    
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
    private val aminoAcidHighlight = mutableListOf<Float>()
    
    // Laser and effects
    private lateinit var laserBeamVertices: FloatBuffer
    private lateinit var laserPlaneVertices: FloatBuffer
    private lateinit var particleVertices: FloatBuffer
    private lateinit var particleColors: FloatBuffer
    private lateinit var glowVertices: FloatBuffer
    
    // Particle system
    private val maxParticles = 50
    private val particlePositions = mutableListOf<FloatArray>()
    private val particleVelocities = mutableListOf<FloatArray>()
    private val particleLifetimes = mutableListOf<Float>()
    
    // Geometry parameters (1.5x larger + enhanced)
    private val helixRadius = 1.2f
    private val helixHeight = 5.0f
    private val helixTurns = 2.8f
    private val tubeRadius = 0.18f
    private val sphereRadius = 0.35f
    
    // Touch controls
    private var userRotationX = 0f
    private var userRotationY = 0f
    
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        try {
            Log.d(TAG, "Starting spectacular helix renderer")
            
            // Enhanced OpenGL setup
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            
            // Generate all geometry
            generateTubularHelixGeometry()
            generateSphereGeometry()
            generateAminoAcidPositions()
            generateLaserGeometry()
            generateParticleSystem()
            generateGlowEffects()
            
            // Create all shader programs
            helixProgram = createShaderProgram(enhancedVertexShader, enhancedFragmentShader)
            sphereProgram = createShaderProgram(sphereVertexShader, sphereFragmentShader)
            laserProgram = createShaderProgram(laserVertexShader, laserFragmentShader)
            particleProgram = createShaderProgram(particleVertexShader, particleFragmentShader)
            glowProgram = createShaderProgram(glowVertexShader, glowFragmentShader)
            
            Log.d(TAG, "Spectacular helix renderer initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing spectacular renderer: ${e.message}", e)
        }
    }
    
    override fun onDrawFrame(unused: GL10) {
        try {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            
            val time = SystemClock.uptimeMillis() * 0.001f
            
            // Update animations with smooth easing
            angle += animationSpeed * smoothStep(sin(time * 0.3f))
            laserPosition += laserDirection * 0.025f
            if (laserPosition > 1.0f || laserPosition < 0.0f) {
                laserDirection *= -1f
                spawnParticleBurst()
            }
            
            // Dynamic camera with multiple angles
            cameraAngle = sin(time * 0.2f) * 15f + userRotationX * 0.1f
            val cameraDistance = 8f + sin(time * 0.15f) * 1f
            val cameraHeight = sin(time * 0.1f) * 2f + userRotationY * 0.05f
            
            // Set up dynamic camera
            Matrix.setLookAtM(mVMatrix, 0,
                sin(cameraAngle * PI.toFloat() / 180f) * cameraDistance,
                cameraHeight,
                cos(cameraAngle * PI.toFloat() / 180f) * cameraDistance,
                0f, 0f, 0f,
                0f, 1f, 0f
            )
            
            // Apply smooth rotation with user input
            Matrix.setIdentityM(mModelMatrix, 0)
            Matrix.rotateM(mModelMatrix, 0, angle + userRotationX * 0.5f, 0f, 1f, 0f)
            Matrix.rotateM(mModelMatrix, 0, -10f + userRotationY * 0.3f, 1f, 0f, 0f)
            
            // Calculate MVP matrix
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, mModelMatrix, 0)
            Matrix.multiplyMM(mMVPMatrix, 0, mPMatrix, 0, tempMatrix, 0)
            
            // Calculate normal matrix for enhanced lighting
            Matrix.invertM(mNormalMatrix, 0, mModelMatrix, 0)
            Matrix.transposeM(mNormalMatrix, 0, mNormalMatrix, 0)
            
            // Update particle system
            updateParticleSystem(time)
            
            // Update amino acid highlighting based on laser position
            updateAminoAcidHighlighting()
            
            // Draw all elements in order
            drawGlowEffects(time)
            drawTubularHelix(time)
            drawAminoAcidSpheres(time)
            drawVolumetricLaser(time)
            drawParticleEffects(time)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in draw frame: ${e.message}", e)
        }
    }
    
    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(mPMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 20f)
    }
    
    private fun generateTubularHelixGeometry() {
        val segments = 150 // Higher resolution for ultra-smooth curves
        val tubeSegments = 16
        
        val vertexCount = segments * tubeSegments
        val leftVertices = mutableListOf<Float>()
        val leftNormals = mutableListOf<Float>()
        val rightVertices = mutableListOf<Float>()
        val rightNormals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val theta = t * helixTurns * 2 * PI.toFloat()
            val y = (t - 0.5f) * helixHeight
            
            // Enhanced helix curve with natural twist
            val twist = sin(theta * 3f) * 0.1f
            val leftCenterX = helixRadius * cos(theta) + twist
            val leftCenterZ = helixRadius * sin(theta)
            
            val rightCenterX = helixRadius * cos(theta + PI.toFloat()) - twist
            val rightCenterZ = helixRadius * sin(theta + PI.toFloat())
            
            for (j in 0 until tubeSegments) {
                val phi = j.toFloat() / tubeSegments * 2 * PI.toFloat()
                
                val tangentX = -helixRadius * sin(theta)
                val tangentZ = helixRadius * cos(theta)
                val tangentLength = sqrt(tangentX * tangentX + tangentZ * tangentZ)
                
                val normalizedTangentX = tangentX / tangentLength
                val normalizedTangentZ = tangentZ / tangentLength
                
                val binormalX = normalizedTangentZ
                val binormalZ = -normalizedTangentX
                
                val surfaceOffsetX = tubeRadius * (cos(phi) * binormalX)
                val surfaceOffsetY = tubeRadius * sin(phi)
                val surfaceOffsetZ = tubeRadius * (cos(phi) * binormalZ)
                
                leftVertices.addAll(listOf(
                    leftCenterX + surfaceOffsetX,
                    y + surfaceOffsetY,
                    leftCenterZ + surfaceOffsetZ
                ))
                
                leftNormals.addAll(listOf(
                    cos(phi) * binormalX,
                    sin(phi),
                    cos(phi) * binormalZ
                ))
                
                rightVertices.addAll(listOf(
                    rightCenterX + surfaceOffsetX,
                    y + surfaceOffsetY,
                    rightCenterZ + surfaceOffsetZ
                ))
                
                rightNormals.addAll(listOf(
                    cos(phi) * binormalX,
                    sin(phi),
                    cos(phi) * binormalZ
                ))
            }
        }
        
        // Generate indices
        for (i in 0 until segments - 1) {
            for (j in 0 until tubeSegments) {
                val current = (i * tubeSegments + j).toShort()
                val next = (i * tubeSegments + (j + 1) % tubeSegments).toShort()
                val nextRow = ((i + 1) * tubeSegments + j).toShort()
                val nextRowNext = ((i + 1) * tubeSegments + (j + 1) % tubeSegments).toShort()
                
                indices.addAll(listOf(current, nextRow, next))
                indices.addAll(listOf(next, nextRow, nextRowNext))
            }
        }
        
        leftHelixVertices = createFloatBuffer(leftVertices.toFloatArray())
        leftHelixNormals = createFloatBuffer(leftNormals.toFloatArray())
        rightHelixVertices = createFloatBuffer(rightVertices.toFloatArray())
        rightHelixNormals = createFloatBuffer(rightNormals.toFloatArray())
        helixIndices = createShortBuffer(indices.toShortArray())
    }
    
    private fun generateSphereGeometry() {
        val stacks = 24 // Ultra-high resolution spheres
        val slices = 32
        
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        
        for (i in 0..stacks) {
            val phi = PI.toFloat() * i / stacks
            for (j in 0..slices) {
                val theta = 2 * PI.toFloat() * j / slices
                
                val x = sphereRadius * sin(phi) * cos(theta)
                val y = sphereRadius * cos(phi)
                val z = sphereRadius * sin(phi) * sin(theta)
                
                vertices.addAll(listOf(x, y, z))
                
                val length = sqrt(x * x + y * y + z * z)
                normals.addAll(listOf(x / length, y / length, z / length))
            }
        }
        
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
        // Enhanced amino acid positions with scientific accuracy (1.5x scale)
        val positions = listOf(
            // Left strand amino acids
            floatArrayOf(1.8f, -2.0f, 0.9f),   // Leucine (hydrophobic)
            floatArrayOf(-1.4f, -1.1f, 2.1f),  // Valine (branched chain)
            floatArrayOf(1.7f, -0.1f, -1.8f),  // Phenylalanine (aromatic)
            floatArrayOf(-2.1f, 0.8f, 1.2f),   // Glycine (flexible)
            floatArrayOf(1.9f, 1.9f, 0.6f),    // Isoleucine (hydrophobic)
            floatArrayOf(-1.6f, 2.8f, -1.4f),  // Tryptophan (aromatic)
            
            // Right strand amino acids
            floatArrayOf(-1.8f, -1.7f, -0.9f), // Leucine
            floatArrayOf(1.4f, -0.4f, -2.1f),  // Valine
            floatArrayOf(-1.7f, 0.7f, 1.8f),   // Phenylalanine
            floatArrayOf(2.1f, 1.6f, -1.2f),   // Glycine
            floatArrayOf(-1.9f, 2.3f, -0.6f),  // Isoleucine
            floatArrayOf(1.6f, 3.2f, 1.4f)     // Tryptophan
        )
        
        // Enhanced color scheme with glow capability
        val colors = listOf(
            // Left strand colors
            floatArrayOf(0.2f, 0.9f, 0.4f, 1.0f),  // Leucine - emerald green
            floatArrayOf(1.0f, 0.6f, 0.1f, 1.0f),  // Valine - amber orange
            floatArrayOf(0.8f, 0.3f, 0.9f, 1.0f),  // Phenylalanine - violet
            floatArrayOf(0.3f, 0.7f, 1.0f, 1.0f),  // Glycine - cyan blue
            floatArrayOf(0.1f, 0.8f, 0.3f, 1.0f),  // Isoleucine - forest green
            floatArrayOf(0.9f, 0.2f, 0.8f, 1.0f),  // Tryptophan - magenta
            
            // Right strand colors
            floatArrayOf(0.2f, 0.9f, 0.4f, 1.0f),  // Leucine
            floatArrayOf(1.0f, 0.6f, 0.1f, 1.0f),  // Valine
            floatArrayOf(0.8f, 0.3f, 0.9f, 1.0f),  // Phenylalanine
            floatArrayOf(0.3f, 0.7f, 1.0f, 1.0f),  // Glycine
            floatArrayOf(0.1f, 0.8f, 0.3f, 1.0f),  // Isoleucine
            floatArrayOf(0.9f, 0.2f, 0.8f, 1.0f)   // Tryptophan
        )
        
        aminoAcidPositions.clear()
        aminoAcidColors.clear()
        aminoAcidHighlight.clear()
        
        aminoAcidPositions.addAll(positions)
        aminoAcidColors.addAll(colors)
        
        // Initialize highlight values
        repeat(positions.size) {
            aminoAcidHighlight.add(0f)
        }
    }
    
    private fun generateLaserGeometry() {
        // Volumetric laser beam geometry
        val beamVertices = mutableListOf<Float>()
        val segments = 32
        val radius = 0.05f
        
        for (i in 0..segments) {
            val theta = i.toFloat() / segments * 2 * PI.toFloat()
            val x = radius * cos(theta)
            val z = radius * sin(theta)
            
            // Beam start
            beamVertices.addAll(listOf(x, -helixHeight / 2, z))
            // Beam end
            beamVertices.addAll(listOf(x, helixHeight / 2, z))
        }
        
        // Scanning plane
        val planeVertices = floatArrayOf(
            -helixRadius * 1.5f, 0f, -helixRadius * 1.5f,
            helixRadius * 1.5f, 0f, -helixRadius * 1.5f,
            helixRadius * 1.5f, 0f, helixRadius * 1.5f,
            -helixRadius * 1.5f, 0f, helixRadius * 1.5f
        )
        
        laserBeamVertices = createFloatBuffer(beamVertices.toFloatArray())
        laserPlaneVertices = createFloatBuffer(planeVertices)
    }
    
    private fun generateParticleSystem() {
        particlePositions.clear()
        particleVelocities.clear()
        particleLifetimes.clear()
        
        val vertices = mutableListOf<Float>()
        val colors = mutableListOf<Float>()
        
        repeat(maxParticles) {
            // Initial positions (will be updated dynamically)
            vertices.addAll(listOf(0f, 0f, 0f))
            
            // Particle colors (energy analysis theme)
            colors.addAll(listOf(
                0.2f + Math.random().toFloat() * 0.8f,  // R
                0.8f + Math.random().toFloat() * 0.2f,  // G
                0.1f + Math.random().toFloat() * 0.3f,  // B
                1.0f                                     // A
            ))
            
            particlePositions.add(floatArrayOf(0f, 0f, 0f))
            particleVelocities.add(floatArrayOf(0f, 0f, 0f))
            particleLifetimes.add(0f)
        }
        
        particleVertices = createFloatBuffer(vertices.toFloatArray())
        particleColors = createFloatBuffer(colors.toFloatArray())
    }
    
    private fun generateGlowEffects() {
        // Glow quad vertices for post-processing effect
        val glowVertices = floatArrayOf(
            -1f, -1f, 0f,
            1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, 1f, 0f
        )
        
        this.glowVertices = createFloatBuffer(glowVertices)
    }
    
    private fun updateAminoAcidHighlighting() {
        val laserY = (laserPosition - 0.5f) * helixHeight
        
        for (i in aminoAcidHighlight.indices) {
            val position = aminoAcidPositions[i]
            val distance = abs(position[1] - laserY)
            
            // Create highlight effect when laser is near amino acid
            val highlightIntensity = when {
                distance < 0.3f -> 1.0f - (distance / 0.3f) // Full highlight
                distance < 0.6f -> 0.5f * (1.0f - (distance - 0.3f) / 0.3f) // Fade out
                else -> 0f
            }
            
            aminoAcidHighlight[i] = smoothStep(highlightIntensity)
        }
    }
    
    private fun spawnParticleBurst() {
        val laserY = (laserPosition - 0.5f) * helixHeight
        var particleIndex = 0
        
        for (i in aminoAcidPositions.indices) {
            if (aminoAcidHighlight[i] > 0.5f && particleIndex < maxParticles - 5) {
                val position = aminoAcidPositions[i]
                
                // Spawn burst of particles
                repeat(5) { j ->
                    if (particleIndex + j < maxParticles) {
                        val angle = j * 2 * PI.toFloat() / 5
                        particlePositions[particleIndex + j] = floatArrayOf(
                            position[0], position[1], position[2]
                        )
                        particleVelocities[particleIndex + j] = floatArrayOf(
                            cos(angle) * 2f,
                            (Math.random() - 0.5).toFloat() * 3f,
                            sin(angle) * 2f
                        )
                        particleLifetimes[particleIndex + j] = 1.0f
                    }
                }
                particleIndex += 5
            }
        }
    }
    
    private fun updateParticleSystem(time: Float) {
        for (i in particleLifetimes.indices) {
            if (particleLifetimes[i] > 0f) {
                val dt = 0.016f // ~60fps
                
                // Update lifetime
                particleLifetimes[i] -= dt
                
                // Update position
                val pos = particlePositions[i]
                val vel = particleVelocities[i]
                
                pos[0] += vel[0] * dt
                pos[1] += vel[1] * dt
                pos[2] += vel[2] * dt
                
                // Apply gravity and drag
                vel[1] -= 9.8f * dt * 0.1f // Gentle gravity
                vel[0] *= 0.95f // Drag
                vel[2] *= 0.95f
            }
        }
        
        // Update particle vertex buffer
        val updatedVertices = mutableListOf<Float>()
        for (i in particlePositions.indices) {
            val pos = particlePositions[i]
            updatedVertices.addAll(listOf(pos[0], pos[1], pos[2]))
        }
        
        particleVertices.clear()
        particleVertices.put(updatedVertices.toFloatArray())
        particleVertices.position(0)
    }
    
    private fun drawGlowEffects(time: Float) {
        // Subtle background glow for sci-fi atmosphere
        GLES20.glUseProgram(glowProgram)
        
        val mvpHandle = GLES20.glGetUniformLocation(glowProgram, "uMVPMatrix")
        val timeHandle = GLES20.glGetUniformLocation(glowProgram, "uTime")
        val intensityHandle = GLES20.glGetUniformLocation(glowProgram, "uIntensity")
        
        // Identity matrix for screen-space glow
        val identityMatrix = FloatArray(16)
        Matrix.setIdentityM(identityMatrix, 0)
        
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, identityMatrix, 0)
        GLES20.glUniform1f(timeHandle, time)
        GLES20.glUniform1f(intensityHandle, 0.1f + sin(time * 0.5f) * 0.05f)
        
        val positionHandle = GLES20.glGetAttribLocation(glowProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, glowVertices)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
    
    private fun drawTubularHelix(time: Float) {
        GLES20.glUseProgram(helixProgram)
        
        val positionHandle = GLES20.glGetAttribLocation(helixProgram, "aPosition")
        val normalHandle = GLES20.glGetAttribLocation(helixProgram, "aNormal")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(helixProgram, "uMVPMatrix")
        val normalMatrixHandle = GLES20.glGetUniformLocation(helixProgram, "uNormalMatrix")
        val lightPosHandle = GLES20.glGetUniformLocation(helixProgram, "uLightPos")
        val colorHandle = GLES20.glGetUniformLocation(helixProgram, "uColor")
        val timeHandle = GLES20.glGetUniformLocation(helixProgram, "uTime")
        
        // Dynamic lighting
        val lightX = sin(time * 0.3f) * 4f
        val lightZ = cos(time * 0.3f) * 4f
        GLES20.glUniform3f(lightPosHandle, lightX, 4.0f, lightZ)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(normalMatrixHandle, 1, false, mNormalMatrix, 0)
        GLES20.glUniform1f(timeHandle, time)
        
        // Draw left helix strand with enhanced colors
        GLES20.glUniform4f(colorHandle, 0.15f, 0.4f, 1.0f, 0.95f)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, leftHelixVertices)
        
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, leftHelixNormals)
        
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, helixIndices.capacity(), GLES20.GL_UNSIGNED_SHORT, helixIndices)
        
        // Draw right helix strand
        GLES20.glUniform4f(colorHandle, 0.5f, 0.2f, 1.0f, 0.95f)
        
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, rightHelixVertices)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, rightHelixNormals)
        
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, helixIndices.capacity(), GLES20.GL_UNSIGNED_SHORT, helixIndices)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
    
    private fun drawAminoAcidSpheres(time: Float) {
        GLES20.glUseProgram(sphereProgram)
        
        val positionHandle = GLES20.glGetAttribLocation(sphereProgram, "aPosition")
        val normalHandle = GLES20.glGetAttribLocation(sphereProgram, "aNormal")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(sphereProgram, "uMVPMatrix")
        val modelMatrixHandle = GLES20.glGetUniformLocation(sphereProgram, "uModelMatrix")
        val lightPosHandle = GLES20.glGetUniformLocation(sphereProgram, "uLightPos")
        val colorHandle = GLES20.glGetUniformLocation(sphereProgram, "uColor")
        val highlightHandle = GLES20.glGetUniformLocation(sphereProgram, "uHighlight")
        val timeHandle = GLES20.glGetUniformLocation(sphereProgram, "uTime")
        
        val lightX = sin(time * 0.3f) * 4f
        val lightZ = cos(time * 0.3f) * 4f
        GLES20.glUniform3f(lightPosHandle, lightX, 4.0f, lightZ)
        GLES20.glUniform1f(timeHandle, time)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, sphereVertices)
        
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, sphereNormals)
        
        // Draw each amino acid sphere with dynamic highlighting
        for (i in aminoAcidPositions.indices) {
            val position = aminoAcidPositions[i]
            val color = aminoAcidColors[i]
            val highlight = aminoAcidHighlight[i]
            
            // Add subtle floating animation
            val floatOffset = sin(time + i * 0.5f) * 0.05f
            
            val sphereMatrix = FloatArray(16)
            Matrix.setIdentityM(sphereMatrix, 0)
            Matrix.translateM(sphereMatrix, 0, position[0], position[1] + floatOffset, position[2])
            
            // Add slight rotation for each amino acid
            Matrix.rotateM(sphereMatrix, 0, time * 30f + i * 45f, 1f, 1f, 0f)
            
            val finalMatrix = FloatArray(16)
            Matrix.multiplyMM(finalMatrix, 0, mModelMatrix, 0, sphereMatrix, 0)
            
            val sphereMVP = FloatArray(16)
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, finalMatrix, 0)
            Matrix.multiplyMM(sphereMVP, 0, mPMatrix, 0, tempMatrix, 0)
            
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, sphereMVP, 0)
            GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, finalMatrix, 0)
            GLES20.glUniform4f(colorHandle, color[0], color[1], color[2], color[3])
            GLES20.glUniform1f(highlightHandle, highlight)
            
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIndices.capacity(), GLES20.GL_UNSIGNED_SHORT, sphereIndices)
        }
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
    
    private fun drawVolumetricLaser(time: Float) {
        GLES20.glUseProgram(laserProgram)
        
        val positionHandle = GLES20.glGetAttribLocation(laserProgram, "aPosition")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(laserProgram, "uMVPMatrix")
        val alphaHandle = GLES20.glGetUniformLocation(laserProgram, "uAlpha")
        val intensityHandle = GLES20.glGetUniformLocation(laserProgram, "uIntensity")
        val timeHandle = GLES20.glGetUniformLocation(laserProgram, "uTime")
        
        GLES20.glUniform1f(timeHandle, time)
        
        // Draw scanning plane
        val laserMatrix = FloatArray(16)
        Matrix.setIdentityM(laserMatrix, 0)
        Matrix.translateM(laserMatrix, 0, 0f, (laserPosition - 0.5f) * helixHeight, 0f)
        Matrix.rotateM(laserMatrix, 0, 90f, 1f, 0f, 0f)
        
        val finalMatrix = FloatArray(16)
        Matrix.multiplyMM(finalMatrix, 0, mModelMatrix, 0, laserMatrix, 0)
        
        val laserMVP = FloatArray(16)
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, mVMatrix, 0, finalMatrix, 0)
        Matrix.multiplyMM(laserMVP, 0, mPMatrix, 0, tempMatrix, 0)
        
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, laserMVP, 0)
        
        // Pulsing laser intensity
        val intensity = 0.6f + sin(time * 8f) * 0.3f
        val alpha = 0.3f + sin(time * 4f) * 0.2f
        GLES20.glUniform1f(intensityHandle, intensity)
        GLES20.glUniform1f(alphaHandle, alpha)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, laserPlaneVertices)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
    
    private fun drawParticleEffects(time: Float) {
        GLES20.glUseProgram(particleProgram)
        
        val positionHandle = GLES20.glGetAttribLocation(particleProgram, "aPosition")
        val colorHandle = GLES20.glGetAttribLocation(particleProgram, "aColor")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(particleProgram, "uMVPMatrix")
        val pointSizeHandle = GLES20.glGetUniformLocation(particleProgram, "uPointSize")
        val timeHandle = GLES20.glGetUniformLocation(particleProgram, "uTime")
        
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glUniform1f(pointSizeHandle, 8f + sin(time * 2f) * 2f)
        GLES20.glUniform1f(timeHandle, time)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, particleVertices)
        
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, particleColors)
        
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, maxParticles)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
    
    // Utility functions
    private fun smoothStep(t: Float): Float {
        val clampedT = t.coerceIn(0f, 1f)
        return clampedT * clampedT * (3f - 2f * clampedT)
    }
    
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
    
    // Enhanced shader programs with advanced effects
    private val enhancedVertexShader = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uNormalMatrix;
        uniform vec3 uLightPos;
        uniform vec4 uColor;
        uniform float uTime;
        
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        
        varying vec3 vLighting;
        varying vec4 vColor;
        varying float vGlow;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            
            vec3 transformedNormal = normalize((uNormalMatrix * vec4(aNormal, 0.0)).xyz);
            vec3 lightDirection = normalize(uLightPos - aPosition.xyz);
            
            // Enhanced lighting with rim lighting
            float directional = max(dot(transformedNormal, lightDirection), 0.0);
            float rimLight = 1.0 - max(dot(transformedNormal, normalize(-aPosition.xyz)), 0.0);
            rimLight = pow(rimLight, 3.0) * 0.3;
            
            // Dynamic glow based on time
            vGlow = 0.1 + sin(uTime + aPosition.y * 0.5) * 0.05;
            
            vLighting = vec3(0.3) + vec3(0.7) * directional + vec3(rimLight);
            vColor = uColor;
        }
    """.trimIndent()
    
    private val enhancedFragmentShader = """
        precision mediump float;
        
        varying vec3 vLighting;
        varying vec4 vColor;
        varying float vGlow;
        
        void main() {
            vec3 finalColor = vColor.rgb * vLighting + vec3(vGlow);
            gl_FragColor = vec4(finalColor, vColor.a);
        }
    """.trimIndent()
    
    private val sphereVertexShader = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        uniform vec3 uLightPos;
        uniform vec4 uColor;
        uniform float uHighlight;
        uniform float uTime;
        
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        varying vec4 vColor;
        varying float vHighlight;
        varying float vTime;
        
        void main() {
            vec4 worldPos = uModelMatrix * aPosition;
            gl_Position = uMVPMatrix * aPosition;
            
            vWorldPos = worldPos.xyz;
            vNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
            vColor = uColor;
            vHighlight = uHighlight;
            vTime = uTime;
        }
    """.trimIndent()
    
    private val sphereFragmentShader = """
        precision mediump float;
        
        uniform vec3 uLightPos;
        
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        varying vec4 vColor;
        varying float vHighlight;
        varying float vTime;
        
        void main() {
            vec3 lightDir = normalize(uLightPos - vWorldPos);
            vec3 normal = normalize(vNormal);
            
            // Enhanced lighting
            float diffuse = max(dot(normal, lightDir), 0.0);
            
            vec3 viewDir = normalize(-vWorldPos);
            vec3 reflectDir = reflect(-lightDir, normal);
            float specular = pow(max(dot(viewDir, reflectDir), 0.0), 64.0);
            
            // Dynamic highlight when laser hits
            float highlightGlow = vHighlight * (1.0 + sin(vTime * 10.0) * 0.3);
            
            vec3 ambient = vColor.rgb * 0.3;
            vec3 highlightColor = vec3(1.0, 0.9, 0.7) * highlightGlow;
            vec3 finalColor = ambient + vColor.rgb * diffuse + vec3(1.0) * specular * 0.5 + highlightColor;
            
            gl_FragColor = vec4(finalColor, vColor.a);
        }
    """.trimIndent()
    
    private val laserVertexShader = """
        uniform mat4 uMVPMatrix;
        uniform float uTime;
        
        attribute vec4 aPosition;
        
        varying vec2 vTexCoord;
        varying float vTime;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aPosition.xz * 0.5 + 0.5;
            vTime = uTime;
        }
    """.trimIndent()
    
    private val laserFragmentShader = """
        precision mediump float;
        
        uniform float uAlpha;
        uniform float uIntensity;
        
        varying vec2 vTexCoord;
        varying float vTime;
        
        void main() {
            // Create scanning wave pattern
            float dist = length(vTexCoord - 0.5);
            float wave = sin(vTime * 3.0 + dist * 10.0) * 0.5 + 0.5;
            
            // Laser color with wave interference
            vec3 laserColor = vec3(0.1, 1.0, 0.3) * uIntensity * wave;
            
            // Fade from center
            float fadeOut = 1.0 - smoothstep(0.0, 0.5, dist);
            
            gl_FragColor = vec4(laserColor, uAlpha * fadeOut);
        }
    """.trimIndent()
    
    private val particleVertexShader = """
        uniform mat4 uMVPMatrix;
        uniform float uPointSize;
        uniform float uTime;
        
        attribute vec4 aPosition;
        attribute vec4 aColor;
        
        varying vec4 vColor;
        varying float vTime;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            gl_PointSize = uPointSize * (1.0 + sin(uTime + aPosition.x) * 0.3);
            vColor = aColor;
            vTime = uTime;
        }
    """.trimIndent()
    
    private val particleFragmentShader = """
        precision mediump float;
        
        varying vec4 vColor;
        varying float vTime;
        
        void main() {
            // Create circular particles with glow
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord);
            
            if (dist > 0.5) {
                discard;
            }
            
            float alpha = 1.0 - smoothstep(0.0, 0.5, dist);
            float glow = 1.0 + sin(vTime * 5.0) * 0.5;
            
            gl_FragColor = vec4(vColor.rgb * glow, vColor.a * alpha);
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
        
        uniform float uTime;
        uniform float uIntensity;
        
        void main() {
            vec2 uv = gl_FragCoord.xy / vec2(512.0, 512.0); // Approximate screen size
            
            // Create subtle background glow
            float glow = sin(uTime * 0.5) * 0.5 + 0.5;
            vec3 glowColor = vec3(0.05, 0.1, 0.2) * uIntensity * glow;
            
            gl_FragColor = vec4(glowColor, 0.1);
        }
    """.trimIndent()
    
    // Control methods
    fun setRotationSpeed(speedX: Float, speedY: Float) {
        userRotationX += speedX * 50f
        userRotationY += speedY * 50f
        userRotationY = userRotationY.coerceIn(-90f, 90f)
    }
    
    fun onPause() {
        animationSpeed = 0f
    }
    
    fun onResume() {
        animationSpeed = 1.2f
    }
}