package com.maxilu.taptap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class GameView extends View {
    private static final String PREFS = "tap_tap_save";
    private static final long OFFLINE_CAP_MS = 6L * 60L * 60L * 1000L;

    private static final int LIGHT = 0;
    private static final int DARK = 1;
    private static final int EGG = 0;
    private static final int BABY = 1;
    private static final int MID = 2;
    private static final int ADULT = 3;

    private final SharedPreferences sp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Creature> creatures = new ArrayList<>();
    private final Random random = new Random();
    private final Drawable[][] creatureArt;

    private double coins;
    private double tapPower = 1.0;
    private double passivePower = 1.0;
    private int activeId = -1;
    private int nextId = 1;
    private long lastSeen;
    private boolean firstHatchUnlockedOpposite;

    private final Runnable passiveTick = new Runnable() {
        @Override public void run() {
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
        sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        creatureArt = new Drawable[][]{
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
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        setBackgroundColor(0xFF171526);
        load();
        applyOffline();
        removeCallbacks(passiveTick);
        postDelayed(passiveTick, 1000L);
    }

    private void load() {
        coins = Double.longBitsToDouble(sp.getLong("coins_bits", Double.doubleToRawLongBits(0.0)));
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

    public void saveAndLeave() {
        lastSeen = System.currentTimeMillis();
        save();
    }

    public void applyOffline() {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, Math.min(OFFLINE_CAP_MS, now - lastSeen));
        if (elapsed >= 3000L && !creatures.isEmpty()) {
            double before = coins;
            addPassiveIncome(elapsed / 1000.0);
            long earned = Math.max(0L, Math.round(coins - before));
            if (earned > 0) {
                NotificationHelper.showOfflineReward(getContext(), earned);
            }
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
        if (stage == EGG) return "Egg";
        if (stage == BABY) return "Baby";
        if (stage == MID) return "Mid";
        return "Adult";
    }

    private String typeName(int type) {
        return type == LIGHT ? "Light" : "Dark";
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

        coins += tapPower;
        if (c.stage >= ADULT) {
            save();
            invalidate();
            return;
        }

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
        save();
        invalidate();
    }

    private void buyPassiveUpgrade() {
        int cost = passiveUpgradeCost();
        if (coins < cost) return;
        coins -= cost;
        passivePower += 0.5;
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
        save();
        invalidate();
    }

    private void drawText(Canvas c, String text, float x, float y, float size, int color, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        c.drawText(text, x, y, paint);
    }

    private void drawButton(Canvas c, RectF r, String text, boolean enabled) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(enabled ? 0xFF312A50 : 0xFF262238);
        c.drawRoundRect(r, 24f, 24f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(enabled ? 0xFF8B6CFF : 0xFF514B66);
        c.drawRoundRect(r, 24f, 24f, paint);
        drawText(c, text, r.centerX(), r.centerY() + 8f, 28f,
                enabled ? 0xFFFFFFFF : 0xFF8A8597, Paint.Align.CENTER);
    }

    private void drawCreature(Canvas canvas, Creature cr, float cx, float cy, float radius) {
        int safeType = cr.type == DARK ? DARK : LIGHT;
        int safeStage = Math.max(EGG, Math.min(ADULT, cr.stage));
        Drawable art = creatureArt[safeType][safeStage];
        if (art == null) return;

        int size = Math.round(radius * 2.35f);
        int left = Math.round(cx - size / 2f);
        int top = Math.round(cy - size / 2f);
        art.setBounds(left, top, left + size, top + size);
        art.draw(canvas);
    }

    private void drawStarterCard(Canvas canvas, RectF card, int type, String label) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(type == LIGHT ? 0xFF332D48 : 0xFF211B35);
        canvas.drawRoundRect(card, 28f, 28f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(type == LIGHT ? 0xFFFFD34E : 0xFF7B4DFF);
        canvas.drawRoundRect(card, 28f, 28f, paint);

        Drawable egg = creatureArt[type][EGG];
        int artSize = Math.round(Math.min(card.width(), card.height()) * 0.62f);
        int cx = Math.round(card.centerX());
        int cy = Math.round(card.top + card.height() * 0.42f);
        egg.setBounds(cx - artSize / 2, cy - artSize / 2, cx + artSize / 2, cy + artSize / 2);
        egg.draw(canvas);

        drawText(canvas, label, card.centerX(), card.bottom - 34f, 25f,
                0xFFFFFFFF, Paint.Align.CENTER);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        drawText(canvas, "TAP TAP", w / 2f, 72f, 48f, 0xFFFFFFFF, Paint.Align.CENTER);
        drawText(canvas, "Coins " + Math.max(0L, Math.round(coins)), 32f, 122f, 30f,
                0xFFFFD85A, Paint.Align.LEFT);

        if (creatures.isEmpty()) {
            drawText(canvas, "Choose your first egg", w / 2f, h * 0.25f, 34f,
                    0xFFFFFFFF, Paint.Align.CENTER);
            RectF light = new RectF(28f, h * 0.34f, w / 2f - 12f, h * 0.68f);
            RectF dark = new RectF(w / 2f + 12f, h * 0.34f, w - 28f, h * 0.68f);
            drawStarterCard(canvas, light, LIGHT, "LIGHT EGG");
            drawStarterCard(canvas, dark, DARK, "DARK EGG");
            drawText(canvas, "Tap an egg to start", w / 2f, h * 0.74f, 24f,
                    0xFFD7D2E9, Paint.Align.CENTER);
            return;
        }

        Creature active = activeCreature();
        if (active == null) return;

        drawText(canvas, typeName(active.type) + " · " + stageName(active.stage),
                w / 2f, 170f, 34f, 0xFFFFFFFF, Paint.Align.CENTER);

        float creatureRadius = Math.min(w, h) * 0.16f + active.stage * 10f;
        drawCreature(canvas, active, w / 2f, h * 0.37f, creatureRadius);

        if (active.stage < ADULT) {
            double need = stageThreshold(active.stage);
            int pct = (int) Math.min(100, Math.round(active.progress * 100.0 / need));
            drawText(canvas, "Tap to evolve · " + pct + "%", w / 2f, h * 0.58f,
                    28f, 0xFFD7D2E9, Paint.Align.CENTER);
        } else {
            drawText(canvas, "Adult · keep tapping for coins", w / 2f, h * 0.58f,
                    28f, 0xFFD7D2E9, Paint.Align.CENTER);
        }

        float top = h * 0.66f;
        float gap = 14f;
        float bw = (w - 56f - gap) / 2f;
        float bh = 82f;
        RectF next = new RectF(28f, top, 28f + bw, top + bh);
        RectF randomEgg = new RectF(28f + bw + gap, top, w - 28f, top + bh);
        RectF tapUpgrade = new RectF(28f, top + bh + gap, 28f + bw, top + bh * 2 + gap);
        RectF passiveUpgrade = new RectF(28f + bw + gap, top + bh + gap, w - 28f, top + bh * 2 + gap);

        drawButton(canvas, next, "NEXT · " + creatures.size(), creatures.size() > 1);
        drawButton(canvas, randomEgg, "RANDOM EGG · 1000", coins >= 1000);
        drawButton(canvas, tapUpgrade,
                String.format(Locale.US, "TAP %.1fx · %d", tapPower, tapUpgradeCost()),
                coins >= tapUpgradeCost());
        drawButton(canvas, passiveUpgrade,
                String.format(Locale.US, "AFK %.1fx · %d", passivePower, passiveUpgradeCost()),
                coins >= passiveUpgradeCost());
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;

        float x = event.getX();
        float y = event.getY();
        float w = getWidth();
        float h = getHeight();

        if (creatures.isEmpty()) {
            if (y >= h * 0.34f && y <= h * 0.68f) {
                chooseStarter(x < w / 2f ? LIGHT : DARK);
            }
            return true;
        }

        if (y >= h * 0.18f && y <= h * 0.62f) {
            tapActive();
            return true;
        }

        float top = h * 0.66f;
        float gap = 14f;
        float bw = (w - 56f - gap) / 2f;
        float bh = 82f;

        if (y >= top && y <= top + bh) {
            if (x <= 28f + bw) cycleActive();
            else buyRandomEgg();
            return true;
        }

        if (y >= top + bh + gap && y <= top + bh * 2 + gap) {
            if (x <= 28f + bw) buyTapUpgrade();
            else buyPassiveUpgrade();
            return true;
        }

        return true;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(passiveTick);
        saveAndLeave();
        super.onDetachedFromWindow();
    }
}
