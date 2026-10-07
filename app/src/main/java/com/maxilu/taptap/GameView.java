package com.maxilu.taptap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class GameView extends View {
    private static final String PREFS = "tap_tap_save";
    private static final long OFFLINE_CAP_MS = 6L * 60L * 60L * 1000L;
    private static final int GEM_BUY_COST = 1000;
    private static final int GEM_SELL_VALUE = 850;

    private static final int LIGHT = 0;
    private static final int DARK = 1;
    private static final int EGG = 0;
    private static final int BABY = 1;
    private static final int MID = 2;
    private static final int ADULT = 3;

    private final SharedPreferences sp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ArrayList<Creature> creatures = new ArrayList<>();
    private final Random random = new Random();
    private final Drawable[][] fallbackArt;
    private final Bitmap[] evolutionSheets = new Bitmap[2];
    private final Bitmap logo;
    private final float density;

    private final RectF starterLight = new RectF();
    private final RectF starterDark = new RectF();
    private final RectF nextButton = new RectF();
    private final RectF eggButton = new RectF();
    private final RectF tapButton = new RectF();
    private final RectF afkButton = new RectF();
    private final RectF buyGemButton = new RectF();
    private final RectF sellGemButton = new RectF();

    private double coins;
    private int gems;
    private double tapPower = 1.0;
    private double passivePower = 1.0;
    private int activeId = -1;
    private int nextId = 1;
    private long lastSeen;
    private boolean firstHatchUnlockedOpposite;
    private boolean ticking;

    private final Runnable passiveTick = new Runnable() {
        @Override public void run() {
            if (!ticking) return;
            addPassiveIncome(1.0);
            invalidate();
            postDelayed(this, 1000L);
        }
    };

    static final class Creature {
        int id;
        int type;
        int stage;
        double progress;

        Creature(int id, int type, int stage, double progress) {
            this.id = id;
            this.type = type;
            this.stage = stage;
            this.progress = progress;
        }
    }

    public GameView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        fallbackArt = new Drawable[][]{
                {
                        context.getDrawable(R.drawable.light_egg),
                        context.getDrawable(R.drawable.light_baby),
                        context.getDrawable(R.drawable.light_mid),
                        context.getDrawable(R.drawable.light_adult)
                },
                {
                        context.getDrawable(R.drawable.dark_egg),
                        context.getDrawable(R.drawable.dark_baby),
                        context.getDrawable(R.drawable.dark_mid),
                        context.getDrawable(R.drawable.dark_adult)
                }
        };

        evolutionSheets[LIGHT] =
                BitmapFactory.decodeResource(getResources(), R.drawable.light_evolution_real);
        evolutionSheets[DARK] =
                ArtLoader.loadBase64Chunks(context,
                        "v2/dark_0.b64",
                        "v2/dark_1a.b64",
                        "v2/dark_1b.b64",
                        "v2/dark_2.b64",
                        "v2/dark_3.b64");
        logo = BitmapFactory.decodeResource(getResources(), R.drawable.tap_tap_icon);

        paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        setBackgroundColor(0xFF0C0A16);
        setFocusable(true);
        load();
    }

    private float dp(float value) {
        return value * density;
    }

    private void load() {
        coins = Double.longBitsToDouble(sp.getLong("coins_bits", Double.doubleToRawLongBits(0.0)));
        gems = Math.max(0, sp.getInt("gems", 0));
        tapPower = Double.longBitsToDouble(sp.getLong("tap_power_bits", Double.doubleToRawLongBits(1.0)));
        passivePower = Double.longBitsToDouble(sp.getLong("passive_power_bits", Double.doubleToRawLongBits(1.0)));
        activeId = sp.getInt("active_id", -1);
        nextId = sp.getInt("next_id", 1);
        firstHatchUnlockedOpposite = sp.getBoolean("opposite_unlocked", false);
        lastSeen = sp.getLong("last_seen", System.currentTimeMillis());

        int count = sp.getInt("creature_count", 0);
        for (int i = 0; i < count; i++) {
            int id = sp.getInt("c_" + i + "_id", i + 1);
            int type = sp.getInt("c_" + i + "_type", LIGHT);
            int stage = sp.getInt("c_" + i + "_stage", EGG);
            double progress = Double.longBitsToDouble(
                    sp.getLong("c_" + i + "_progress_bits", Double.doubleToRawLongBits(0.0)));
            creatures.add(new Creature(id, type, stage, progress));
        }
    }

    private void save() {
        SharedPreferences.Editor e = sp.edit();
        e.putLong("coins_bits", Double.doubleToRawLongBits(coins));
        e.putInt("gems", Math.max(0, gems));
        e.putLong("tap_power_bits", Double.doubleToRawLongBits(tapPower));
        e.putLong("passive_power_bits", Double.doubleToRawLongBits(passivePower));
        e.putInt("active_id", activeId);
        e.putInt("next_id", nextId);
        e.putBoolean("opposite_unlocked", firstHatchUnlockedOpposite);
        e.putLong("last_seen", lastSeen);
        e.putInt("creature_count", creatures.size());

        for (int i = 0; i < creatures.size(); i++) {
            Creature c = creatures.get(i);
            e.putInt("c_" + i + "_id", c.id);
            e.putInt("c_" + i + "_type", c.type);
            e.putInt("c_" + i + "_stage", c.stage);
            e.putLong("c_" + i + "_progress_bits", Double.doubleToRawLongBits(c.progress));
        }
        e.apply();
    }

    public void pauseGame() {
        ticking = false;
        removeCallbacks(passiveTick);
        lastSeen = System.currentTimeMillis();
        save();
    }

    public void resumeGame() {
        applyOffline();
        ticking = true;
        removeCallbacks(passiveTick);
        postDelayed(passiveTick, 1000L);
    }

    public void saveAndLeave() {
        pauseGame();
    }

    public void applyOffline() {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, Math.min(OFFLINE_CAP_MS, now - lastSeen));
        if (elapsed >= 3000L && !creatures.isEmpty()) {
            double before = coins;
            addPassiveIncome(elapsed / 1000.0);
            long earned = Math.max(0L, Math.round(coins - before));
            if (earned > 0) NotificationHelper.showOfflineReward(getContext(), earned);
        }
        lastSeen = now;
        save();
        invalidate();
    }

    private Creature activeCreature() {
        for (Creature c : creatures) if (c.id == activeId) return c;
        return creatures.isEmpty() ? null : creatures.get(0);
    }

    private void chooseStarter(int type) {
        if (!creatures.isEmpty()) return;
        Creature c = new Creature(nextId++, type, EGG, 0.0);
        creatures.add(c);
        activeId = c.id;
        haptic();
        save();
        invalidate();
    }

    private double stageThreshold(int stage) {
        if (stage == EGG) return 30.0;
        if (stage == BABY) return 100.0;
        if (stage == MID) return 250.0;
        return Double.POSITIVE_INFINITY;
    }

    private String stageName(int stage) {
        if (stage == EGG) return "EGG";
        if (stage == BABY) return "BABY";
        if (stage == MID) return "MID";
        return "ADULT";
    }

    private String typeName(int type) {
        return type == LIGHT ? "LIGHT" : "DARK";
    }

    private double baseIncome(Creature c) {
        if (c.stage == BABY) return 1.0;
        if (c.stage == MID) return 2.5;
        if (c.stage == ADULT) return 5.0;
        return 0.0;
    }

    private void addPassiveIncome(double seconds) {
        double perSecond = 0.0;
        for (Creature c : creatures) {
            double modifier = c.id == activeId ? 1.0 : 0.75;
            perSecond += baseIncome(c) * modifier * passivePower;
        }
        coins += perSecond * seconds;
    }

    private void tapActive() {
        Creature c = activeCreature();
        if (c == null) return;
        haptic();
        coins += tapPower;

        if (c.stage < ADULT) {
            c.progress += tapPower;
            double needed = stageThreshold(c.stage);
            if (c.progress >= needed) {
                c.progress -= needed;
                c.stage++;

                if (c.stage == BABY) {
                    NotificationHelper.showEggReady(getContext(), typeName(c.type) + " Baby");
                    if (!firstHatchUnlockedOpposite) {
                        firstHatchUnlockedOpposite = true;
                        int opposite = c.type == LIGHT ? DARK : LIGHT;
                        creatures.add(new Creature(nextId++, opposite, EGG, 0.0));
                    }
                }
            }
        }

        save();
        invalidate();
    }

    private void cycleActive() {
        if (creatures.size() < 2) return;
        int index = 0;
        for (int i = 0; i < creatures.size(); i++) {
            if (creatures.get(i).id == activeId) {
                index = i;
                break;
            }
        }
        activeId = creatures.get((index + 1) % creatures.size()).id;
        haptic();
        save();
        invalidate();
    }

    private int tapUpgradeCost() {
        int level = (int) Math.round((tapPower - 1.0) / 0.5);
        return 100 + level * 125;
    }

    private int passiveUpgradeCost() {
        int level = (int) Math.round((passivePower - 1.0) / 0.5);
        return 150 + level * 150;
    }

    private void buyTapUpgrade() {
        int cost = tapUpgradeCost();
        if (coins < cost) return;
        coins -= cost;
        tapPower += 0.5;
        haptic();
        save();
        invalidate();
    }

    private void buyPassiveUpgrade() {
        int cost = passiveUpgradeCost();
        if (coins < cost) return;
        coins -= cost;
        passivePower += 0.5;
        haptic();
        save();
        invalidate();
    }

    private void buyRandomEgg() {
        if (coins < 1000.0) return;
        coins -= 1000.0;
        int type = random.nextBoolean() ? LIGHT : DARK;
        Creature egg = new Creature(nextId++, type, EGG, 0.0);
        creatures.add(egg);
        if (activeId < 0) activeId = egg.id;
        haptic();
        save();
        invalidate();
    }

    private void buyGem() {
        if (coins < GEM_BUY_COST) return;
        coins -= GEM_BUY_COST;
        gems += 1;
        haptic();
        save();
        invalidate();
    }

    private void sellGem() {
        if (gems <= 0) return;
        gems -= 1;
        coins += GEM_SELL_VALUE;
        haptic();
        save();
        invalidate();
    }

    // Future-ready hooks for gem-only systems such as rerolls, cosmetics,
    // special eggs, prestige utilities and event crafting.
    private boolean spendGems(int amount) {
        if (amount <= 0 || gems < amount) return false;
        gems -= amount;
        save();
        return true;
    }

    private void addGems(int amount) {
        if (amount <= 0) return;
        gems += amount;
        save();
    }

    private void haptic() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void drawBackground(Canvas canvas, int type) {
        int top = type == LIGHT ? 0xFF17131E : 0xFF100B20;
        int bottom = 0xFF080711;
        paint.setShader(new LinearGradient(0, 0, 0, getHeight(), top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);

        float glowX = type == LIGHT ? getWidth() * 0.28f : getWidth() * 0.72f;
        int glow = type == LIGHT ? 0x55F5C84C : 0x556A39FF;
        paint.setShader(new RadialGradient(glowX, getHeight() * 0.28f,
                getWidth() * 0.72f, glow, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(glowX, getHeight() * 0.28f, getWidth() * 0.72f, paint);
        paint.setShader(null);
    }

    private void text(Canvas c, String value, float x, float y, float sizeSp,
                      int color, Paint.Align align, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(dp(sizeSp));
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(value, x, y, paint);
    }

    private void pill(Canvas c, RectF r, int fill, int stroke) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        c.drawRoundRect(r, dp(18), dp(18), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(stroke);
        c.drawRoundRect(r, dp(18), dp(18), paint);
    }

    private void drawLogo(Canvas canvas, float cx, float cy, float size) {
        if (logo == null) return;
        Rect src = new Rect(0, 0, logo.getWidth(), logo.getHeight());
        RectF dst = new RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        canvas.drawBitmap(logo, src, dst, paint);
    }

    private Rect spriteRect(Bitmap sheet, int stage) {
        int cw = sheet.getWidth() / 2;
        int ch = sheet.getHeight() / 2;
        int col = stage % 2;
        int row = stage / 2;
        int x = col * cw;
        int y = row * ch;

        int left = x + Math.round(cw * 0.07f);
        int top = y + Math.round(ch * 0.05f);
        int right = x + Math.round(cw * 0.93f);
        int bottom = y + Math.round(ch * 0.84f);
        return new Rect(left, top, right, bottom);
    }

    private void drawCreatureArt(Canvas canvas, int type, int stage, RectF dst) {
        int safeType = type == DARK ? DARK : LIGHT;
        int safeStage = Math.max(EGG, Math.min(ADULT, stage));
        Bitmap sheet = evolutionSheets[safeType];

        if (sheet != null && !sheet.isRecycled()) {
            canvas.drawBitmap(sheet, spriteRect(sheet, safeStage), dst, paint);
            return;
        }

        Drawable art = fallbackArt[safeType][safeStage];
        if (art == null) return;
        art.setBounds(Math.round(dst.left), Math.round(dst.top),
                Math.round(dst.right), Math.round(dst.bottom));
        art.draw(canvas);
    }

    private void drawStarterCard(Canvas canvas, RectF r, int type) {
        boolean light = type == LIGHT;
        int accent = light ? 0xFFFFD45C : 0xFF8156FF;
        int accentSoft = light ? 0x33FFD45C : 0x338156FF;

        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                light ? 0xEE302B34 : 0xEE211832,
                light ? 0xEE181520 : 0xEE100D1C,
                Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(r, dp(26), dp(26), paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(accent);
        canvas.drawRoundRect(r, dp(26), dp(26), paint);

        RectF glow = new RectF(r.left + dp(12), r.top + dp(12),
                r.left + r.height() - dp(12), r.bottom - dp(12));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accentSoft);
        canvas.drawRoundRect(glow, dp(22), dp(22), paint);

        float artSize = r.height() * 0.76f;
        RectF art = new RectF(r.left + dp(9), r.centerY() - artSize / 2,
                r.left + dp(9) + artSize, r.centerY() + artSize / 2);
        drawCreatureArt(canvas, type, EGG, art);

        float tx = r.left + r.height() + dp(12);
        text(canvas, light ? "LIGHT EGG" : "DARK EGG",
                tx, r.centerY() - dp(12), 20, 0xFFFFFFFF, Paint.Align.LEFT, true);
        text(canvas, light ? "RADIANT ORIGIN" : "VOID ORIGIN",
                tx, r.centerY() + dp(13), 10, accent, Paint.Align.LEFT, true);
        text(canvas, light ? "Warm · celestial · balanced" : "Mystic · void · intense",
                tx, r.centerY() + dp(34), 10, 0xFFB9B4C8, Paint.Align.LEFT, false);
    }

    private void drawStart(Canvas canvas) {
        drawBackground(canvas, DARK);
        float w = getWidth();
        float h = getHeight();

        drawLogo(canvas, w / 2f, dp(82), dp(82));
        text(canvas, "TAP TAP", w / 2f, dp(143), 30,
                0xFFFFFFFF, Paint.Align.CENTER, true);
        text(canvas, "V2 BETA  ·  CHOOSE YOUR ORIGIN", w / 2f, dp(166), 10,
                0xFFAAA4BC, Paint.Align.CENTER, true);

        float cardH = Math.min(dp(184), h * 0.205f);
        float left = dp(18);
        float right = w - dp(18);
        float firstTop = Math.max(dp(200), h * 0.245f);
        float gap = dp(18);

        starterLight.set(left, firstTop, right, firstTop + cardH);
        starterDark.set(left, starterLight.bottom + gap, right,
                starterLight.bottom + gap + cardH);

        drawStarterCard(canvas, starterLight, LIGHT);
        drawStarterCard(canvas, starterDark, DARK);

        text(canvas, "Tap one egg to begin your collection",
                w / 2f, Math.min(h - dp(34), starterDark.bottom + dp(42)),
                11, 0xFFD2CCDF, Paint.Align.CENTER, false);
    }

    private void drawTopBar(Canvas canvas) {
        float w = getWidth();
        text(canvas, "TAP TAP", dp(18), dp(42), 20,
                0xFFFFFFFF, Paint.Align.LEFT, true);
        text(canvas, "MONSTER LAB", dp(18), dp(59), 8,
                0xFF948EA8, Paint.Align.LEFT, true);

        RectF wallet = new RectF(w - dp(166), dp(20), w - dp(14), dp(68));
        pill(canvas, wallet, 0xCC1A1725, 0x55FFFFFF);

        float mid = wallet.centerX();
        text(canvas, "COINS", wallet.left + dp(12), wallet.centerY() - dp(5),
                7, 0xFFA9A3B9, Paint.Align.LEFT, true);
        text(canvas, String.valueOf(Math.max(0L, Math.round(coins))),
                wallet.left + dp(12), wallet.centerY() + dp(13),
                13, 0xFFFFD45C, Paint.Align.LEFT, true);

        text(canvas, "GEMS", mid + dp(8), wallet.centerY() - dp(5),
                7, 0xFFA9A3B9, Paint.Align.LEFT, true);
        text(canvas, String.valueOf(Math.max(0, gems)),
                mid + dp(8), wallet.centerY() + dp(13),
                13, 0xFF6FE8FF, Paint.Align.LEFT, true);
    }

    private void drawProgress(Canvas canvas, Creature active, RectF panel, int accent) {
        float left = panel.left + dp(20);
        float right = panel.right - dp(20);
        float y = panel.bottom - dp(46);

        if (active.stage >= ADULT) {
            text(canvas, "ADULT MAX STAGE · KEEP TAPPING FOR COINS",
                    panel.centerX(), y + dp(6), 10, 0xFFCBC5D8,
                    Paint.Align.CENTER, true);
            return;
        }

        double needed = stageThreshold(active.stage);
        float p = (float) Math.max(0, Math.min(1, active.progress / needed));

        RectF track = new RectF(left, y, right, y + dp(8));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x552F2B3C);
        canvas.drawRoundRect(track, dp(6), dp(6), paint);

        RectF fill = new RectF(track.left, track.top,
                track.left + track.width() * p, track.bottom);
        paint.setColor(accent);
        canvas.drawRoundRect(fill, dp(6), dp(6), paint);

        text(canvas, "TAP TO EVOLVE  ·  " + Math.round(p * 100f) + "%",
                panel.centerX(), y - dp(10), 10, 0xFFE1DCE9,
                Paint.Align.CENTER, true);
    }

    private void drawControl(Canvas canvas, RectF r, String title, String detail,
                             boolean enabled, int accent) {
        int fill = enabled ? 0xD9201C2C : 0xB5161420;
        int stroke = enabled ? (accent & 0x00FFFFFF) | 0xAA000000 : 0x554F4A5F;
        pill(canvas, r, fill, stroke);
        text(canvas, title, r.left + dp(14), r.centerY() - dp(3), 11,
                enabled ? 0xFFFFFFFF : 0xFF777183, Paint.Align.LEFT, true);
        text(canvas, detail, r.left + dp(14), r.centerY() + dp(16), 9,
                enabled ? accent : 0xFF696474, Paint.Align.LEFT, true);
    }

    private void drawGame(Canvas canvas, Creature active) {
        int accent = active.type == LIGHT ? 0xFFFFD45C : 0xFF8156FF;
        drawBackground(canvas, active.type);
        drawTopBar(canvas);

        float w = getWidth();
        float h = getHeight();
        float margin = dp(16);

        RectF hero = new RectF(margin, dp(86), w - margin, h * 0.61f);
        paint.setShader(new LinearGradient(hero.left, hero.top, hero.right, hero.bottom,
                active.type == LIGHT ? 0xDD302B32 : 0xDD21172F,
                0xDD11101A, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(hero, dp(28), dp(28), paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.4f));
        paint.setColor(accent);
        canvas.drawRoundRect(hero, dp(28), dp(28), paint);

        text(canvas, typeName(active.type) + " · " + stageName(active.stage),
                hero.left + dp(20), hero.top + dp(32), 14,
                0xFFFFFFFF, Paint.Align.LEFT, true);
        text(canvas, "ACTIVE CREATURE",
                hero.left + dp(20), hero.top + dp(50), 8,
                0xFFA9A3B9, Paint.Align.LEFT, true);

        float artTop = hero.top + dp(54);
        float artBottom = hero.bottom - dp(66);
        float artSize = Math.min(hero.width() - dp(32), artBottom - artTop);
        RectF art = new RectF(hero.centerX() - artSize / 2f, artTop,
                hero.centerX() + artSize / 2f, artTop + artSize);
        drawCreatureArt(canvas, active.type, active.stage, art);
        drawProgress(canvas, active, hero, accent);

        float controlsTop = hero.bottom + dp(12);
        float gap = dp(8);
        float buttonW = (w - margin * 2f - gap) / 2f;
        float available = h - controlsTop - dp(20) - gap * 2f;
        float buttonH = Math.min(dp(62), Math.max(dp(48), available / 3f));

        nextButton.set(margin, controlsTop, margin + buttonW, controlsTop + buttonH);
        eggButton.set(margin + buttonW + gap, controlsTop, w - margin, controlsTop + buttonH);

        buyGemButton.set(margin, controlsTop + buttonH + gap,
                margin + buttonW, controlsTop + buttonH * 2f + gap);
        sellGemButton.set(margin + buttonW + gap, controlsTop + buttonH + gap,
                w - margin, controlsTop + buttonH * 2f + gap);

        tapButton.set(margin, controlsTop + buttonH * 2f + gap * 2f,
                margin + buttonW, controlsTop + buttonH * 3f + gap * 2f);
        afkButton.set(margin + buttonW + gap, controlsTop + buttonH * 2f + gap * 2f,
                w - margin, controlsTop + buttonH * 3f + gap * 2f);

        drawControl(canvas, nextButton, "COLLECTION", creatures.size() + " CREATURES",
                creatures.size() > 1, accent);
        drawControl(canvas, eggButton, "MYSTERY EGG", "1,000 COINS",
                coins >= 1000, accent);

        drawControl(canvas, buyGemButton, "BUY GEM", GEM_BUY_COST + " COINS",
                coins >= GEM_BUY_COST, 0xFF6FE8FF);
        drawControl(canvas, sellGemButton, "SELL GEM", GEM_SELL_VALUE + " COINS",
                gems > 0, 0xFF6FE8FF);

        drawControl(canvas, tapButton,
                String.format(Locale.US, "TAP  ×%.1f", tapPower),
                tapUpgradeCost() + " COINS", coins >= tapUpgradeCost(), accent);
        drawControl(canvas, afkButton,
                String.format(Locale.US, "AFK  ×%.1f", passivePower),
                passiveUpgradeCost() + " COINS", coins >= passiveUpgradeCost(), accent);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (creatures.isEmpty()) {
            drawStart(canvas);
            return;
        }

        Creature active = activeCreature();
        if (active != null) drawGame(canvas, active);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = event.getX();
        float y = event.getY();

        if (creatures.isEmpty()) {
            if (starterLight.contains(x, y)) chooseStarter(LIGHT);
            else if (starterDark.contains(x, y)) chooseStarter(DARK);
            return true;
        }

        Creature active = activeCreature();
        if (active == null) return true;

        float heroBottom = getHeight() * 0.61f;
        if (y >= dp(86) && y <= heroBottom) {
            tapActive();
            return true;
        }

        if (nextButton.contains(x, y)) cycleActive();
        else if (eggButton.contains(x, y)) buyRandomEgg();
        else if (buyGemButton.contains(x, y)) buyGem();
        else if (sellGemButton.contains(x, y)) sellGem();
        else if (tapButton.contains(x, y)) buyTapUpgrade();
        else if (afkButton.contains(x, y)) buyPassiveUpgrade();

        return true;
    }

    @Override protected void onDetachedFromWindow() {
        pauseGame();
        super.onDetachedFromWindow();
    }
}
