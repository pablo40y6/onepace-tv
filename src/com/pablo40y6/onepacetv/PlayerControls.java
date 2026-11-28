package com.pablo40y6.onepacetv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.IntConsumer;

final class PlayerControls extends LinearLayout {
    private static final int SHORT_REWIND = 2;
    private static final int NEXT = 5;
    private static final int INTRO = 6;
    private final Button[] buttons = new Button[7];
    private final LinearLayout buttonRow;
    private final LinearLayout timelineRow;
    private final SeekBar timeline;
    private final TextView elapsed;
    private final TextView durationLabel;
    private final TextView introHint;
    private final LinearLayout nextPrompt;
    private final TextView nextLabel;
    private final Button cancelButton;
    private boolean introAvailable;
    private int focused;
    private int countdownSeconds;

    PlayerControls(Context context, Runnable previous, IntConsumer seek, Runnable next, Runnable skipIntro,
                   Runnable cancelNext) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(9), dp(9), dp(9), dp(9));
        setBackground(rounded(0xe619191e));
        buttonRow = new LinearLayout(context);
        buttonRow.setGravity(Gravity.CENTER);
        addView(buttonRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        add(0, "Capítulo anterior", previous);
        add(1, "−1 min", () -> seek.accept(-60));
        add(SHORT_REWIND, "−10 s", () -> seek.accept(-10));
        add(3, "+10 s", () -> seek.accept(10));
        add(4, "+1 min", () -> seek.accept(60));
        add(NEXT, "Capítulo siguiente", next);
        add(INTRO, "Saltar intro", skipIntro);
        buttons[INTRO].setEnabled(false);
        buttons[INTRO].setVisibility(View.GONE);

        timelineRow = new LinearLayout(context);
        timelineRow.setGravity(Gravity.CENTER_VERTICAL);
        timelineRow.setPadding(dp(8), 0, dp(8), 0);
        timelineRow.setBackground(rounded(0xff262630));
        timelineRow.setVisibility(View.GONE);
        LayoutParams track = new LayoutParams(LayoutParams.MATCH_PARENT, dp(44));
        track.setMargins(dp(4), dp(7), dp(4), 0);
        addView(timelineRow, track);
        elapsed = timelineLabel();
        timelineRow.addView(elapsed, new LayoutParams(dp(78), LayoutParams.MATCH_PARENT));

        timeline = new SeekBar(context);
        timeline.setMax(1000);
        timeline.setFocusable(true);
        timeline.setContentDescription("Posición del capítulo");
        timeline.setProgressTintList(ColorStateList.valueOf(0xffffe89b));
        timeline.setProgressBackgroundTintList(ColorStateList.valueOf(0xff666671));
        timeline.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{0xffffe89b, 0xffc4bfad}));
        timeline.setSplitTrack(false);
        timeline.setOnFocusChangeListener((view, focused) -> timelineRow.setBackground(rounded(
                focused ? 0xff3b3b48 : 0xff262630, focused ? 0xffffe89b : 0)));
        timelineRow.addView(timeline, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        durationLabel = timelineLabel();
        timelineRow.addView(durationLabel, new LayoutParams(dp(78), LayoutParams.MATCH_PARENT));
        setVisibility(View.GONE);

        introHint = new TextView(context);
        introHint.setText("↓  Saltar intro");
        introHint.setTextColor(0xff18130c);
        introHint.setTextSize(18);
        introHint.setGravity(Gravity.CENTER);
        introHint.setPadding(dp(18), 0, dp(18), 0);
        introHint.setBackground(rounded(0xffffe89b));
        introHint.setVisibility(View.GONE);

        nextPrompt = new LinearLayout(context);
        nextPrompt.setGravity(Gravity.CENTER_VERTICAL);
        nextPrompt.setPadding(dp(14), dp(7), dp(7), dp(7));
        nextPrompt.setBackground(rounded(0xe619191e));
        nextPrompt.setVisibility(View.GONE);
        nextLabel = new TextView(context);
        nextLabel.setTextColor(0xfff8f7f5);
        nextLabel.setTextSize(18);
        nextLabel.setPadding(0, 0, dp(15), 0);
        nextPrompt.addView(nextLabel);
        cancelButton = styledButton("↓  Cancelar", cancelNext);
        cancelButton.setContentDescription("Cancelar el siguiente capítulo");
        nextPrompt.addView(cancelButton, new LayoutParams(dp(126), dp(43)));
    }

    void attachTo(FrameLayout root) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        params.setMargins(dp(44), 0, dp(44), dp(23));
        root.addView(this, params);

        FrameLayout.LayoutParams hint = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dp(46), Gravity.BOTTOM | Gravity.END);
        hint.setMargins(0, 0, dp(44), dp(23));
        root.addView(introHint, hint);

        FrameLayout.LayoutParams countdown = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        countdown.setMargins(0, 0, dp(44), dp(102));
        root.addView(nextPrompt, countdown);
    }

    boolean visible() {
        return getVisibility() == View.VISIBLE;
    }

    void open(boolean hasPrevious, boolean hasNext) {
        buttons[0].setEnabled(hasPrevious);
        buttons[NEXT].setEnabled(hasNext);
        setVisibility(View.VISIBLE);
        bringToFront();
        focus(introAvailable ? INTRO : SHORT_REWIND);
        showIntroHint();
        positionCountdown();
    }

    void close() {
        setVisibility(View.GONE);
        showIntroHint();
        positionCountdown();
    }

    void updateTimeline(double time, double duration, double preview) {
        boolean available = Double.isFinite(time) && Double.isFinite(duration) && duration > 0;
        boolean hadTimeline = timelineRow.getVisibility() == View.VISIBLE;
        if (available != hadTimeline) {
            if (!available && timelineFocused()) focusMenu();
            timelineRow.setVisibility(available ? View.VISIBLE : View.GONE);
            positionCountdown();
        }
        if (!available) return;

        double position = Double.isFinite(preview) ? preview : time;
        int progress = (int) Math.round(1000 * Math.max(0, Math.min(position, duration)) / duration);
        timeline.setProgress(progress);
        String currentTime = EpisodeProgress.timeLabel((long) position);
        String totalTime = EpisodeProgress.timeLabel((long) duration);
        if (!currentTime.contentEquals(elapsed.getText())) elapsed.setText(currentTime);
        if (!totalTime.contentEquals(durationLabel.getText())) durationLabel.setText(totalTime);
        int color = Double.isFinite(preview) ? 0xffffe89b : 0xfff8f7f5;
        if (elapsed.getCurrentTextColor() != color) elapsed.setTextColor(color);
    }

    boolean timelineFocused() {
        return timeline.hasFocus();
    }

    boolean focusTimeline() {
        return timelineRow.getVisibility() == View.VISIBLE && timeline.requestFocus();
    }

    private void positionCountdown() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) nextPrompt.getLayoutParams();
        int bottom = dp(visible() && timelineRow.getVisibility() == View.VISIBLE ? 153 : 102);
        if (params.bottomMargin != bottom) {
            params.bottomMargin = bottom;
            nextPrompt.setLayoutParams(params);
        }
    }

    void showCountdown(int seconds) {
        if (countdownSeconds == seconds) return;
        countdownSeconds = seconds;
        if (seconds <= 0) {
            nextPrompt.setVisibility(View.GONE);
        } else {
            nextLabel.setText(String.format(Locale.ROOT, "Siguiente capítulo · %d s", seconds));
            if (!countdownVisible()) {
                nextPrompt.setVisibility(View.VISIBLE);
                nextPrompt.bringToFront();
            }
        }
    }

    boolean countdownVisible() {
        return nextPrompt.getVisibility() == View.VISIBLE;
    }

    boolean cancelFocused() {
        return countdownVisible() && cancelButton.hasFocus();
    }

    void focusCancel() {
        cancelButton.requestFocus();
    }

    void activateCancel() {
        cancelButton.performClick();
    }

    void focusMenu() {
        focus(focused);
    }

    void setIntroAvailable(boolean available) {
        if (introAvailable == available) return;
        boolean restoreFocus = !available && visible() && buttons[INTRO].hasFocus();
        introAvailable = available;
        if (!available && focused == INTRO) focused = SHORT_REWIND;
        buttons[INTRO].setEnabled(available);
        buttons[INTRO].setVisibility(available ? View.VISIBLE : View.GONE);
        if (restoreFocus) focus(SHORT_REWIND);
        showIntroHint();
    }

    private void showIntroHint() {
        boolean show = introAvailable && !visible();
        introHint.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) introHint.bringToFront();
    }

    private void focus(int index) {
        buttons[index].requestFocus();
        focused = index;
    }

    void move(int direction) {
        for (int target = focused + direction; target >= 0 && target < buttons.length; target += direction) {
            if (buttons[target].isEnabled()) {
                focus(target);
                return;
            }
        }
    }

    void activate() {
        if (buttons[focused].isEnabled()) buttons[focused].performClick();
    }

    private void add(int index, String label, Runnable action) {
        Button button = styledButton(label, action);
        button.setOnFocusChangeListener((view, hasFocus) -> { if (hasFocus) focused = index; });
        float weight = index == 0 || index == NEXT || index == INTRO ? 1.6f : 1;
        LayoutParams params = new LayoutParams(0, dp(52), weight);
        params.setMargins(dp(4), 0, dp(4), 0);
        buttonRow.addView(button, params);
        buttons[index] = button;
    }

    private TextView timelineLabel() {
        TextView label = new TextView(getContext());
        label.setTextSize(17);
        label.setTextColor(0xfff8f7f5);
        label.setGravity(Gravity.CENTER);
        return label;
    }

    private Button styledButton(String label, Runnable action) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
        button.setGravity(Gravity.CENTER);
        int[][] states = {{-android.R.attr.state_enabled}, {android.R.attr.state_focused}, {}};
        button.setTextColor(new ColorStateList(states, new int[]{0xffababaf, 0xff18130c, 0xfff8f7f5}));
        StateListDrawable background = new StateListDrawable();
        background.addState(states[0], rounded(0xff27272b));
        background.addState(states[1], rounded(0xffffe89b));
        background.addState(states[2], rounded(0xff35353d));
        button.setBackground(background);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private GradientDrawable rounded(int color) {
        return rounded(color, 0);
    }

    private GradientDrawable rounded(int color, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        if (stroke != 0) drawable.setStroke(dp(2), stroke);
        return drawable;
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }
}
