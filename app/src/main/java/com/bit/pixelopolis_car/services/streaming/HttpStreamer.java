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

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class HttpStreamer {
    private static final String BOUNDARY = "--gc0p4Jq0M2Yt08jU534c0p--";
    private static final String BOUNDARY_LINES = "\r\n"+BOUNDARY+"\r\n";
    private static final String HTTP_HEADER = "HTTP/1.0 200 OK\r\nServer: Streamer\r\nConnection: close\r\nMax-Age: 0\r\nExpires: 0\r\nCache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\nPragma: no-cache\r\nAccess-Control-Allow-Origin:*\r\nContent-Type: multipart/x-mixed-replace; boundary="+BOUNDARY+"\r\n\r\n"+BOUNDARY+"\r\n";
    private static final String TAG = HttpStreamer.class.getSimpleName();
    private final byte[] bufferA;
    private final byte[] bufferB;
    private final Object bufferLock = new Object();
    private int lengthA = Integer.MIN_VALUE;
    private int lengthB = Integer.MIN_VALUE;
    private boolean jpeg = false;
    private final int port;
    private volatile boolean running = false;
    private boolean streamingBufferA = true;
    private long timestampA = Long.MIN_VALUE;
    private long timestampB = Long.MIN_VALUE;
    private Thread worker = null;

    HttpStreamer(int port, int bufferSize) {
        this.port = port;
        this.bufferA = new byte[bufferSize];
        this.bufferB = new byte[bufferSize];
    }

    public void start() {
        if (this.running) {
            throw new IllegalStateException("HttpStreamer is already running");
        }
        this.running = true;
        this.worker = new Thread(new Runnable() {
            public void run() {
                HttpStreamer.this.workerRun();
            }
        });
        this.worker.start();
    }

    public void stop() {
        if (!this.running) {
            throw new IllegalStateException("HttpStreamer is already stopped");
        }
        this.running = false;
        this.worker.interrupt();
    }

    public void streamJpeg(byte[] jpeg, int length, long timestamp) {
        byte[] buffer;
        synchronized (this.bufferLock) {
            if (this.streamingBufferA) {
                buffer = this.bufferB;
                this.lengthB = length;
                this.timestampB = timestamp;
            } else {
                buffer = this.bufferA;
                this.lengthA = length;
                this.timestampA = timestamp;
            }
            System.arraycopy(jpeg, 0, buffer, 0, length);
            this.jpeg = true;
            this.bufferLock.notify();
        }
    }

    public void workerRun() {
        while (this.running) {
            try {
                acceptAndStream();
            } catch (IOException exceptionWhileStreaming) {
                System.err.println(exceptionWhileStreaming);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    private void acceptAndStream() throws IOException , Throwable{
        byte[] buffer;
        int length;
        long timestamp;
        ServerSocket serverSocket = null;
        Socket socket = null;
        DataOutputStream stream = null;
        Throwable th;
        try {
            ServerSocket serverSocket2 = new ServerSocket(this.port);
            try {
                serverSocket2.setSoTimeout(1000);
                do {
                    socket = serverSocket2.accept();
                    continue;
                } while (socket == null);
            } catch (SocketTimeoutException e) {
                if (!this.running) {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (IOException closingStream) {
                            System.err.println(closingStream);
                        }
                    }
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException closingSocket) {
                            System.err.println(closingSocket);
                        }
                    }
                    if (serverSocket2 != null) {
                        try {
                            serverSocket2.close();
                        } catch (IOException closingServerSocket) {
                            System.err.println(closingServerSocket);
                        }
                    }
                    ServerSocket serverSocket3 = serverSocket2;
                    return;
                }
            } catch (Throwable th1) {
                th = th1;
                serverSocket = serverSocket2;
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException closingStream2) {
                        System.err.println(closingStream2);
                    }
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException closingSocket2) {
                        System.err.println(closingSocket2);
                    }
                }
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException closingServerSocket2) {
                        System.err.println(closingServerSocket2);
                    }
                }
                throw th;
            }
            serverSocket2.close();
            serverSocket = null;
            DataOutputStream stream2 = new DataOutputStream(socket.getOutputStream());
            try {
                stream2.writeBytes(HTTP_HEADER);
                stream2.flush();
                while (this.running) {
                    synchronized (this.bufferLock) {
                        while (!this.jpeg) {
                            try {
                                this.bufferLock.wait();
                            } catch (InterruptedException e2) {
                                if (stream2 != null) {
                                    try {
                                        stream2.close();
                                    } catch (IOException closingStream3) {
                                        System.err.println(closingStream3);
                                    }
                                }
                                if (socket != null) {
                                    try {
                                        socket.close();
                                    } catch (IOException closingSocket3) {
                                        System.err.println(closingSocket3);
                                    }
                                }
                                if (serverSocket != null) {
                                    try {
                                        serverSocket.close();
                                    } catch (IOException closingServerSocket3) {
                                        System.err.println(closingServerSocket3);
                                    }
                                }
                                DataOutputStream dataOutputStream = stream2;
                                return;
                            }
                        }
                        this.streamingBufferA = !this.streamingBufferA;
                        if (this.streamingBufferA) {
                            buffer = this.bufferA;
                            length = this.lengthA;
                            timestamp = this.timestampA;
                        } else {
                            buffer = this.bufferB;
                            length = this.lengthB;
                            timestamp = this.timestampB;
                        }
                        this.jpeg = false;
                    }
                    stream2.writeBytes("Content-type: image/jpeg\r\nContent-Length: " + length + "\r\nX-Timestamp:" + timestamp + "\r\n\r\n");
                    stream2.write(buffer, 0, length);
                    stream2.writeBytes(BOUNDARY_LINES);
                    stream2.flush();
                }
                if (stream2 != null) {
                    try {
                        stream2.close();
                    } catch (IOException closingStream4) {
                        System.err.println(closingStream4);
                    }
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException closingSocket4) {
                        System.err.println(closingSocket4);
                    }
                }
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException closingServerSocket4) {
                        System.err.println(closingServerSocket4);
                    }
                }
                DataOutputStream dataOutputStream2 = stream2;
            } catch (Throwable th2) {
                th = th2;
                stream = stream2;
            }
        } catch (Throwable th3) {
            th = th3;
            if (stream != null) {
            }
            if (socket != null) {
            }
            if (serverSocket != null) {
            }
            throw th;
        }
    }
}