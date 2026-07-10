package com.example.dailyfocus.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.example.dailyfocus.R;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Heatmap de activitate în stil GitHub: o coloană pe săptămână, un rând pe zi
 * (Luni sus), intensitatea culorii = numărul de task-uri completate în ziua aceea.
 * Datele sunt chei de zi (miezul nopții) -> număr completări.
 */
public class HeatmapView extends View {

    private static final int WEEKS = 18;
    private static final int DAYS = 7;

    private final Map<Long, Integer> counts = new HashMap<>();
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cellRect = new RectF();

    private int[] levelColors;
    private long gridStartDay; // Luni, acum WEEKS săptămâni
    private long todayKey;

    public HeatmapView(Context context) {
        super(context);
        init();
    }

    public HeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        levelColors = new int[]{
                ContextCompat.getColor(getContext(), R.color.heatmap_level0),
                ContextCompat.getColor(getContext(), R.color.heatmap_level1),
                ContextCompat.getColor(getContext(), R.color.heatmap_level2),
                ContextCompat.getColor(getContext(), R.color.heatmap_level3),
                ContextCompat.getColor(getContext(), R.color.heatmap_level4)
        };
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.heatmap_label));
        labelPaint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 10);
        computeGridStart();
    }

    private void computeGridStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        todayKey = cal.getTimeInMillis();
        // Înapoi la lunea săptămânii curente
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        cal.add(Calendar.WEEK_OF_YEAR, -(WEEKS - 1));
        gridStartDay = cal.getTimeInMillis();
    }

    public void setData(Map<Long, Integer> dayCounts) {
        counts.clear();
        if (dayCounts != null) counts.putAll(dayCounts);
        computeGridStart();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            height = (int) (getResources().getDisplayMetrics().density * 120);
        }
        float cell = height / (float) DAYS;
        int labelWidth = (int) labelPaint.measureText("MM") + 8;
        int width = (int) (labelWidth + cell * WEEKS);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cell = getHeight() / (float) DAYS;
        float gap = Math.max(1f, cell * 0.12f);
        float labelWidth = labelPaint.measureText("MM") + 8;

        // Etichete pentru Luni / Miercuri / Vineri
        String[] dayLetters = getResources().getStringArray(R.array.heatmap_day_letters);
        for (int row : new int[]{0, 2, 4}) {
            canvas.drawText(dayLetters[row], 0, row * cell + cell * 0.75f, labelPaint);
        }

        int maxCount = 1;
        for (int c : counts.values()) {
            if (c > maxCount) maxCount = c;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(gridStartDay);

        for (int week = 0; week < WEEKS; week++) {
            for (int day = 0; day < DAYS; day++) {
                long key = cal.getTimeInMillis();
                if (key > todayKey) break;

                Integer count = counts.get(key);
                int level;
                if (count == null || count == 0) {
                    level = 0;
                } else {
                    // 4 niveluri de intensitate raportate la ziua cea mai plină
                    level = 1 + Math.min(3, (count * 4 - 1) / Math.max(1, maxCount));
                }
                cellPaint.setColor(levelColors[level]);

                float left = labelWidth + week * cell;
                float top = day * cell;
                cellRect.set(left, top, left + cell - gap, top + cell - gap);
                canvas.drawRoundRect(cellRect, gap, gap, cellPaint);

                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
    }
}
