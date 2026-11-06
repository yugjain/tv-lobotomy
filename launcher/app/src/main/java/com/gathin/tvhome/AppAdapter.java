package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order,
 * then empty slots up to a full row, see {@link DockSlots}), then the module cards the owner chose to show
 * (by label), the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);
    }

    static final class Entry {
        static final int APP = 0, HOME_SETTINGS = 1, CARD = 2, EMPTY = 3;

        final int kind;
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /** Text under the tile when it is focused in the grid: the label, plus the caption for a card. */
        final String name;
        /** The app declares its own icon; false means the system's blank fallback would be shown. */
        boolean hasIcon = true;
        /** Ambient glow colour; 0 until sampled from the banner, or the card's accent from the start. */
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(APP, label, component, banner, icon, null, 0);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.name = caption == null ? label : label + " \u00B7 " + caption;
            this.ambient = accent;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(45 * density);
    }

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock). */
    private final boolean fillDock;
    /** Dock apps found at the last reload, and the empty slots after them. */
    private int docked, emptySlots;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final boolean animations;
    private final boolean advancedEffects;
    private final boolean showHomeSettings;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): no overshoot, shorter fades. */
    private final boolean reducedMotion;
    private final boolean cleanframeDock;
    private final boolean isListLayout;
    private final List<Entry> items = new ArrayList<>();
    /** Clean icons and labels are the same for every bind; build each one once per package. */
    private final Map<String, Drawable> cleanIcons = new HashMap<>();
    private final Map<String, String> cleanLabels = new HashMap<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = cfg.dockApps;
        this.fillDock = cfg.fillDock;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.animations = cfg.animations;
        this.advancedEffects = cfg.animations && cfg.advancedEffects;
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        this.reducedMotion = cfg.reducedMotion;
        this.cleanframeDock = cfg.cleanframeDock;
        this.isListLayout = cfg.isListLayout;
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    /**
     * Rebuilds the tiles. A shown module card stands in for every tile of its package (same component,
     * so focus follows it); hidden_apps wins over a card, and a card's package in dock_apps takes that slot.
     */
    void reload(List<Cards.Card> cards) {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);
        List<Entry> fresh = new ArrayList<>();
        // An app update brings a new icon and label; build them again on the next bind.
        cleanIcons.clear();
        cleanLabels.clear();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;   // the card stands in for this tile
            // The list layout never shows a banner, and its glow is a colour no one draws; loading the
            // artwork there costs megabytes of native heap and decode time on every reload for nothing.
            Drawable banner = isListLayout ? null : loadBanner(pm, ri);
            Drawable icon = ri.loadIcon(pm);
            Entry entry;
            if (card != null) {
                entry = new Entry(Entry.CARD, card.label, component, banner, icon, card.caption, card.accent);
            } else {
                // Same cleaner as the card prompt: an app label with a bidi override or line separator must not
                // redraw the tile name as something else (#117).
                entry = new Entry(Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg), component, banner, icon);
            }
            // loadIcon() falls back to the system's grey placeholder; remember when that is what happened.
            entry.hasIcon = ri.activityInfo.icon != 0 || ri.activityInfo.applicationInfo.icon != 0;
            fresh.add(entry);
        }
        Collections.sort(fresh, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            // Cards come right after the configured dock apps, before the alphabetical grid.
            boolean ca = a.kind == Entry.CARD, cb = b.kind == Entry.CARD;
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        // Retain the optional Default-home tile until dependent tile-ordering work is merged.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }
        docked = 0;
        while (docked < fresh.size() && favourites.contains(fresh.get(docked).pkg)) docked++;
        emptySlots = isListLayout ? 0 : DockSlots.empty(docked, dockSize, fresh.size(), fillDock);
        for (int i = 0; i < emptySlots; i++) {
            fresh.add(docked + i, new Entry(Entry.EMPTY, "", new ComponentName(ctx, "empty-dock-slot-" + i),
                    null, null, null, 0));
        }
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        if (id != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                // Keep at least xhdpi detail rather than downsampling artwork before the focus zoom.
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                return resources.getDrawableForDensity(id, density, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        return ri.activityInfo.loadBanner(pm);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (isListLayout) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_list, parent, false);
            return new Holder(v, listener, animations, advancedEffects, reducedMotion, cleanframeDock, true, dockSize);
        }
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, dockSize, density), h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener, animations, advancedEffects, reducedMotion, cleanframeDock, false, dockSize);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        if (e.kind == Entry.EMPTY) {
            // A free dock slot keeps its place in the row and the band, but shows nothing and takes no focus.
            h.card.setVisibility(View.INVISIBLE);
            h.card.setFocusable(false);
            h.name.setVisibility(View.INVISIBLE);
            h.banner.setImageDrawable(null);
            h.icon.setImageDrawable(null);
            h.card.setOnClickListener(null);
            h.card.setOnLongClickListener(null);
            return;
        }
        h.card.setVisibility(View.VISIBLE);
        h.card.setFocusable(true);
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        if (isListLayout) {
            // The row background and the hidden banner/name views come from item_app_list.xml, so a
            // bind only has to change what varies: icon, label and the focus alpha.
            h.card.setAlpha(focused ? 1f : 0.72f);
            h.card.setScaleX(focused && animations ? 1.04f : 1f);
            h.card.setScaleY(focused && animations ? 1.04f : 1f);
            h.icon.setImageDrawable(getCleanIcon(e));
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(cleanLabel(e));
            h.label.setVisibility(View.VISIBLE);
            h.card.setOnClickListener(v -> launch(e));
            h.card.setOnLongClickListener(v -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    ctx.startActivity(i);
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            });
            return;
        }
        boolean isDockClean = cleanframeDock && position < dockSize;
        if (isDockClean) {
            h.card.setBackground(null);
            h.card.setAlpha(focused ? 1f : 0.82f);
            h.name.setVisibility(View.GONE);
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(getCleanIcon(e));
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        } else {
            h.card.setBackgroundResource(R.drawable.tile_bg);
            h.card.setAlpha(1f);
            h.name.setText(e.name);
            h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);   // the dock has no labels
            h.name.setAlpha(focused ? 1f : 0f);
            if (e.banner != null) {
                h.banner.setImageDrawable(e.banner);
                h.banner.setVisibility(View.VISIBLE);
                h.icon.setVisibility(View.GONE);
                h.label.setVisibility(View.GONE);
            } else {
                h.banner.setImageDrawable(null);
                h.banner.setVisibility(View.GONE);
                h.icon.setImageDrawable(e.icon);
                h.icon.setVisibility(View.VISIBLE);
                h.label.setText(e.label);
                h.label.setVisibility(View.VISIBLE);
            }
        }
        h.card.setScaleX(focused && animations ? focusScale() : 1f);
        h.card.setScaleY(focused && animations ? focusScale() : 1f);
        h.card.setTranslationY(focused && advancedEffects ? -4f * ctx.getResources().getDisplayMetrics().density : 0f);
        h.card.setTranslationZ(focused && advancedEffects ? 8f * ctx.getResources().getDisplayMetrics().density : 0f);
        h.banner.setScaleX(focused && advancedEffects ? 1.035f : 1f);
        h.banner.setScaleY(focused && advancedEffects ? 1.035f : 1f);
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        });
    }

    /** Opens the tile's activity by explicit component, with no extras: a card can name nothing else. */
    private void launch(Entry e) {
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
            if (advancedEffects && ctx instanceof Activity) {
                ((Activity) ctx).overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
            }
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    /** Dock slots that hold an app: the whole dock unless some slots are empty. */
    int dockFilled() {
        return emptySlots > 0 ? docked : dockSize;
    }

    /** A position focus can land on: an empty dock slot gives the last dock app. */
    int focusablePosition(int position) {
        return DockSlots.focusable(position, docked, emptySlots);
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    float focusScale() {
        return !animations ? 1f : advancedEffects ? 1.12f : 1.10f;
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry; a card's accent when it set one. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    private Drawable getCleanIcon(Entry e) {
        if (e == null || e.pkg == null) return null;
        Drawable cached = cleanIcons.get(e.pkg);
        if (cached != null) return cached;
        String pkg = e.pkg.toLowerCase();
        int res = 0;
        if (pkg.contains("netflix")) {
            res = R.drawable.ic_clean_netflix;
        } else if (pkg.contains("amazonvideo") || pkg.contains("primevideo")) {
            res = R.drawable.ic_clean_primevideo;
        } else if (pkg.contains("youtube")) {
            res = R.drawable.ic_clean_youtube;
        } else if (pkg.contains("hotstar")) {
            res = R.drawable.ic_clean_hotstar;
        } else if (pkg.contains("disney")) {
            res = R.drawable.ic_clean_disneyplus;
        } else if (pkg.contains("apple") || pkg.contains("appletv")) {
            res = R.drawable.ic_clean_appletv;
        } else if (pkg.contains("spotify")) {
            res = R.drawable.ic_clean_spotify;
        } else if (pkg.contains("sonyliv")) {
            res = R.drawable.ic_clean_sonyliv;
        } else if (pkg.contains("stremio")) {
            res = R.drawable.ic_clean_stremio;
        } else if (pkg.contains("mediacenter") || pkg.contains("usbmedia")) {
            res = R.drawable.ic_clean_mediacenter;
        } else if (pkg.contains("vending") || pkg.contains("playstore")) {
            res = R.drawable.ic_clean_playstore;
        } else if (pkg.contains("settings")) {
            res = R.drawable.ic_clean_settings;
        }
        if (res != 0) {
            Drawable d = ctx.getDrawable(res);
            if (d != null) {
                cleanIcons.put(e.pkg, d);
                return d;
            }
        }
        if (!e.hasIcon) {
            Drawable monogram = new MonogramDrawable(cleanLabel(e),
                    ctx.getResources().getFont(R.font.outfit_medium));
            cleanIcons.put(e.pkg, monogram);
            return monogram;
        }
        Drawable fallback = e.icon != null ? whiteSilhouette(e.icon) : null;
        if (fallback != null) cleanIcons.put(e.pkg, fallback);
        return fallback;
    }

    /**
     * The white logo shown for an app with no hand-drawn vector. An adaptive icon is tinted from its
     * foreground layer, and any other icon is reduced to a white silhouette by brightness. Tinting the
     * whole icon flat white (the old behaviour) painted its opaque background plate too, which is what
     * turned Zee 5 into a blank square: its real icon is a dark plate with a white "Z5" on it.
     */
    private Drawable whiteSilhouette(Drawable icon) {
        Drawable source = icon;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon instanceof AdaptiveIconDrawable) {
            Drawable foreground = ((AdaptiveIconDrawable) icon).getForeground();
            // Some launchers ship a foreground with no size of its own; drawing that gives nothing,
            // so those keep the plain icon rather than a white block.
            if (foreground != null && foreground.getIntrinsicWidth() > 0) source = foreground;
        }
        int size = Math.max(96, Math.round(48 * ctx.getResources().getDisplayMetrics().density));
        Bitmap src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Drawable copy = source.getConstantState() != null
                ? source.getConstantState().newDrawable().mutate() : source.mutate();
        copy.setBounds(0, 0, size, size);
        copy.draw(new Canvas(src));

        int[] px = new int[size * size];
        src.getPixels(px, 0, size, 0, 0, size, size);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int lum = (Color.red(p) * 77 + Color.green(p) * 151 + Color.blue(p) * 28) >> 8;
            // Drop the dark plate instead of washing it in at 10% white, which would still read as a
            // square; everything brighter than the plate keeps its brightness as opacity.
            int t = Math.max(0, Math.min(255, (lum - 48) * 255 / 207));
            px[i] = ((Color.alpha(p) * t / 255) << 24) | 0x00FFFFFF;
        }
        src.setPixels(px, 0, size, 0, 0, size, size);
        return new android.graphics.drawable.BitmapDrawable(ctx.getResources(), src);
    }

    /** The label shown for a tile, cleaned once per package instead of on every bind. */
    private String cleanLabel(Entry e) {
        if (e == null || e.pkg == null) return getCleanLabel(e);
        String cached = cleanLabels.get(e.pkg);
        if (cached != null) return cached;
        String label = getCleanLabel(e);
        cleanLabels.put(e.pkg, label);
        return label;
    }

    static String getCleanLabel(Entry e) {
        if (e == null) return "";
        if (e.pkg != null) {
            String pkg = e.pkg.toLowerCase();
            if (pkg.contains("netflix")) return "Netflix";
            if (pkg.contains("amazonvideo") || pkg.contains("primevideo")) return "Prime Video";
            if (pkg.contains("youtube.tvmusic")) return "YouTube Music";
            if (pkg.contains("youtube")) return "YouTube";
            if (pkg.contains("hotstar")) return "JioHotstar";
            if (pkg.contains("disney")) return "Disney+";
            if (pkg.contains("apple") || pkg.contains("appletv")) return "Apple TV";
            if (pkg.contains("spotify")) return "Spotify";
            if (pkg.contains("sonyliv")) return "Sony LIV";
            if (pkg.contains("stremio")) return "Stremio";
            if (pkg.contains("mediacenter") || pkg.contains("usbmedia")) return "Media Center";
            if (pkg.contains("vending") || pkg.contains("playstore")) return "Play Store";
            if (pkg.contains("settings")) return "Settings";
        }
        return e.label != null ? e.label : "";
    }

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        final boolean animations;
        final boolean advancedEffects;
        final boolean reducedMotion;
        final boolean cleanframeDock;
        final boolean isListLayout;
        final int dockSize;

        Holder(View v, TileListener listener, boolean animations, boolean advancedEffects,
               boolean reducedMotion, boolean cleanframeDock, boolean isListLayout, int dockSize) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);
            this.animations = animations;
            this.advancedEffects = advancedEffects;
            this.reducedMotion = reducedMotion;
            this.cleanframeDock = cleanframeDock;
            this.isListLayout = isListLayout;
            this.dockSize = dockSize;

            final float density = v.getResources().getDisplayMetrics().density;
            if (!isListLayout) {
                card.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                    }
                });
                card.setClipToOutline(true);
            }

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                view.animate().cancel();
                if (banner != null) banner.animate().cancel();
                if (name != null) name.animate().cancel();
                int pos = getBindingAdapterPosition();
                if (isListLayout) {
                    float s = focused && animations ? 1.04f : 1.0f;
                    float a = focused ? 1.0f : 0.72f;
                    if (!animations) {
                        view.setScaleX(s);
                        view.setScaleY(s);
                        view.setAlpha(a);
                    } else {
                        view.animate().scaleX(s).scaleY(s).alpha(a)
                                .setDuration(160)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    }
                    if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                        listener.onTileFocused(pos, view);
                    }
                    return;
                }
                boolean isClean = cleanframeDock && pos != RecyclerView.NO_POSITION && pos < dockSize;
                if (isClean) {
                    card.setBackground(null);
                    view.animate().alpha(focused ? 1f : 0.82f).setDuration(150).start();
                } else {
                    card.setBackgroundResource(R.drawable.tile_bg);
                    view.setAlpha(1f);
                }
                float scale = !animations ? 1f : advancedEffects ? 1.12f : 1.10f;
                float s = focused ? scale : 1f;
                if (!animations) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setTranslationY(0f);
                    view.setTranslationZ(0f);
                    banner.setScaleX(1f);
                    banner.setScaleY(1f);
                    if (!isClean) {
                        name.setAlpha(focused ? 1f : 0f);
                        name.setTranslationY(0f);
                    }
                } else if (reducedMotion) {
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(focused && advancedEffects ? -4f * density : 0f)
                            .translationZ(focused && advancedEffects ? 8f * density : 0f)
                            .setDuration(focused ? 120 : 80)
                            .setInterpolator(new DecelerateInterpolator())
                            .withLayer().start();
                    if (!isClean) {
                        name.animate().alpha(focused ? 1f : 0f).setDuration(100).start();
                    }
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(focused && advancedEffects ? -4f * density : 0f)
                            .translationZ(focused && advancedEffects ? 8f * density : 0f)
                            .setDuration(advancedEffects ? (focused ? 220 : 170) : (focused ? 260 : 180))
                            .setInterpolator(new OvershootInterpolator(focused ? (advancedEffects ? 0.7f : 1.6f) : 0f))
                            .withLayer().start();
                    if (advancedEffects && !isClean) {
                        banner.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)
                                .setDuration(280).start();
                    }
                    if (!isClean) {
                        name.animate().alpha(focused ? 1f : 0f)
                                .translationY(focused ? density * 4f : -density * 6f)
                                .setDuration(220).start();
                    }
                }
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
