/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bit.pixelopolis_car.services.streaming;

import com.bit.pixelopolis_car.services.camera.PixelCamera;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.IOException;

public class CameraStreamer {

    public class MovingAverage {
        private int end = 0;
        private int length = 0;
        private final int numValues;
        private long sum = 0;
        private final long[] values;

        MovingAverage(int numValues) {
            this.numValues = numValues;
            this.values = new long[numValues];
        }

        public void update(long value) {
            this.sum -= this.values[this.end];
            this.values[this.end] = value;
            this.end = (this.end + 1) % this.numValues;
            if (this.length < this.numValues) {
                this.length++;
            }
            this.sum += value;
        }

        public double getAverage() {
            return ((double) this.sum) / ((double) this.length);
        }
    }

    private static final long OPEN_CAMERA_POLLING_INTERVAL_MS = 1000;
    private static final String TAG = CameraStreamer.class.getSimpleName();
    private long lastTimestamp = Long.MIN_VALUE;
    private long numFrames = 0;
    private final PixelCamera.StreamFrameCallback streamFrameCallback = (Mat frame, PixelCamera camera) -> {
        if(this.streaming) {
            long timestamp = SystemClock.elapsedRealtime();
            Message message = CameraStreamer.this.workHandler.obtainMessage();
            message.what = 1;
            message.obj = new Object[]{frame, timestamp};
            message.sendToTarget();
        }
    };

    private final MovingAverage averageSpf = new MovingAverage(50);
    private PixelCamera pixelCamera = null;
    private BufferStream outputStream = null;
    private final int quality;
    private final Object lock = new Object();
    private Looper looper = null;
    private HttpStreamer httpStreamer = null;
    private final int port;
    private int previewBufferSize = Integer.MIN_VALUE;
    private int previewFormat = Integer.MIN_VALUE;
    private int previewHeight = Integer.MIN_VALUE;
    private Rect previewRect = null;
    private int previewWidth = Integer.MIN_VALUE;
    private boolean running = false;
    private boolean streaming = false;
    private Bitmap bmp;
    public Handler workHandler = null;

    private final class WorkHandler extends Handler {
        private WorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            switch (message.what) {
                case 0:
                    CameraStreamer.this.tryStartStreaming();
                    return;
                case 1:
                    Object[] args = (Object[]) message.obj;
                    CameraStreamer.this.sendPreviewFrame((Mat) args[0], ((Long) args[1]).longValue());
                    return;
                default:
                    throw new IllegalArgumentException("cannot handle message");
            }
        }
    }

    public CameraStreamer(int port, int jpegQuality, PixelCamera camera) {
        if (camera == null) {
            throw new IllegalArgumentException("camera must not be null");
        }
        this.port = port;
        this.quality = jpegQuality;
        this.pixelCamera = camera;
    }

    public void start() {
        synchronized (this.lock) {
            if (this.running) {
                throw new IllegalStateException("Streamer already running");
            }
            this.running = true;
        }
        HandlerThread worker = new HandlerThread(TAG, -1);
        worker.setDaemon(true);
        worker.start();
        this.looper = worker.getLooper();
        this.workHandler = new WorkHandler(this.looper);
        this.workHandler.obtainMessage(0).sendToTarget();
    }

    public void stop() {
        synchronized (this.lock) {
            if (!this.running) {
                throw new IllegalStateException("Streamer already stopped");
            }
            this.running = false;
            if (this.httpStreamer != null) {
                this.httpStreamer.stop();
            }
            this.streaming = false;
            pixelCamera.setStreamFrameCallback(null);
        }
        this.looper.quit();
    }

    public void tryStartStreaming() {
        while (true) {
            try {
                startStreamingIfRunning();
                break;
            } catch (RuntimeException openCameraFailed) {
                try {
                    Log.d(TAG, "Open camera failed, retying in 1000ms", openCameraFailed);
                    Thread.sleep(OPEN_CAMERA_POLLING_INTERVAL_MS);
                } catch (Exception startPreviewFailed) {
                    Log.w(TAG, "Failed to start camera preview", startPreviewFailed);
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startStreamingIfRunning() throws IOException {

        this.previewFormat = ImageFormat.FLEX_RGB_888;
        this.previewWidth = pixelCamera.getStreamingFrame().width();
        this.previewHeight = pixelCamera.getStreamingFrame().height();
        this.previewBufferSize = ((((this.previewWidth * this.previewHeight) * (ImageFormat.getBitsPerPixel(this.previewFormat) / 8)) * 3) / 2) + 1;
        this.previewRect = new Rect(0, 0, this.previewWidth, this.previewHeight);
        this.bmp = Bitmap.createBitmap(this.previewWidth,this.previewHeight, Bitmap.Config.ARGB_8888);
        this.pixelCamera.setStreamFrameCallback(this.streamFrameCallback);
        this.outputStream = new BufferStream(this.previewBufferSize);
        HttpStreamer streamer = new HttpStreamer(this.port, this.previewBufferSize);
        streamer.start();
        synchronized (this.lock) {
            if (!this.running) {
                streamer.stop();
                this.streaming = false;
                return;
            }

            this.httpStreamer = streamer;
            this.streaming = true;

        }
    }

    public void sendPreviewFrame(Mat img, long timestamp) {
        long timestampSeconds = timestamp / OPEN_CAMERA_POLLING_INTERVAL_MS;
        this.numFrames++;
        if (this.lastTimestamp != Long.MIN_VALUE) {
            this.averageSpf.update(timestampSeconds - this.lastTimestamp);
            if (this.numFrames % 10 == 9) {
                Log.d(TAG, "FramePerSecond= " + (1.0d / this.averageSpf.getAverage()));
            }
        }
        this.lastTimestamp = timestampSeconds;
        Utils.matToBitmap(img, bmp);
        bmp.compress(Bitmap.CompressFormat.JPEG, this.quality,this.outputStream);
        this.httpStreamer.streamJpeg(this.outputStream.getBuffer(), this.outputStream.getLength(), timestamp);
        this.outputStream.seek(0);
    }
}
