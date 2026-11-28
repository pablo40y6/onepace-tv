package com.pablo40y6.onepacetv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.pablo40y6.onepacetv.PlaybackCatalog.Arc;
import com.pablo40y6.onepacetv.PlaybackCatalog.Episode;
import com.pablo40y6.onepacetv.SubtitleSources.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class CatalogScreen extends LinearLayout {
    interface OnPlay { void open(Episode episode); }

    private static final int BACKGROUND = 0xff0b1525;
    private static final int PANEL = 0xff1b2738;
    private static final int BORDER = 0xff394658;
    private static final int FOCUSED = 0xffffe4a6;
    private static final int GOLD = 0xffffbb48;
    private static final int TEXT = 0xfff8ebd2;
    private static final int MUTED = 0xffd0c8bd;
    private static final int HOME_ARC_LIMIT = 3;
    private static final String POSITION_PREFIX = "position:";
    private static final String SPANISH_PREFERENCE = "spanish-subtitles";

    private PlaybackCatalog catalog;
    private final OnPlay onPlay;
    private final Runnable changeLanguage;
    private final SharedPreferences preferences;
    private final SubtitleSources subtitles;
    private final Set<String> watched;
    private Episode current;
    private Arc browsing;
    private boolean showingArcs;
    private boolean returnToArcs;
    private int homeFocus;

    CatalogScreen(Context context, PlaybackCatalog catalog, SubtitleSources subtitles, OnPlay onPlay,
                  Runnable changeLanguage) {
        super(context);
        this.catalog = catalog;
        this.subtitles = subtitles;
        this.onPlay = onPlay;
        this.changeLanguage = changeLanguage;
        preferences = context.getSharedPreferences("one-pace-tv-progress", Context.MODE_PRIVATE);
        current = catalog.resume(preferences.getString("current", null));
        watched = new HashSet<>(preferences.getStringSet("watched", Collections.emptySet()));
        setOrientation(VERTICAL);
        setBackgroundColor(BACKGROUND);
    }

    void started(Episode episode) {
        current = episode;
        preferences.edit().putString("current", episode.id).apply();
    }

    boolean isHome() {
        return browsing == null && !showingArcs;
    }

    void updateCatalog(PlaybackCatalog replacement) {
        catalog = replacement;
        current = catalog.resume(current.id);
        showHome(homeFocus);
    }

    void completed(Episode episode, boolean spanish) {
        watched.add(episode.id);
        Episode next = catalog.next(episode);
        current = next == null ? episode : next;
        preferences.edit().putString("current", current.id).putStringSet("watched", new HashSet<>(watched))
                .remove(positionKey(episode, spanish)).apply();
    }

    EpisodeProgress position(Episode episode) {
        return position(episode, playbackSource(episode).spanish());
    }

    EpisodeProgress position(Episode episode, boolean spanish) {
        return EpisodeProgress.fromStorage(preferences.getString(positionKey(episode, spanish), null));
    }

    void savePosition(Episode episode, EpisodeProgress progress, boolean spanish) {
        String key = positionKey(episode, spanish);
        if (progress.positionMs == 0) preferences.edit().remove(key).apply();
        else preferences.edit().putString(key, progress.storedValue()).apply();
    }

    private String positionKey(Episode episode, boolean spanish) {
        return POSITION_PREFIX + (spanish ? "es-419:" : "") + episode.id;
    }

    boolean prefersSpanish() {
        return preferences.getBoolean(SPANISH_PREFERENCE, false);
    }

    boolean hasLanguagePreference() {
        return preferences.contains(SPANISH_PREFERENCE);
    }

    void selectLanguage(boolean spanish) {
        preferences.edit().putBoolean(SPANISH_PREFERENCE, spanish).apply();
    }

    Source playbackSource(Episode episode) {
        Source spanish = prefersSpanish() ? subtitles.spanish(episode.id) : null;
        return spanish == null ? episode.englishSource() : spanish;
    }

    void showHome() {
        showHome(0);
    }

    private void showHome(int rememberedFocus) {
        browsing = null;
        showingArcs = false;
        removeAllViews();
        setPadding(0, 0, 0, 0);

        FrameLayout screen = new FrameLayout(getContext());
        addView(screen, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addBackdrop(screen, current.arc);

        LinearLayout content = column();
        content.setPadding(dp(32), dp(20), dp(32), dp(24));
        screen.addView(content, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout hero = column();
        content.addView(hero, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 56));
        hero.addView(text("One Pace TV", 22, TEXT, true));
        LinearLayout story = column();
        story.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(story, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        TextView heading = text(current.arc.title, 52, TEXT, true);
        heading.setMaxWidth(dp(600));
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        story.addView(heading);
        EpisodeProgress position = position(current);
        String language = playbackSource(current).spanish() ? "Subtítulos en español"
                : prefersSpanish() ? "Solo disponible en inglés" : "Subtítulos en inglés";
        String detail = chapterLabel(current) + " · " + language;
        if (position != null) detail += " · " + position.timeLabel();
        addWithTopMargin(story, text(detail, 19, MUTED, false), 5);

        LinearLayout actions = new LinearLayout(getContext());
        addWithTopMargin(story, actions, 17);
        String action = position != null ? "Continuar" : watched.contains(current.id) ? "Volver a ver"
                : preferences.contains("current") ? "Continuar" : "Ver capítulo";
        Button play = button("\u25b6  " + action, true, () -> play(current));
        play.setContentDescription(action);
        Button chapters = button("Capítulos", false, () -> showChapters(current.arc, false));
        actions.addView(play, new LayoutParams(dp(174), dp(46)));
        LayoutParams chapterButton = new LayoutParams(dp(143), dp(46));
        chapterButton.leftMargin = dp(12);
        actions.addView(chapters, chapterButton);
        Button subtitlesButton = button(prefersSpanish() ? "Subtítulos · Español" : "Subtítulos · Inglés", false,
                changeLanguage);
        LayoutParams subtitleParams = new LayoutParams(dp(186), dp(46));
        subtitleParams.leftMargin = dp(12);
        actions.addView(subtitlesButton, subtitleParams);

        LinearLayout covers = column();
        content.addView(covers, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 44));
        LinearLayout sectionHeader = new LinearLayout(getContext());
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView sectionTitle = text("Arcos", 25, TEXT, true);
        sectionHeader.addView(sectionTitle, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        Button all = button("Todos los arcos", false, () -> showArcs(current.arc));
        all.setTextSize(15);
        sectionHeader.addView(all, new LayoutParams(dp(148), dp(36)));
        covers.addView(sectionHeader, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout cards = new LinearLayout(getContext());
        cards.setWeightSum(HOME_ARC_LIMIT);
        covers.addView(cards, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        List<Arc> homeArcs = recentArcs(catalog.arcs(), current.arc);
        View[] focus = new View[homeArcs.size() + 4];
        focus[0] = play;
        focus[1] = chapters;
        focus[2] = subtitlesButton;
        focus[3] = all;
        for (int i = 0; i < homeArcs.size(); i++) {
            Arc arc = homeArcs.get(i);
            View card = arcCard(arc);
            LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            if (i > 0) params.leftMargin = dp(12);
            cards.addView(card, params);
            focus[i + 4] = card;
        }
        wireHomeFocus(focus);
        focus[Math.min(rememberedFocus, focus.length - 1)].requestFocus();
    }

    private static List<Arc> recentArcs(List<Arc> arcs, Arc currentArc) {
        int currentPosition = arcs.indexOf(currentArc);
        int firstVisible = Math.max(0, currentPosition - HOME_ARC_LIMIT + 1);
        return arcs.subList(firstVisible, currentPosition + 1);
    }

    private void addBackdrop(FrameLayout screen, Arc arc) {
        Drawable art = illustration(arc, false);
        if (art == null) {
            View gradient = new View(getContext());
            gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{BACKGROUND, 0xff22314b, BACKGROUND}));
            screen.addView(gradient, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return;
        }
        LinearLayout area = column();
        screen.addView(area, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        FrameLayout hero = new FrameLayout(getContext());
        area.addView(hero, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 72));
        area.addView(new View(getContext()), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 28));
        hero.addView(image(art), new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        overlay(hero, GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BACKGROUND, 0xdc0b1525, 0x2c0b1525, 0x000b1525});
        overlay(hero, GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x400b1525, 0x000b1525, 0x080b1525, BACKGROUND});
    }

    private void overlay(FrameLayout frame, GradientDrawable.Orientation direction, int[] colors) {
        View shade = new View(getContext());
        shade.setBackground(new GradientDrawable(direction, colors));
        frame.addView(shade, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private View arcCard(Arc arc) {
        LinearLayout card = column();
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(arc.title);
        card.setPadding(dp(3), dp(3), dp(3), dp(3));
        card.setBackground(focusBackground(PANEL, GOLD, PANEL));
        card.setClipToOutline(true);
        ImageView cover = image(illustration(arc, false));
        card.addView(cover, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        TextView name = text(arc.title, 18, TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setPadding(dp(12), 0, dp(6), 0);
        card.addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, dp(37)));
        card.setOnClickListener(view -> showChapters(arc, false));
        return card;
    }

    private void wireHomeFocus(View[] controls) {
        for (int i = 0; i < controls.length; i++) {
            final int position = i;
            controls[i].setId(View.generateViewId());
            controls[i].setOnFocusChangeListener((view, focused) -> {
                if (focused) homeFocus = position;
            });
        }
        for (int i = 0; i < 3; i++) {
            controls[i].setNextFocusRightId(controls[i + 1].getId());
            controls[i + 1].setNextFocusLeftId(controls[i].getId());
        }
        for (int i = 0; i < 4; i++) {
            controls[i].setNextFocusDownId(controls[Math.min(i + 4, controls.length - 1)].getId());
        }
        for (int i = 4; i < controls.length; i++) {
            controls[i].setNextFocusUpId(controls[i - 4].getId());
            if (i > 4) controls[i].setNextFocusLeftId(controls[i - 1].getId());
            if (i + 1 < controls.length) controls[i].setNextFocusRightId(controls[i + 1].getId());
        }
    }

    boolean back() {
        if (browsing != null && returnToArcs) showArcs(browsing);
        else if (browsing != null || showingArcs) showHome(homeFocus);
        else return false;
        return true;
    }

    private void showArcs(Arc selected) {
        browsing = null;
        showingArcs = true;
        prepareList();
        addWithTopMargin(this, text("Arcos", 34, TEXT, true), 6);
        List<Arc> arcs = catalog.arcs();
        List<String> labels = new ArrayList<>();
        for (Arc arc : arcs) labels.add(arc.title);
        ListView list = list(new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, recycled, parent);
                row.setTextColor(TEXT);
                row.setTextSize(19);
                row.setMinHeight(dp(51));
                row.setPadding(dp(19), dp(6), dp(16), dp(6));
                row.setBackground(rounded(PANEL, BORDER, 1));
                Drawable thumbnail = illustration(arcs.get(position), true);
                if (thumbnail != null) thumbnail.setBounds(0, 0, dp(72), dp(40));
                row.setCompoundDrawablePadding(dp(13));
                row.setCompoundDrawablesRelative(thumbnail, null, null, null);
                return row;
            }
        }, Math.max(0, arcs.indexOf(selected)));
        list.setOnItemClickListener((parent, row, position, id) -> showChapters(arcs.get(position), true));
    }

    private void showChapters(Arc arc, boolean fromArcs) {
        browsing = arc;
        showingArcs = false;
        returnToArcs = fromArcs;
        prepareList();

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column();
        labels.addView(text(arc.title, 32, TEXT, true));
        addWithTopMargin(labels, text("Capítulos", 18, MUTED, false), 4);
        header.addView(labels, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        Drawable cover = illustration(arc, true);
        if (cover != null) header.addView(image(cover), new LayoutParams(dp(165), dp(83)));
        addWithTopMargin(this, header, 5);

        ListView list = list(new ArrayAdapter<Episode>(getContext(), 0, arc.episodes) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                ChapterRow row = recycled instanceof ChapterRow ? (ChapterRow) recycled : new ChapterRow();
                row.bind(arc.episodes.get(position));
                return row;
            }
        }, current.arc == arc ? current.item : 0);
        list.setOnItemClickListener((parent, row, position, id) -> play(arc.episodes.get(position)));
    }

    private void prepareList() {
        removeAllViews();
        setPadding(dp(32), dp(20), dp(32), dp(20));
        addView(text("One Pace TV", 19, FOCUSED, true));
    }

    private void play(Episode episode) {
        onPlay.open(episode);
    }

    private ListView list(ListAdapter adapter, int selected) {
        ListView list = new ListView(getContext());
        list.setDivider(new ColorDrawable(BACKGROUND));
        list.setDividerHeight(dp(5));
        list.setSelector(rounded(0x1affba45, GOLD, 2));
        list.setDrawSelectorOnTop(true);
        list.setAdapter(adapter);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1);
        params.topMargin = dp(12);
        addView(list, params);
        list.setSelection(selected);
        list.requestFocus();
        return list;
    }

    private final class ChapterRow extends LinearLayout {
        private final TextView label;
        private final ProgressBar bar;

        ChapterRow() {
            super(CatalogScreen.this.getContext());
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(19), dp(8), dp(16), dp(8));
            setMinimumHeight(dp(54));
            setBackground(rounded(PANEL, BORDER, 1));
            label = text("", 19, TEXT, false);
            addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            bar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(EpisodeProgress.BAR_MAX);
            bar.setPadding(0, 0, 0, 0);
            LayerDrawable paint = new LayerDrawable(new Drawable[]{
                    rounded(BORDER, BORDER, 0),
                    new ClipDrawable(rounded(GOLD, GOLD, 0), Gravity.START, ClipDrawable.HORIZONTAL)
            });
            paint.setId(0, android.R.id.background);
            paint.setId(1, android.R.id.progress);
            bar.setProgressDrawable(paint);
            bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams track = new LayoutParams(LayoutParams.MATCH_PARENT, dp(4));
            track.topMargin = dp(6);
            addView(bar, track);
        }

        void bind(Episode episode) {
            EpisodeProgress progress = position(episode);
            boolean seen = watched.contains(episode.id);
            Source source = playbackSource(episode);
            String detail = chapterLabel(episode) + "  ·  " + source.quality;
            if (prefersSpanish()) detail += source.spanish() ? "  ·  Español" : "  ·  Solo inglés";
            if (progress != null) detail += "  ·  A medias  ·  " + progress.timeLabel();
            else if (seen) detail += "  ·  Visto";
            label.setText(detail);
            bar.setProgress(progress != null ? progress.barProgress() : seen ? EpisodeProgress.BAR_MAX : 0);
        }
    }

    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(6), 0, dp(6), 0);
        int[][] states = {{android.R.attr.state_focused}, {}};
        button.setTextColor(new ColorStateList(states, new int[]{BACKGROUND, primary ? BACKGROUND : TEXT}));
        button.setBackground(focusBackground(FOCUSED, GOLD, primary ? 0xffffd999 : PANEL));
        button.setBackgroundTintList(null);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private StateListDrawable focusBackground(int focusFill, int focusStroke, int normal) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_focused}, rounded(focusFill, focusStroke, 3));
        drawable.addState(new int[0], rounded(normal, BORDER, 1));
        return drawable;
    }

    private GradientDrawable rounded(int color, int border, int thickness) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(thickness), border);
        return drawable;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(VERTICAL);
        return column;
    }

    private void addWithTopMargin(LinearLayout parent, View child, int margin) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }

    private TextView text(String label, int size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private ImageView image(Drawable artwork) {
        ImageView view = new ImageView(getContext());
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageDrawable(artwork);
        view.setBackgroundColor(PANEL);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private Drawable illustration(Arc arc, boolean thumbnail) {
        if (!arc.cover.isEmpty()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = thumbnail ? 4 : 1;
            Bitmap cover = BitmapFactory.decodeFile(CatalogRefresher.coverFile(getContext(), arc.cover).getPath(), options);
            if (cover != null) return new BitmapDrawable(getResources(), cover);
        }
        String name = "arc_" + arc.artworkList.toLowerCase(Locale.ROOT);
        if (thumbnail) name += "_thumb";
        int resource = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
        return resource == 0 ? null : getContext().getDrawable(resource);
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private static String chapterLabel(Episode episode) {
        return String.format(Locale.ROOT, "Capítulo %02d", episode.number);
    }
}
