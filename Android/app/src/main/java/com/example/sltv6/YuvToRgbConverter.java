package com.example.sltv6;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class YuvToRgbConverter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public Bitmap convert(@NonNull Image image) {
        int width = image.getWidth();
        int height = image.getHeight();

        byte[] nv21 = imageToNv21(image);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);

        out.reset(); 
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);

        byte[] jpegBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    private static byte[] imageToNv21(@NonNull Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }
}