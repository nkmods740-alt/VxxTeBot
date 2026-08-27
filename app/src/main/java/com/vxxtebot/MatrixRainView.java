package com.vxxtebot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * High-performance animated Matrix Digital Rain background view in Cyber Green / Neon Cyan.
 */
public class MatrixRainView extends View {

    private static final String CHARACTERS = "010101ABCDEF9876543210<>#%$*@!&[]{}~+=";
    private static final int FONT_SIZE_DP = 13;

    private Paint leadPaint;
    private Paint bodyPaint;
    private Paint trailPaint;
    
    private int fontSizePx;
    private int columnCount;
    private int[] columnYPositions;
    private int[] columnSpeeds;
    private char[][] columnCharBuffers;
    
    private final Random random = new Random();
    private boolean isRunning = false;
    private float streamAlphaMultiplier = 0.35f;

    public MatrixRainView(Context context) {
        super(context);
        init();
    }

    public MatrixRainView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MatrixRainView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        fontSizePx = (int) (FONT_SIZE_DP * getResources().getDisplayMetrics().density);

        // Lead falling character: Bright White / Neon Green head
        leadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        leadPaint.setTextSize(fontSizePx);
        leadPaint.setTypeface(Typeface.MONOSPACE);
        leadPaint.setColor(Color.parseColor("#E8FFF0"));

        // Main body character: Matrix Bright Green
        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setTextSize(fontSizePx);
        bodyPaint.setTypeface(Typeface.MONOSPACE);
        bodyPaint.setColor(Color.parseColor("#00FF66"));

        // Fading trail character: Darker Cyber Green / Cyan
        trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trailPaint.setTextSize(fontSizePx);
        trailPaint.setTypeface(Typeface.MONOSPACE);
        trailPaint.setColor(Color.parseColor("#009944"));
    }

    public void setStreamAlpha(float alpha) {
        this.streamAlphaMultiplier = Math.max(0.1f, Math.min(1.0f, alpha));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        columnCount = (w / fontSizePx) + 1;
        columnYPositions = new int[columnCount];
        columnSpeeds = new int[columnCount];
        columnCharBuffers = new char[columnCount][(h / fontSizePx) + 4];

        for (int i = 0; i < columnCount; i++) {
            columnYPositions[i] = -random.nextInt(Math.max(1, h / fontSizePx));
            columnSpeeds[i] = 1 + random.nextInt(2);
            for (int j = 0; j < columnCharBuffers[i].length; j++) {
                columnCharBuffers[i][j] = CHARACTERS.charAt(random.nextInt(CHARACTERS.length()));
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (columnYPositions == null || columnCount <= 0) return;

        int viewHeight = getHeight();
        int maxRows = (viewHeight / fontSizePx) + 2;

        leadPaint.setAlpha((int) (240 * streamAlphaMultiplier));
        bodyPaint.setAlpha((int) (200 * streamAlphaMultiplier));
        trailPaint.setAlpha((int) (90 * streamAlphaMultiplier));

        for (int i = 0; i < columnCount; i++) {
            int currentY = columnYPositions[i];
            int x = i * fontSizePx;

            // Draw fading trail and head characters
            for (int row = 0; row < maxRows; row++) {
                int charRow = currentY - row;
                if (charRow >= 0 && charRow < columnCharBuffers[i].length) {
                    float y = charRow * fontSizePx;
                    char c = columnCharBuffers[i][charRow];

                    if (row == 0) {
                        // Falling Head
                        canvas.drawText(String.valueOf(c), x, y, leadPaint);
                    } else if (row < 4) {
                        // Mid Trail
                        canvas.drawText(String.valueOf(c), x, y, bodyPaint);
                    } else if (row < 10) {
                        // Fading Tail
                        canvas.drawText(String.valueOf(c), x, y, trailPaint);
                    }
                }
            }

            // Occasionally mutate a random character for cyber flicker effect
            if (random.nextInt(6) == 0 && currentY >= 0 && currentY < columnCharBuffers[i].length) {
                columnCharBuffers[i][currentY] = CHARACTERS.charAt(random.nextInt(CHARACTERS.length()));
            }

            // Advance stream position
            columnYPositions[i] += columnSpeeds[i];

            // Reset when reaching bottom
            if (columnYPositions[i] - 12 > maxRows) {
                columnYPositions[i] = -random.nextInt(6);
                columnSpeeds[i] = 1 + random.nextInt(2);
            }
        }

        if (isRunning) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isRunning = true;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isRunning = false;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        isRunning = (visibility == VISIBLE);
        if (isRunning) {
            invalidate();
        }
    }
}
