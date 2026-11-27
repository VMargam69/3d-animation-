package com.example.engine.io

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import com.example.engine.animation.AnimationEvaluator
import com.example.engine.gl.GlSceneRenderer
import com.example.engine.gl.RenderConfig
import com.example.engine.model.ExportResolution
import com.example.engine.model.RenderQuality
import com.example.engine.model.SceneDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class VideoExportRequest(
    val resolution: ExportResolution = ExportResolution.HD_720,
    val fps: Int = 30,
    val startFrame: Int = 0,
    val endFrame: Int = 120,
    val quality: RenderQuality = RenderQuality.MEDIUM
)

object VideoExporter {

    /**
     * Genuine offline hardware-accelerated MP4 (H.264) exporter using offscreen EGL14 + OpenGL ES 3.0
     * rendering directly into MediaCodec's input Surface and muxing with MediaMuxer.
     */
    suspend fun exportToMp4(
        context: Context,
        scene: SceneDocument,
        request: VideoExportRequest,
        onProgress: (Float) -> Unit
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.Default) {
        runCatching {
            val width = (request.resolution.width / 16) * 16
            val height = (request.resolution.height / 16) * 16
            val fps = request.fps.coerceIn(15, 60)
            val startF = request.startFrame.coerceAtLeast(0)
            val endF = request.endFrame.coerceAtLeast(startF + 1)
            val totalFrames = (endF - startF + 1).coerceAtLeast(1)

            val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, request.quality.bitrateMbps * 1_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            var inputSurface: Surface? = null
            var muxer: MediaMuxer? = null
            var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
            var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
            var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
            val renderer = GlSceneRenderer()

            try {
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = encoder.createInputSurface()
                encoder.start()

                // Setup offscreen EGL14 context bound to encoder's Surface
                eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                val version = IntArray(2)
                EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

                val attribList = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 16,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    0x3142, 1, // EGL_RECORDABLE_ANDROID
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
                val eglConfig = configs[0] ?: error("Unable to find recordable EGL config")

                val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
                eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, inputSurface, intArrayOf(EGL14.EGL_NONE), 0)
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

                renderer.initGl()

                muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var trackIndex = -1
                var muxerStarted = false
                val bufferInfo = MediaCodec.BufferInfo()

                fun drainEncoder(endOfStream: Boolean) {
                    if (endOfStream) encoder.signalEndOfInputStream()
                    while (true) {
                        val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                        when {
                            outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) break
                            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                trackIndex = muxer!!.addTrack(encoder.outputFormat)
                                muxer!!.start()
                                muxerStarted = true
                            }
                            outIdx >= 0 -> {
                                val encodedData = encoder.getOutputBuffer(outIdx)
                                if (encodedData != null && bufferInfo.size > 0 && muxerStarted &&
                                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                                ) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer!!.writeSampleData(trackIndex, encodedData, bufferInfo)
                                }
                                encoder.releaseOutputBuffer(outIdx, false)
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                            }
                        }
                    }
                }

                val baseConfig = RenderConfig(scene = scene, showGrid = false, showSkeleton = false, showGizmo = false)
                val nsPerFrame = 1_000_000_000L / fps

                for (i in 0 until totalFrames) {
                    coroutineContext.ensureActive()
                    val frameNum = (startF + i).toFloat()
                    val evaluated = AnimationEvaluator.evaluate(scene, frameNum)
                    renderer.renderSceneFrame(width, height, baseConfig, evaluated, forExport = true)
                    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, i * nsPerFrame)
                    EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                    drainEncoder(false)
                    onProgress((i + 1).toFloat() / totalFrames.toFloat())
                }
                drainEncoder(true)
            } finally {
                renderer.releaseGl()
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
                runCatching { encoder.stop() }
                runCatching { encoder.release() }
                runCatching { inputSurface?.release() }
                runCatching { muxer?.stop() }
                runCatching { muxer?.release() }
            }

            val safeTitle = scene.projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "draftrig_anim" }
            val fileName = "${safeTitle}_${System.currentTimeMillis()}.mp4"
            val outUri = publishVideoToMediaStore(context, tempFile, fileName)
            tempFile.delete()
            outUri to fileName
        }
    }

    private fun publishVideoToMediaStore(context: Context, source: File, fileName: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DraftRig3D")
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create MediaStore video entry")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
            return uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "DraftRig3D").apply { mkdirs() }
            val dest = File(dir, fileName)
            source.copyTo(dest, overwrite = true)
            return Uri.fromFile(dest)
        }
    }
}
