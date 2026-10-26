package me.nillerusr;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Layout kontrol sentuh yang diatur dari launcher.
 *
 * Disimpan sebagai teks sederhana (files/touch_profile.txt) dan diterjemahkan ke
 * file cfg yang dimengerti engine (touch_addbutton ...), lalu ditaruh di folder
 * overlay (files/overlay/cfg/touch.cfg) yang dimuat lewat -insert_search_path.
 *
 * Format baris touch_addbutton sama persis dengan yang ditulis engine
 * (game/client/touch.cpp, CTouchControls::WriteConfig).
 */
public class TouchProfile
{
	public static final String FILE_NAME = "touch_profile.txt";
	public static final float MIN_SIZE = 0.03f;

	/** Tekstur yang tersedia di extras_dir.vpk (materials/vgui/touch). */
	public static final String[] TEXTURES = {
		"vgui/touch/back", "vgui/touch/chat", "vgui/touch/chat_team", "vgui/touch/crouch",
		"vgui/touch/dpad_left", "vgui/touch/dpad_right", "vgui/touch/flash_light_filled",
		"vgui/touch/gamma", "vgui/touch/jump", "vgui/touch/load", "vgui/touch/map",
		"vgui/touch/menu", "vgui/touch/menu_a", "vgui/touch/menu_b", "vgui/touch/menu_x",
		"vgui/touch/menu_y", "vgui/touch/next_weap", "vgui/touch/prev_weap",
		"vgui/touch/reload", "vgui/touch/save", "vgui/touch/settings", "vgui/touch/shoot",
		"vgui/touch/shoot_alt", "vgui/touch/show_weapons", "vgui/touch/showconsole",
		"vgui/touch/speed", "vgui/touch/tduck", "vgui/touch/use", "vgui/touch/zoom"
	};

	public static class Preset
	{
		public final String label, name, texture, command;
		public Preset(String label, String name, String texture, String command)
		{
			this.label = label; this.name = name; this.texture = texture; this.command = command;
		}
	}

	public static final Preset[] PRESETS = {
		new Preset("Tembak", "attack", "vgui/touch/shoot", "+attack"),
		new Preset("Tembak alternatif", "attack2", "vgui/touch/shoot_alt", "+attack2"),
		new Preset("Lompat", "jump", "vgui/touch/jump", "+jump"),
		new Preset("Jongkok (tahan)", "duck", "vgui/touch/crouch", "+duck"),
		new Preset("Jongkok (toggle)", "tduck", "vgui/touch/tduck", ";+duck"),
		new Preset("Pakai / Use", "use", "vgui/touch/use", "+use"),
		new Preset("Reload", "reload", "vgui/touch/reload", "+reload"),
		new Preset("Senter", "flashlight", "vgui/touch/flash_light_filled", "impulse 100"),
		new Preset("Zoom", "zoom", "vgui/touch/zoom", "+zoom"),
		new Preset("Lari", "speed", "vgui/touch/speed", "+speed"),
		new Preset("Senjata berikutnya", "invnext", "vgui/touch/next_weap", "invnext"),
		new Preset("Senjata sebelumnya", "invprev", "vgui/touch/prev_weap", "invprev"),
		new Preset("Quick save", "savequick", "vgui/touch/save", "save quick"),
		new Preset("Quick load", "loadquick", "vgui/touch/load", "load quick"),
		new Preset("Konsol", "console", "vgui/touch/showconsole", "toggleconsole"),
		new Preset("Menu", "menu", "vgui/touch/menu", "gameui_activate"),
		new Preset("Chat", "chat", "vgui/touch/chat", "messagemode"),
		new Preset("Kustom (kosong)", "custom", "", "")
	};

	public static class Btn
	{
		public String name = "button";
		public String texture = "";
		public String command = "";
		public float x1, y1, x2, y2;
		public int r = 255, g = 255, b = 255, a = 155;
		public int flags = 0;

		public Btn() {}

		public Btn(String name, String texture, String command, float x1, float y1, float x2, float y2)
		{
			this.name = name; this.texture = texture; this.command = command;
			this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
		}

		/** Area _look dan _move: tidak digambar, hanya zona sentuhan. */
		public boolean isZone()
		{
			return "_look".equals(command) || "_move".equals(command);
		}

		public Btn copy()
		{
			Btn c = new Btn(name, texture, command, x1, y1, x2, y2);
			c.r = r; c.g = g; c.b = b; c.a = a; c.flags = flags;
			return c;
		}
	}

	public final ArrayList<Btn> buttons = new ArrayList<Btn>();
	/** Versi format file yang dibaca (profil versi 1 dibuat dengan layout bawaan yang tidak persegi). */
	public int version = 2;

	public float pitch = 90f;
	public float yaw = 120f;
	public float sensitivity = 3f;
	public float forwardZone = 0.06f;
	public float sideZone = 0.06f;
	public float preciseAmount = 0.5f;
	public boolean draw = true;

	// ------------------------------------------------------------------
	// default layout (salinan dari CTouchControls::ResetToDefaults)
	// ------------------------------------------------------------------

	/**
	 * Engine membuat tombol bawaan dengan aspect = 1.0, artinya PERSEGI di layar apa pun
	 * (y2 = y1 + lebar * (lebar_layar/tinggi_layar)). Koordinat bawaan di bawah hanya
	 * persegi di 16:9, jadi tinggi dihitung ulang untuk rasio layar perangkat ini.
	 */
	public static TouchProfile defaults(float screenAspect)
	{
		TouchProfile p = new TouchProfile();
		p.buttons.add(new Btn("look", "", "_look", 0.5f, 0f, 1f, 1f));
		p.buttons.add(new Btn("move", "", "_move", 0f, 0f, 0.5f, 1f));
		p.buttons.add(new Btn("use", "vgui/touch/use", "+use", 0.88f, 0.213333f, 1f, 0.426667f));
		p.buttons.add(new Btn("jump", "vgui/touch/jump", "+jump", 0.88f, 0.462222f, 1f, 0.675556f));
		p.buttons.add(new Btn("attack", "vgui/touch/shoot", "+attack", 0.76f, 0.583333f, 0.88f, 0.796667f));
		p.buttons.add(new Btn("attack2", "vgui/touch/shoot_alt", "+attack2", 0.76f, 0.32f, 0.88f, 0.533333f));
		p.buttons.add(new Btn("duck", "vgui/touch/crouch", "+duck", 0.88f, 0.746667f, 1f, 0.96f));
		p.buttons.add(new Btn("tduck", "vgui/touch/tduck", ";+duck", 0.56f, 0.817778f, 0.62f, 0.924444f));
		p.buttons.add(new Btn("zoom", "vgui/touch/zoom", "+zoom", 0.68f, 0f, 0.76f, 0.142222f));
		p.buttons.add(new Btn("speed", "vgui/touch/speed", "+speed", 0.18f, 0.568889f, 0.28f, 0.746667f));
		p.buttons.add(new Btn("loadquick", "vgui/touch/load", "load quick", 0.76f, 0f, 0.84f, 0.142222f));
		p.buttons.add(new Btn("savequick", "vgui/touch/save", "save quick", 0.84f, 0f, 0.92f, 0.142222f));
		p.buttons.add(new Btn("reload", "vgui/touch/reload", "+reload", 0f, 0.32f, 0.12f, 0.533333f));
		p.buttons.add(new Btn("flashlight", "vgui/touch/flash_light_filled", "impulse 100", 0.92f, 0f, 1f, 0.142222f));
		p.buttons.add(new Btn("invnext", "vgui/touch/next_weap", "invnext", 0f, 0.533333f, 0.12f, 0.746667f));
		p.buttons.add(new Btn("invprev", "vgui/touch/prev_weap", "invprev", 0f, 0.071111f, 0.12f, 0.284444f));
		p.buttons.add(new Btn("menu", "vgui/touch/menu", "gameui_activate", 0f, 0f, 0.08f, 0.142222f));
		p.squareAll(screenAspect, true);
		return p;
	}

	/**
	 * Jadikan tombol bergambar persegi (dalam piksel) di layar dengan rasio screenAspect.
	 * @param keepHeight true: tinggi tetap, lebar menyesuaikan (dipakai untuk layout bawaan,
	 *        yang menetapkan tinggi); false: sisi = yang lebih kecil dari lebar/tinggi saat ini.
	 * Titik tengah dipertahankan, lalu digeser supaya tetap di dalam layar.
	 * Area _look/_move tidak diubah.
	 */
	public void squareAll(float screenAspect, boolean keepHeight)
	{
		if (screenAspect <= 0f)
			screenAspect = 16f / 9f;
		for (Btn b : buttons)
		{
			if (b.isZone() || b.texture.length() == 0)
				continue;
			float cx = (b.x1 + b.x2) / 2f, cy = (b.y1 + b.y2) / 2f;
			float wpx = (b.x2 - b.x1) * screenAspect, hpx = b.y2 - b.y1; // satuan: tinggi layar
			float side = keepHeight ? hpx : Math.min(wpx, hpx);
			float w = side / screenAspect, h = side;
			if (w > 1f) { w = 1f; h = w * screenAspect; }
			if (h > 1f) { h = 1f; w = h / screenAspect; }
			float x1 = clamp01(cx - w / 2f), y1 = clamp01(cy - h / 2f);
			if (x1 + w > 1f) x1 = 1f - w;
			if (y1 + h > 1f) y1 = 1f - h;
			b.x1 = x1; b.y1 = y1; b.x2 = x1 + w; b.y2 = y1 + h;
		}
	}

	public void copyFrom(TouchProfile o)
	{
		buttons.clear();
		for (Btn b : o.buttons)
			buttons.add(b.copy());
		pitch = o.pitch; yaw = o.yaw; sensitivity = o.sensitivity;
		forwardZone = o.forwardZone; sideZone = o.sideZone;
		preciseAmount = o.preciseAmount; draw = o.draw;
	}

	public int indexOfName(String name)
	{
		for (int i = 0; i < buttons.size(); i++)
			if (buttons.get(i).name.equals(name))
				return i;
		return -1;
	}

	public String uniqueName(String base)
	{
		if (indexOfName(base) < 0)
			return base;
		for (int i = 2; i < 1000; i++)
			if (indexOfName(base + i) < 0)
				return base + i;
		return base + System.currentTimeMillis();
	}

	// ------------------------------------------------------------------
	// teks <-> objek
	// ------------------------------------------------------------------

	/** Buang karakter yang merusak format cfg / file profil. */
	public static String clean(String s)
	{
		if (s == null)
			return "";
		return s.replace("\"", "").replace("\t", " ").replace("\r", " ").replace("\n", " ").trim();
	}

	private static String f(float v)
	{
		return String.format(Locale.US, "%f", v);
	}

	private static float clamp01(float v)
	{
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	public String serialize()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("TOUCHPROFILE\t2\n");
		sb.append("pitch\t").append(f(pitch)).append('\n');
		sb.append("yaw\t").append(f(yaw)).append('\n');
		sb.append("sensitivity\t").append(f(sensitivity)).append('\n');
		sb.append("forwardzone\t").append(f(forwardZone)).append('\n');
		sb.append("sidezone\t").append(f(sideZone)).append('\n');
		sb.append("precise\t").append(f(preciseAmount)).append('\n');
		sb.append("draw\t").append(draw ? 1 : 0).append('\n');
		for (Btn b : buttons)
		{
			sb.append("btn\t").append(clean(b.name)).append('\t').append(clean(b.texture)).append('\t')
				.append(clean(b.command)).append('\t')
				.append(f(b.x1)).append('\t').append(f(b.y1)).append('\t')
				.append(f(b.x2)).append('\t').append(f(b.y2)).append('\t')
				.append(b.r).append('\t').append(b.g).append('\t').append(b.b).append('\t')
				.append(b.a).append('\t').append(b.flags).append('\n');
		}
		return sb.toString();
	}

	/** Baca profil; mengembalikan null kalau formatnya tidak dikenali. */
	public static TouchProfile parse(String text)
	{
		if (text == null)
			return null;
		String[] lines = text.split("\n");
		if (lines.length == 0 || !lines[0].startsWith("TOUCHPROFILE"))
			return null;

		TouchProfile p = new TouchProfile();
		p.version = 1;
		try { p.version = Integer.parseInt(lines[0].split("\t")[1].trim()); } catch (RuntimeException e) { }
		for (int i = 1; i < lines.length; i++)
		{
			String[] t = lines[i].split("\t", -1);
			try
			{
				if (t[0].equals("pitch")) p.pitch = Float.parseFloat(t[1]);
				else if (t[0].equals("yaw")) p.yaw = Float.parseFloat(t[1]);
				else if (t[0].equals("sensitivity")) p.sensitivity = Float.parseFloat(t[1]);
				else if (t[0].equals("forwardzone")) p.forwardZone = Float.parseFloat(t[1]);
				else if (t[0].equals("sidezone")) p.sideZone = Float.parseFloat(t[1]);
				else if (t[0].equals("precise")) p.preciseAmount = Float.parseFloat(t[1]);
				else if (t[0].equals("draw")) p.draw = !t[1].equals("0");
				else if (t[0].equals("btn") && t.length >= 13)
				{
					Btn b = new Btn(t[1], t[2], t[3],
						Float.parseFloat(t[4]), Float.parseFloat(t[5]),
						Float.parseFloat(t[6]), Float.parseFloat(t[7]));
					b.r = Integer.parseInt(t[8]);
					b.g = Integer.parseInt(t[9]);
					b.b = Integer.parseInt(t[10]);
					b.a = Integer.parseInt(t[11]);
					b.flags = Integer.parseInt(t[12]);
					p.buttons.add(b);
				}
			}
			catch (RuntimeException e)
			{
				// baris rusak dilewati
			}
		}
		return p.buttons.isEmpty() ? null : p;
	}

	// ------------------------------------------------------------------
	// cfg untuk engine
	// ------------------------------------------------------------------

	/** Pastikan zona look/move dan tombol menu ada supaya pemain tidak terkunci. */
	public void ensureEssentials()
	{
		boolean look = false, move = false, menu = false;
		for (Btn b : buttons)
		{
			if ("_look".equals(b.command)) look = true;
			if ("_move".equals(b.command)) move = true;
			if ("gameui_activate".equals(b.command)) menu = true;
		}
		if (!move) buttons.add(0, new Btn(uniqueName("move"), "", "_move", 0f, 0f, 0.5f, 1f));
		if (!look) buttons.add(0, new Btn(uniqueName("look"), "", "_look", 0.5f, 0f, 1f, 1f));
		if (!menu)
		{
			Btn m = new Btn(uniqueName("menu"), "vgui/touch/menu", "gameui_activate", 0f, 0f, 0.08f, 0.142222f);
			m.a = 155;
			buttons.add(m);
		}
	}

	/**
	 * @param screenAspect lebar/tinggi layar (mis. 16/9 = 1.777).
	 * Engine menghitung ulang y2 dari x1, y1, x2 dan "aspect" supaya tombol tetap
	 * proporsional di layar lain: y2 = y1 + (x2-x1) * (lebar/tinggi) * aspect.
	 */
	public String toCfg(float screenAspect)
	{
		if (screenAspect <= 0f)
			screenAspect = 16f / 9f;

		StringBuilder sb = new StringBuilder();
		sb.append("//=======================================================================\n");
		sb.append("//  dibuat otomatis oleh launcher (editor kontrol) - jangan diedit manual\n");
		sb.append("//=======================================================================\n\n");
		sb.append("touch_pitch \"").append(f(pitch)).append("\"\n");
		sb.append("touch_yaw \"").append(f(yaw)).append("\"\n");
		sb.append("touch_sensitivity \"").append(f(sensitivity)).append("\"\n");
		sb.append("touch_forwardzone \"").append(f(forwardZone)).append("\"\n");
		sb.append("touch_sidezone \"").append(f(sideZone)).append("\"\n");
		sb.append("touch_precise_amount \"").append(f(preciseAmount)).append("\"\n");
		sb.append("touch_draw \"").append(draw ? 1 : 0).append("\"\n\n");
		sb.append("touch_removeall\n");

		for (Btn b : buttons)
		{
			float x1 = clamp01(b.x1), y1 = clamp01(b.y1);
			float x2 = clamp01(b.x2), y2 = clamp01(b.y2);
			if (x2 < x1 + MIN_SIZE) x2 = Math.min(1f, x1 + MIN_SIZE);
			if (y2 < y1 + MIN_SIZE) y2 = Math.min(1f, y1 + MIN_SIZE);

			float aspect = (y2 - y1) / ((x2 - x1) * screenAspect);

			sb.append("touch_addbutton \"").append(clean(b.name)).append("\" \"")
				.append(clean(b.texture)).append("\" \"").append(clean(b.command)).append("\" ")
				.append(f(x1)).append(' ').append(f(y1)).append(' ')
				.append(f(x2)).append(' ').append(f(y2)).append(' ')
				.append(b.r).append(' ').append(b.g).append(' ').append(b.b).append(' ')
				.append(b.a).append(' ').append(b.flags).append(' ')
				.append(f(aspect)).append('\n');
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------
	// file di penyimpanan aplikasi
	// ------------------------------------------------------------------

	public static float screenAspect(Context ctx)
	{
		try
		{
			WindowManager wm = (WindowManager)ctx.getSystemService(Context.WINDOW_SERVICE);
			Display d = wm.getDefaultDisplay();
			DisplayMetrics dm = new DisplayMetrics();
			d.getRealMetrics(dm);
			float w = Math.max(dm.widthPixels, dm.heightPixels);
			float h = Math.min(dm.widthPixels, dm.heightPixels);
			if (h > 0f)
				return w / h;
		}
		catch (Throwable t)
		{
		}
		return 16f / 9f;
	}

	public static String overlayPath(Context ctx)
	{
		return ctx.getFilesDir().getPath() + "/overlay";
	}

	private static String readAll(File f) throws IOException
	{
		BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
		try
		{
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null)
				sb.append(line).append('\n');
			return sb.toString();
		}
		finally
		{
			r.close();
		}
	}

	private static void writeAll(File f, String text) throws IOException
	{
		File dir = f.getParentFile();
		if (dir != null)
			dir.mkdirs();
		Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
		try
		{
			w.write(text);
		}
		finally
		{
			w.close();
		}
	}

	public static TouchProfile load(Context ctx)
	{
		try
		{
			File f = new File(ctx.getFilesDir(), FILE_NAME);
			if (f.exists())
			{
				TouchProfile p = parse(readAll(f));
				if (p != null)
				{
					if (p.version < 2)
					{
						// v1: koordinat bawaan hanya persegi di 16:9 -> ikon terdistorsi di layar lain
						p.squareAll(screenAspect(ctx), false);
						p.version = 2;
					}
					return p;
				}
			}
		}
		catch (IOException e)
		{
		}
		return defaults(screenAspect(ctx));
	}

	public static void save(Context ctx, TouchProfile p) throws IOException
	{
		writeAll(new File(ctx.getFilesDir(), FILE_NAME), p.serialize());
	}

	/** Tulis overlay/cfg/touch.cfg yang akan dimuat engine. */
	public static void writeOverlay(Context ctx, TouchProfile p) throws IOException
	{
		p.ensureEssentials();
		writeAll(new File(overlayPath(ctx) + "/cfg", "touch.cfg"), p.toCfg(screenAspect(ctx)));
	}
}
