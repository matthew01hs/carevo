package com.example.sltv6;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;


import androidx.annotation.NonNull;

import com.google.mediapipe.tasks.components.containers.Connection;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;

import java.util.List;

public class OverlayView extends View {

    private List<List<NormalizedLandmark>> results;

    private final Paint linePaint = new Paint();
    private final Paint pointPaint = new Paint();

    private float scaleFactor = 1f;
    private int imageWidth = 1;
    private int imageHeight = 1;

    private boolean mirrorX = false;

    private boolean skeletonEnabled = true;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {

        linePaint.setColor(Color.parseColor("#f2f2f2"));
        linePaint.setStrokeWidth(7f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        pointPaint.setColor(Color.parseColor("#262626"));
        pointPaint.setStrokeWidth(8f);
        pointPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        pointPaint.setAntiAlias(true);
    }

    public void clear() {
        results = null;
        invalidate();
    }

    public void setMirror(boolean mirror) {
        mirrorX = mirror;
        invalidate();
    }

    
    public void setResults(
            List<List<NormalizedLandmark>> handLandmarks,
            int imageHeight,
            int imageWidth
    ) {
        this.results = handLandmarks;
        this.imageHeight = imageHeight;
        this.imageWidth = imageWidth;

        scaleFactor = Math.max(
                getWidth() * 1f / imageWidth,
                getHeight() * 1f / imageHeight
        );

        invalidate();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);

        if (!skeletonEnabled) return;

        if (results == null) return;

        for (List<NormalizedLandmark> handLandmarks : results) {

            for (Connection connection : HandLandmarker.HAND_CONNECTIONS) {

                int startIndex = connection.start();
                int endIndex = connection.end();

                NormalizedLandmark start = handLandmarks.get(startIndex);
                NormalizedLandmark end = handLandmarks.get(endIndex);

                float startX = start.x() * imageWidth * scaleFactor;
                float startY = start.y() * imageHeight * scaleFactor;
                float endX = end.x() * imageWidth * scaleFactor;
                float endY = end.y() * imageHeight * scaleFactor;

                if (mirrorX) {
                    startX = getWidth() - startX;
                    endX = getWidth() - endX;
                }

                canvas.drawLine(startX, startY, endX, endY, linePaint);
            }

            for (NormalizedLandmark landmark : handLandmarks) {
                float x = landmark.x() * imageWidth * scaleFactor;
                float y = landmark.y() * imageHeight * scaleFactor;

                if (mirrorX) {
                    x = getWidth() - x;
                }

                canvas.drawCircle(x, y, 4f, pointPaint);
            }

        }
    }

    public void setSkeletonEnabled(boolean enabled) {
        this.skeletonEnabled = enabled;
        invalidate();
    }
}