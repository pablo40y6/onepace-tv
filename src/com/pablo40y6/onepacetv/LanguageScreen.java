package com.pablo40y6.onepacetv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

final class LanguageScreen extends ViewGroup {
    interface OnSelect { void choose(boolean spanish); }

    private static final int ART_WIDTH = 1672;
    private static final int ART_HEIGHT = 941;
    private static final RectF SPANISH = new RectF(293, 544, 641, 674);
    private static final RectF ENGLISH = new RectF(1034, 544, 1380, 674);
    private final Drawable deck;
    private final Button spanish;
    private final Button english;
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leftTrim = trim(false);
    private final Path rightTrim = trim(true);
    private float scale;
    private float artLeft;
    private float artTop;

    LanguageScreen(Context context, OnSelect onSelect) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(0xff091323);
        deck = context.getDrawable(R.drawable.language_deck);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeJoin(Paint.Join.ROUND);
        spanish = plaque("Español latinoamericano", () -> onSelect.choose(true));
        english = plaque("Inglés", () -> onSelect.choose(false));
        spanish.setNextFocusRightId(english.getId());
        english.setNextFocusLeftId(spanish.getId());
    }

    void focusLanguage(boolean useSpanish) {
        (useSpanish ? spanish : english).requestFocus();
    }

    private Button plaque(String label, Runnable onClick) {
        Button button = new Button(getContext());
        button.setId(View.generateViewId());
        button.setContentDescription(label);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setDefaultFocusHighlightEnabled(false);
        button.setStateListAnimator(null);
        button.setOnClickListener(view -> onClick.run());
        button.setOnFocusChangeListener((view, focused) -> invalidate());
        addView(button);
        return button;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        scale = Math.min((float) width / ART_WIDTH, (float) height / ART_HEIGHT);
        artLeft = (width - ART_WIDTH * scale) / 2;
        artTop = (height - ART_HEIGHT * scale) / 2;
        measurePlaque(spanish, SPANISH);
        measurePlaque(english, ENGLISH);
    }

    private void measurePlaque(View plaque, RectF bounds) {
        plaque.measure(MeasureSpec.makeMeasureSpec(Math.round(bounds.width() * scale), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(bounds.height() * scale), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        layoutPlaque(spanish, SPANISH);
        layoutPlaque(english, ENGLISH);
    }

    private void layoutPlaque(View plaque, RectF bounds) {
        int x = Math.round(artLeft + bounds.left * scale);
        int y = Math.round(artTop + bounds.top * scale);
        plaque.layout(x, y, x + plaque.getMeasuredWidth(), y + plaque.getMeasuredHeight());
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(artLeft, artTop);
        canvas.scale(scale, scale);
        deck.setBounds(0, 0, ART_WIDTH, ART_HEIGHT);
        deck.draw(canvas);
        if (spanish.hasFocus()) highlight(canvas, leftTrim);
        else if (english.hasFocus()) highlight(canvas, rightTrim);
        canvas.restore();
    }

    private void highlight(Canvas canvas, Path path) {
        glow.setColor(0x55ff9a25);
        glow.setStrokeWidth(15);
        canvas.drawPath(path, glow);
        glow.setColor(0xccffba42);
        glow.setStrokeWidth(8);
        canvas.drawPath(path, glow);
        glow.setColor(0xffffefb5);
        glow.setStrokeWidth(3);
        canvas.drawPath(path, glow);
    }

    private static Path trim(boolean right) {
        Path edge = new Path();
        edge.moveTo(300, 582);
        edge.quadTo(315, 574, 323, 556);
        edge.quadTo(462, 538, 615, 552);
        edge.quadTo(620, 566, 636, 576);
        edge.lineTo(636, 629);
        edge.quadTo(619, 635, 612, 649);
        edge.quadTo(460, 648, 326, 667);
        edge.quadTo(318, 650, 300, 644);
        edge.close();
        if (right) {
            Matrix mirror = new Matrix();
            mirror.setScale(-1, 1, ART_WIDTH / 2f, 0);
            edge.transform(mirror);
        }
        return edge;
    }
}
