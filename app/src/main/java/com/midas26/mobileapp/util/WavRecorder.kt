package com.midas26.mobileapp.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Android AudioRecord를 이용해 PCM 데이터를 캡처하고
 * WAV 헤더를 붙여 파일로 저장하는 유틸리티.
 *
 * 사용법:
 *   val recorder = WavRecorder(context)
 *   recorder.start()          // 녹음 시작
 *   val file = recorder.stop() // 녹음 종료 → WAV File 반환
 */
class WavRecorder(private val context: Context) {

    private val sampleRate    = 16_000               // 16 kHz — 서버 STT에 적합
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat   = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize    = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        .coerceAtLeast(4096)

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread?  = null
    private var outputFile: File?         = null
    private var isRecording               = false

    /** 녹음을 시작한다. 이미 녹음 중이면 무시. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.wav")
        val file   = outputFile ?: return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelConfig, audioFormat, bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            writeWav(file)
        }.also { it.start() }
    }

    /**
     * 녹음을 중지하고 WAV 파일을 반환한다.
     * @return 저장된 WAV 파일. 녹음 중이 아니었거나 오류 시 null.
     */
    fun stop(): File? {
        if (!isRecording) return null

        isRecording = false
        audioRecord?.stop()
        recordingThread?.join(3_000)   // 최대 3초 대기
        audioRecord?.release()
        audioRecord = null
        recordingThread = null

        return outputFile
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  내부 구현
    // ──────────────────────────────────────────────────────────────────────────

    private fun writeWav(file: File) {
        FileOutputStream(file).use { fos ->
            // 헤더 자리 확보 (44 bytes) — 크기는 녹음 후 업데이트
            fos.write(ByteArray(44))

            val buf   = ByteArray(bufferSize)
            var total = 0
            while (isRecording) {
                val read = audioRecord?.read(buf, 0, buf.size) ?: break
                if (read > 0) {
                    fos.write(buf, 0, read)
                    total += read
                }
            }

            // 남은 데이터 flush
            val lastRead = audioRecord?.read(buf, 0, buf.size) ?: 0
            if (lastRead > 0) {
                fos.write(buf, 0, lastRead)
                total += lastRead
            }

            // WAV 헤더 업데이트
            updateWavHeader(file, total)
        }
    }

    private fun updateWavHeader(file: File, pcmBytes: Int) {
        val channels   = 1
        val bitsPerSample = 16
        val byteRate   = sampleRate * channels * bitsPerSample / 8
        val dataSize   = pcmBytes
        val fileSize   = dataSize + 36          // RIFF 청크 크기

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(buildWavHeader(fileSize, dataSize, byteRate, channels))
        }
    }

    private fun buildWavHeader(
        fileSize: Int,
        dataSize: Int,
        byteRate: Int,
        channels: Int
    ): ByteArray {
        val header = ByteArray(44)
        var idx = 0

        fun writeStr(s: String) { s.forEach { header[idx++] = it.code.toByte() } }
        fun writeInt(v: Int)    { header[idx++] = (v        ).toByte()
                                  header[idx++] = (v shr  8 ).toByte()
                                  header[idx++] = (v shr 16 ).toByte()
                                  header[idx++] = (v shr 24 ).toByte() }
        fun writeShort(v: Int)  { header[idx++] = (v       ).toByte()
                                  header[idx++] = (v shr  8).toByte() }

        writeStr("RIFF")
        writeInt(fileSize)
        writeStr("WAVE")
        writeStr("fmt ")
        writeInt(16)                     // PCM 서브청크 크기
        writeShort(1)                    // AudioFormat = PCM
        writeShort(channels)
        writeInt(sampleRate)
        writeInt(byteRate)
        writeShort(channels * 16 / 8)   // 블록 얼라인
        writeShort(16)                   // 비트 깊이
        writeStr("data")
        writeInt(dataSize)

        return header
    }
}
