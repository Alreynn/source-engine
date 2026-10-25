package me.nillerusr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import me.nillerusr.TouchProfile.Btn;

/**
 * Kanvas editor: menggambar layar (rasio sama dengan layar perangkat) dan semua
 * tombol sentuh. Geser tombol = pindah, tarik pegangan di pojok kanan bawah = ubah ukuran.
 */
public class ControlsEditorView extends View
{
	public interface Listener
	{
		void onSelectionChanged(Btn selected);
		void onModified();
	}

	private static final int MODE_NONE = 0, MODE_MOVE = 1, MODE_RESIZE = 2;

	private TouchProfile profile;
	private Btn selected;
	private Listener listener;
	private float screenAspect = 16f / 9f;
	private boolean keepAspect = true;
	private boolean snap = true;
	private static final float GRID = 0.02f;

	private final RectF screen = new RectF();
	private final RectF tmp = new RectF();
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint grid = new Paint();
	private final float density;

	private int mode = MODE_NONE;
	private float grabDx, grabDy;     // offset jari terhadap pojok kiri-atas tombol (normalisasi)
	private float startRatio = 1f;    // tinggi/lebar (piksel) saat mulai resize

	public ControlsEditorView(Context ctx)
	{
		super(ctx);
		density = ctx.getResources().getDisplayMetrics().density;
		stroke.setStyle(Paint.Style.STROKE);
		stroke.setStrokeWidth(2f * density);
		text.setTextAlign(Paint.Align.CENTER);
		text.setTextSize(11f * density);
		grid.setColor(0x22FFFFFF);
		grid.setStrokeWidth(1f);
		setBackgroundColor(0xFF101010);
	}

	public void setProfile(TouchProfile p)
	{
		profile = p;
		selected = null;
		invalidate();
	}

	public void setListener(Listener l) { listener = l; }
	public void setScreenAspect(float a) { if (a > 0f) screenAspect = a; invalidate(); }
	public void setKeepAspect(boolean b) { keepAspect = b; }
	public boolean getKeepAspect() { return keepAspect; }
	public void setSnap(boolean b) { snap = b; }
	public Btn getSelected() { return selected; }

	public void select(Btn b)
	{
		selected = b;
		if (listener != null)
			listener.onSelectionChanged(b);
		invalidate();
	}

	// ---- geometri -----------------------------------------------------

	private void computeScreenRect()
	{
		float w = getWidth(), h = getHeight();
		float pad = 6f * density;
		float aw = w - 2 * pad, ah = h - 2 * pad;
		float rw = aw, rh = aw / screenAspect;
		if (rh > ah)
		{
			rh = ah;
			rw = ah * screenAspect;
		}
		float l = (w - rw) / 2f, t = (h - rh) / 2f;
		screen.set(l, t, l + rw, t + rh);
	}

	private void toPx(Btn b, RectF out)
	{
		out.set(screen.left + b.x1 * screen.width(), screen.top + b.y1 * screen.height(),
			screen.left + b.x2 * screen.width(), screen.top + b.y2 * screen.height());
	}

	private float nx(float px) { return (px - screen.left) / screen.width(); }
	private float ny(float py) { return (py - screen.top) / screen.height(); }

	private static float clamp(float v, float lo, float hi)
	{
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private float snapv(float v)
	{
		return snap ? Math.round(v / GRID) * GRID : v;
	}

	// ---- gambar -------------------------------------------------------

	@Override
	protected void onDraw(Canvas c)
	{
		super.onDraw(c);
		computeScreenRect();

		fill.setStyle(Paint.Style.FILL);
		fill.setColor(0xFF1E2A33);
		c.drawRect(screen, fill);

		for (float g = 0.1f; g < 0.999f; g += 0.1f)
		{
			c.drawLine(screen.left + g * screen.width(), screen.top,
				screen.left + g * screen.width(), screen.bottom, grid);
			c.drawLine(screen.left, screen.top + g * screen.height(),
				screen.right, screen.top + g * screen.height(), grid);
		}
		stroke.setColor(0xFF607D8B);
		c.drawRect(screen, stroke);

		if (profile == null)
			return;

		// zona dulu (di bawah), baru tombol
		for (int pass = 0; pass < 2; pass++)
		{
			for (Btn b : profile.buttons)
			{
				if (b.isZone() != (pass == 0))
					continue;
				drawButton(c, b);
			}
		}

		if (selected != null)
		{
			toPx(selected, tmp);
			fill.setColor(0xFFFFEB3B);
			c.drawCircle(tmp.right, tmp.bottom, 9f * density, fill);
		}
	}

	private void drawButton(Canvas c, Btn b)
	{
		toPx(b, tmp);
		boolean sel = b == selected;

		if (b.isZone())
		{
			fill.setColor("_look".equals(b.command) ? 0x2242A5F5 : 0x2266BB6A);
			c.drawRect(tmp, fill);
			stroke.setColor(sel ? 0xFFFFEB3B : ("_look".equals(b.command) ? 0x8842A5F5 : 0x8866BB6A));
			c.drawRect(tmp, stroke);
			text.setColor(0xAAFFFFFF);
			c.drawText("_look".equals(b.command) ? "AREA LIHAT" : "AREA GERAK",
				tmp.centerX(), tmp.top + 16f * density, text);
			return;
		}

		fill.setColor((clampInt(b.a) << 24) | 0x00455A64);
		c.drawRoundRect(tmp, 8f * density, 8f * density, fill);
		stroke.setColor(sel ? 0xFFFFEB3B : 0xFFFFFFFF);
		c.drawRoundRect(tmp, 8f * density, 8f * density, stroke);

		text.setColor(0xFFFFFFFF);
		float cy = tmp.centerY();
		c.drawText(shorten(b.name, tmp.width()), tmp.centerX(), cy - 2f * density, text);
		text.setColor(0xFFB0BEC5);
		c.drawText(shorten(b.command, tmp.width()), tmp.centerX(), cy + 12f * density, text);
	}

	private static int clampInt(int a)
	{
		// alpha minimal supaya tombol tetap terlihat di editor
		return a < 60 ? 60 : (a > 255 ? 255 : a);
	}

	private String shorten(String s, float widthPx)
	{
		if (s == null)
			return "";
		int max = (int)(widthPx / (text.getTextSize() * 0.6f));
		if (max < 2)
			return "";
		return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
	}

	// ---- sentuhan -----------------------------------------------------

	private Btn hitTest(float px, float py)
	{
		if (profile == null)
			return null;
		for (int pass = 0; pass < 2; pass++) // tombol dulu, zona terakhir
		{
			boolean zones = pass == 1;
			for (int i = profile.buttons.size() - 1; i >= 0; i--)
			{
				Btn b = profile.buttons.get(i);
				if (b.isZone() != zones)
					continue;
				toPx(b, tmp);
				if (tmp.contains(px, py))
					return b;
			}
		}
		return null;
	}

	@Override
	public boolean onTouchEvent(MotionEvent e)
	{
		if (profile == null)
			return false;

		float px = e.getX(), py = e.getY();

		switch (e.getActionMasked())
		{
		case MotionEvent.ACTION_DOWN:
		{
			mode = MODE_NONE;

			if (selected != null)
			{
				toPx(selected, tmp);
				float dx = px - tmp.right, dy = py - tmp.bottom;
				float r = 24f * density;
				if (dx * dx + dy * dy <= r * r)
				{
					mode = MODE_RESIZE;
					float wpx = Math.max(1f, tmp.width()), hpx = Math.max(1f, tmp.height());
					startRatio = hpx / wpx;
					return true;
				}
			}

			Btn hit = hitTest(px, py);
			if (hit != selected)
				select(hit);
			if (hit != null)
			{
				mode = MODE_MOVE;
				grabDx = nx(px) - hit.x1;
				grabDy = ny(py) - hit.y1;
			}
			return true;
		}

		case MotionEvent.ACTION_MOVE:
		{
			if (selected == null)
				return true;

			if (mode == MODE_MOVE)
			{
				float w = selected.x2 - selected.x1, h = selected.y2 - selected.y1;
				float x1 = snapv(nx(px) - grabDx), y1 = snapv(ny(py) - grabDy);
				x1 = clamp(x1, 0f, 1f - w);
				y1 = clamp(y1, 0f, 1f - h);
				selected.x1 = x1; selected.y1 = y1;
				selected.x2 = x1 + w; selected.y2 = y1 + h;
				modified();
			}
			else if (mode == MODE_RESIZE)
			{
				float minW = TouchProfile.MIN_SIZE, minH = TouchProfile.MIN_SIZE;
				float x2 = clamp(snapv(nx(px)), selected.x1 + minW, 1f);
				float y2 = clamp(snapv(ny(py)), selected.y1 + minH, 1f);

				if (keepAspect && !selected.isZone())
				{
					// pertahankan rasio piksel; ikuti sumbu yang digeser lebih jauh
					float wn = x2 - selected.x1;
					float hpx = wn * screen.width() * startRatio;
					float hn = hpx / screen.height();
					if (selected.y1 + hn > 1f)
					{
						hn = 1f - selected.y1;
						wn = hn * screen.height() / startRatio / screen.width();
					}
					x2 = selected.x1 + Math.max(wn, minW);
					y2 = selected.y1 + Math.max(hn, minH);
				}
				selected.x2 = x2;
				selected.y2 = y2;
				modified();
			}
			return true;
		}

		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			mode = MODE_NONE;
			return true;
		}
		return true;
	}

	private void modified()
	{
		if (listener != null)
			listener.onModified();
		invalidate();
	}
}
