@file:JvmName("AndroidMediaDevices")

package com.shepeliev.webrtckmp

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.shepeliev.webrtckmp.capturer.CameraVideoCapturerController
import com.shepeliev.webrtckmp.capturer.ScreenCapturerController
import org.webrtc.MediaConstraints
import java.util.UUID

internal actual val mediaDevices: MediaDevices = MediaDevicesImpl

private object MediaDevicesImpl : MediaDevices {
    override suspend fun getUserMedia(
        streamConstraints: MediaStreamConstraintsBuilder.() -> Unit,
    ): MediaStream {
        val constraints =
            MediaStreamConstraintsBuilder().let {
                streamConstraints(it)
                it.constraints
            }

        var audioTrack: AudioTrack? = null
        if (constraints.audio != null) {
            checkRecordAudioPermission()
            val mediaConstraints =
                MediaConstraints().apply {
                    mandatory.addAll(
                        constraints.audio
                            .toMandatoryMap()
                            .map { (k, v) -> MediaConstraints.KeyValuePair("$k", "$v") },
                    )
                    optional.addAll(
                        constraints.audio
                            .toOptionalMap()
                            .map { (k, v) -> MediaConstraints.KeyValuePair("$k", "$v") },
                    )
                }
            val audioSource = WebRtc.peerConnectionFactory.createAudioSource(mediaConstraints)
            val androidTrack =
                WebRtc.peerConnectionFactory.createAudioTrack(
                    UUID.randomUUID().toString(),
                    audioSource,
                )
            audioTrack = LocalAudioTrack(androidTrack, audioSource, constraints.audio)
        }

        var videoTrack: LocalVideoTrack? = null
        if (constraints.video != null) {
            checkCameraPermission()
            val videoSource = WebRtc.peerConnectionFactory.createVideoSource(false)
            videoSource.setVideoProcessor(WebRtc.videoProcessorFactory?.createVideoProcessor())
            val videoCaptureController =
                CameraVideoCapturerController(constraints.video, videoSource)
            val androidTrack =
                WebRtc.peerConnectionFactory.createVideoTrack(
                    UUID.randomUUID().toString(),
                    videoSource,
                )
            videoTrack = LocalVideoTrack(androidTrack, videoCaptureController)
        }

        return MediaStream().apply {
            if (audioTrack != null) addTrack(audioTrack)
            if (videoTrack != null) addTrack(videoTrack)
        }
    }

    override suspend fun getDisplayMedia(): MediaStream {
        val videoSource = WebRtc.peerConnectionFactory.createVideoSource(false)
        WebRtc.videoProcessorFactory?.createVideoProcessor()?.let {
            videoSource.setVideoProcessor(it)
        }
        val screenCaptureController = ScreenCapturerController(videoSource)
        val videoTrack =
            WebRtc.peerConnectionFactory.createVideoTrack(
                UUID.randomUUID().toString(),
                videoSource,
            )
        val videoStreamTrack = LocalVideoTrack(videoTrack, screenCaptureController)
        return MediaStream().apply { addTrack(videoStreamTrack) }
    }

    override suspend fun supportsDisplayMedia(): Boolean = true

    private fun checkRecordAudioPermission() {
        val result =
            ContextCompat.checkSelfPermission(
                WebRtc.applicationContext,
                Manifest.permission.RECORD_AUDIO,
            )
        if (result != PackageManager.PERMISSION_GRANTED) throw RecordAudioPermissionException()
    }

    private fun checkCameraPermission() {
        val result =
            ContextCompat.checkSelfPermission(
                WebRtc.applicationContext,
                Manifest.permission.CAMERA,
            )
        if (result != PackageManager.PERMISSION_GRANTED) throw CameraPermissionException()
    }

    override suspend fun enumerateDevices(): List<MediaDeviceInfo> =
        WebRtc.cameraEnumerator.deviceNames.map {
            MediaDeviceInfo(deviceId = it, label = it, kind = MediaDeviceKind.VideoInput)
        }
}
