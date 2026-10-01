package com.example.data.telegram

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class CountingRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
    private val isCancelled: () -> Boolean,
    private val isSkipped: () -> Boolean
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long {
        return try {
            delegate.contentLength()
        } catch (e: IOException) {
            -1L
        }
    }

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = contentLength()
        val countingSink = object : ForwardingSink(sink) {
            private var bytesWritten = 0L

            override fun write(source: Buffer, byteCount: Long) {
                if (isCancelled()) {
                    throw CancellationException("Upload cancelado pelo usuário")
                }
                if (isSkipped()) {
                    throw SkipException("Arquivo pulado pelo usuário")
                }
                super.write(source, byteCount)
                bytesWritten += byteCount
                onProgress(bytesWritten, totalBytes)
            }
        }

        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}

class CancellationException(message: String) : IOException(message)
class SkipException(message: String) : IOException(message)
