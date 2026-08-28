package com.vxxtebot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.CycleInterpolator;
import android.widget.TextView;

import java.util.Random;

public class HackerAnimationHelper {

    private static final String GLITCH_CHARS = "01#&$%*<>@!=+/~ABCDEF";
    private static final Random random = new Random();

    /**
     * Cyberpunk Scramble Decryption Animation:
     * Cycles random hacker characters before revealing the resolved target string letter-by-letter.
     */
    public static void animateDecryptionText(final TextView targetView, final String targetText, final Runnable onComplete) {
        if (targetView == null || targetText == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        final int length = targetText.length();
        final int totalFrames = 18;
        final long frameDelayMs = 35;

        final ValueAnimator animator = ValueAnimator.ofInt(0, totalFrames);
        animator.setDuration(totalFrames * frameDelayMs);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int frame = (int) animation.getAnimatedValue();
                int resolvedChars = (int) (((float) frame / totalFrames) * length);

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    char targetChar = targetText.charAt(i);
                    if (Character.isWhitespace(targetChar) || targetChar == '\n') {
                        sb.append(targetChar);
                    } else if (i < resolvedChars) {
                        sb.append(targetChar);
                    } else {
                        sb.append(GLITCH_CHARS.charAt(random.nextInt(GLITCH_CHARS.length())));
                    }
                }
                if (frame < totalFrames) {
                    sb.append(" █");
                }
                targetView.setText(sb.toString());
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                targetView.setText(targetText);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });

        animator.start();
    }

    /**
     * Continuous Breathing / Pulsing Glow Animation for Cyber Shields and Status Indicators.
     */
    public static ObjectAnimator startPulseGlow(View view) {
        if (view == null) return null;
        ObjectAnimator pulse = ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.45f, 1.0f);
        pulse.setDuration(1600);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.start();
        return pulse;
    }

    /**
     * Subtle cyber glitch shake burst for button actions / transmit / purge events.
     */
    public static void animateCyberGlitch(View view) {
        if (view == null) return;
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 0f, 12f, -10f, 8f, -6f, 0f);
        shake.setDuration(350);
        shake.setInterpolator(new CycleInterpolator(1));
        shake.start();
    }

    /**
     * Cyber Card Pop & Fade Slide Entrance Animation.
     */
    public static void animateCardEntrance(View view) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(30f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }
}
