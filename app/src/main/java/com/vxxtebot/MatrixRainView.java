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
 * Ultra-realistic animated Matrix Digital Rain background in Neon Green & Emerald.
 * Features Katakana, Cyber Hex, Binary Glyphs, Variable Stream Speeds, and Glowing Heads.
 */
public class MatrixRainView extends View {

    // Authentic Matrix characters mix (Katakana, Cyber Numbers, Hex, Logic symbols)
    private static final String MATRIX_CHARS = 
            "0101010189ABCDEF01" +
            "ｦｱｳｴｵｶｷｹｺｻｼｽｾｿﾀﾂﾃﾅﾆﾇﾈﾊﾋﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾜ" +
            "<>#%$*@!&[]{}~+=:;?/|";

    private static final int FONT_SIZE_DP = 14;

    private Paint leadPaint;
    private Paint brightPaint;
    private Paint midPaint;
    private Paint trailPaint;

    private int fontSizePx;
    private int columnCount;
    private int[] columnYPositions;
    private int[] columnSpeeds;
    private int[] columnLengths;
    private char[][] columnCharBuffers;

    private final Random random = new Random();
    private boolean isRunning = false;
    private float streamAlphaMultiplier = 0.80f; // High visibility by default

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

        // Falling Stream Head: Ultra-bright White-Lime Glow
        leadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        leadPaint.setTextSize(fontSizePx);
        leadPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        leadPaint.setColor(Color.parseColor("#FFFFFF"));
        leadPaint.setShadowLayer(8f, 0, 0, Color.parseColor("#00FF66"));

        // Upper Stream: Vibrant Neon Matrix Green
        brightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brightPaint.setTextSize(fontSizePx);
        brightPaint.setTypeface(Typeface.MONOSPACE);
        brightPaint.setColor(Color.parseColor("#00FF66"));

        // Mid Stream: Deep Emerald Green
        midPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        midPaint.setTextSize(fontSizePx);
        midPaint.setTypeface(Typeface.MONOSPACE);
        midPaint.setColor(Color.parseColor("#00B344"));

        // Fading Tail: Dark Cyber Shadow Green
        trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trailPaint.setTextSize(fontSizePx);
        trailPaint.setTypeface(Typeface.MONOSPACE);
        trailPaint.setColor(Color.parseColor("#005520"));
    }

    public void setStreamAlpha(float alpha) {
        this.streamAlphaMultiplier = Math.max(0.2f, Math.min(1.0f, alpha));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        columnCount = (w / (fontSizePx + 2)) + 1;
        columnYPositions = new int[columnCount];
        columnSpeeds = new int[columnCount];
        columnLengths = new int[columnCount];
        
        int bufferRows = (h / fontSizePx) + 20;
        columnCharBuffers = new char[columnCount][bufferRows];

        for (int i = 0; i < columnCount; i++) {
            resetColumn(i, true, h);
        }
    }

    private void resetColumn(int col, boolean initial, int viewHeight) {
        int maxRows = (viewHeight / fontSizePx) + 4;
        if (initial) {
            columnYPositions[col] = -random.nextInt(Math.max(1, maxRows * 2));
        } else {
            columnYPositions[col] = -random.nextInt(15);
        }
        columnSpeeds[col] = 1 + random.nextInt(3); // Variable fall speed
        columnLengths[col] = 10 + random.nextInt(18); // Dynamic stream length

        for (int j = 0; j < columnCharBuffers[col].length; j++) {
            columnCharBuffers[col][j] = MATRIX_CHARS.charAt(random.nextInt(MATRIX_CHARS.length()));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (columnYPositions == null || columnCount <= 0) return;

        int viewHeight = getHeight();
        int maxRows = (viewHeight / fontSizePx) + 2;

        leadPaint.setAlpha((int) (255 * streamAlphaMultiplier));
        brightPaint.setAlpha((int) (240 * streamAlphaMultiplier));
        midPaint.setAlpha((int) (180 * streamAlphaMultiplier));
        trailPaint.setAlpha((int) (110 * streamAlphaMultiplier));

        int stepX = fontSizePx + 2;

        for (int i = 0; i < columnCount; i++) {
            int currentHeadY = columnYPositions[i];
            int x = i * stepX;
            int streamLen = columnLengths[i];

            // Render stream characters upwards from head
            for (int r = 0; r < streamLen; r++) {
                int charIndex = currentHeadY - r;
                if (charIndex >= 0 && charIndex < columnCharBuffers[i].length) {
                    float y = charIndex * fontSizePx;
                    char c = columnCharBuffers[i][charIndex];

                    if (r == 0) {
                        // Glowing Falling Head
                        canvas.drawText(String.valueOf(c), x, y, leadPaint);
                    } else if (r <= 3) {
                        // Bright Neon Neck
                        canvas.drawText(String.valueOf(c), x, y, brightPaint);
                    } else if (r <= 8) {
                        // Mid Body
                        canvas.drawText(String.valueOf(c), x, y, midPaint);
                    } else {
                        // Fading Tail
                        canvas.drawText(String.valueOf(c), x, y, trailPaint);
                    }
                }
            }

            // High frequency cyberpunk glyph mutation (random flicker)
            if (random.nextInt(4) == 0 && currentHeadY >= 0 && currentHeadY < columnCharBuffers[i].length) {
                columnCharBuffers[i][currentHeadY] = MATRIX_CHARS.charAt(random.nextInt(MATRIX_CHARS.length()));
            }
            if (random.nextInt(10) == 0) {
                int randomRow = random.nextInt(columnCharBuffers[i].length);
                columnCharBuffers[i][randomRow] = MATRIX_CHARS.charAt(random.nextInt(MATRIX_CHARS.length()));
            }

            // Move stream down
            columnYPositions[i] += columnSpeeds[i];

            // Check if entire stream has exited bottom
            if (columnYPositions[i] - streamLen > maxRows) {
                resetColumn(i, false, viewHeight);
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
