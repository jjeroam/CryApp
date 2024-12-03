package com.example.babycry.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MJPEGInputStream {
    private final InputStream inputStream;
    private static final int START_MARKER = 0xFF;
    private static final int SOI_MARKER = 0xD8; // Start of Image
    private static final int EOI_MARKER = 0xD9; // End of Image

    public MJPEGInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public Bitmap readFrame() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int prev = 0, cur;

        boolean startOfFrame = false;

        // Read bytes until the start and end markers of the JPEG frame are detected
        while ((cur = inputStream.read()) != -1) {
            if (prev == START_MARKER && cur == SOI_MARKER) {
                // Start of a new JPEG frame
                startOfFrame = true;
                buffer.write(prev); // Write START_MARKER
            }

            if (startOfFrame) {
                buffer.write(cur);
            }

            if (prev == START_MARKER && cur == EOI_MARKER) {
                // End of the current JPEG frame
                break;
            }

            prev = cur;
        }

        // Decode the JPEG frame into a Bitmap
        byte[] frameBytes = buffer.toByteArray();
        if (frameBytes.length > 0) {
            return BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.length);
        } else {
            return null; // No valid frame detected
        }
    }

    public void close() throws IOException {
        inputStream.close();
    }
}
