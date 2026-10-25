package me.nillerusr;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Tambahan launcher: (1) overlay shader versi lama, (2) kontrol sentuh kustom.
 * Dipanggil dari LauncherActivity lewat dua baris tambahan (lihat apply_patch.py).
 *
 * Tidak ada file milik Valve yang dibundel; folder/file shader ditunjuk pengguna
 * dari salinan game miliknya sendiri.
 */
public class LauncherExtras
{
	public static final String PREF_SHADER_ON = "legacy_shaders_on";
	public static final String PREF_SHADER_PATH = "legacy_shaders_path";
	public static final String PREF_TOUCH_ON = "custom_touch_on";

	private static int dp(Activity a, int v)
	{
		return (int)(v * a.getResources().getDisplayMetrics().density);
	}

	private static LinearLayout.LayoutParams lp(Activity a)
	{
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(dp(a, 3), dp(a, 5), dp(a, 3), dp(a, 5));
		return p;
	}

	private static TextView heading(Activity a, String s)
	{
		TextView t = new TextView(a);
		t.setText(s);
		t.setTextSize(18f);
		t.setTextColor(0xFFFFFFFF);
		LinearLayout.LayoutParams p = lp(a);
		p.leftMargin = dp(a, 5);
		p.topMargin = dp(a, 14);
		t.setLayoutParams(p);
		return t;
	}

	private static TextView hint(Activity a, String s)
	{
		TextView t = new TextView(a);
		t.setText(s);
		t.setTextSize(12f);
		t.setTextColor(0xFFB0B0B0);
		t.setLayoutParams(lp(a));
		return t;
	}

	/** Tambahkan bagian "Shader" dan "Kontrol sentuh" ke bagian bawah form launcher. */
	public static void addViews(final Activity act, LinearLayout body, final SharedPreferences pref)
	{
		// ---------- shader versi lama ----------
		body.addView(heading(act, "Shader (versi lama / pra-Anniversary)"));
		body.addView(hint(act, "Isi dengan folder yang berisi shaders/fxc/*.vcs, atau file *_dir.vpk dari salinan "
			+ "game versi lama milikmu sendiri. Pisahkan dengan koma kalau lebih dari satu. "
			+ "Isinya dimuat lebih dulu daripada shader di folder game."));

		final CheckBox cbShader = new CheckBox(act);
		cbShader.setText("Pakai shader dari lokasi ini");
		cbShader.setTextColor(0xFFFFFFFF);
		cbShader.setChecked(pref.getBoolean(PREF_SHADER_ON, false));
		cbShader.setLayoutParams(lp(act));
		body.addView(cbShader);

		final EditText path = new EditText(act);
		path.setSingleLine(true);
		path.setHint("/sdcard/hl2-legacy/shaders  atau  /sdcard/hl2-legacy/hl2_misc_dir.vpk");
		path.setText(pref.getString(PREF_SHADER_PATH, ""));
		path.setLayoutParams(lp(act));
		body.addView(path);

		final TextView status = hint(act, "");
		Button check = new Button(act);
		check.setText("Periksa lokasi shader");
		check.setLayoutParams(lp(act));
		body.addView(check);
		body.addView(status);

		cbShader.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton v, boolean on)
			{
				SharedPreferences.Editor e = pref.edit();
				e.putBoolean(PREF_SHADER_ON, on);
				e.commit();
			}
		});
		path.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			public void onTextChanged(CharSequence s, int a, int b, int c) {}
			public void afterTextChanged(Editable s)
			{
				SharedPreferences.Editor e = pref.edit();
				e.putString(PREF_SHADER_PATH, s.toString());
				e.commit();
			}
		});
		check.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v)
			{
				StringBuilder sb = new StringBuilder();
				ArrayList<String> ok = validShaderPaths(path.getText().toString(), sb);
				status.setText((ok.isEmpty() ? "Tidak ada lokasi yang valid.\n" : ok.size() + " lokasi valid.\n") + sb);
			}
		});

		// ---------- kontrol sentuh ----------
		body.addView(heading(act, "Kontrol sentuh"));
		body.addView(hint(act, "Atur posisi dan ukuran tombol di sini, bukan di dalam game. "
			+ "Layout disimpan di penyimpanan aplikasi dan dipasang otomatis tiap game dijalankan."));

		final CheckBox cbTouch = new CheckBox(act);
		cbTouch.setText("Pakai kontrol kustom");
		cbTouch.setTextColor(0xFFFFFFFF);
		cbTouch.setChecked(pref.getBoolean(PREF_TOUCH_ON, false));
		cbTouch.setLayoutParams(lp(act));
		body.addView(cbTouch);
		cbTouch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton v, boolean on)
			{
				SharedPreferences.Editor e = pref.edit();
				e.putBoolean(PREF_TOUCH_ON, on);
				e.commit();
			}
		});

		Button edit = new Button(act);
		edit.setText("Edit kontrol sentuh");
		edit.setLayoutParams(lp(act));
		body.addView(edit);
		edit.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v)
			{
				act.startActivity(new Intent(act, ControlsActivity.class));
				cbTouch.setChecked(true);
			}
		});
	}

	// ------------------------------------------------------------------

	private static boolean hasVcs(File dir)
	{
		File fxc = new File(dir, "shaders/fxc");
		if (!fxc.isDirectory())
			return false;
		String[] names = fxc.list();
		if (names == null)
			return false;
		for (String n : names)
			if (n.toLowerCase().endsWith(".vcs"))
				return true;
		return false;
	}

	/** Kembalikan daftar path valid; sb diisi alasan untuk yang tidak valid. */
	public static ArrayList<String> validShaderPaths(String raw, StringBuilder sb)
	{
		ArrayList<String> out = new ArrayList<String>();
		if (raw == null)
			return out;

		for (String part : raw.split(","))
		{
			String p = part.trim();
			if (p.length() == 0)
				continue;

			while (p.length() > 1 && p.endsWith("/"))
				p = p.substring(0, p.length() - 1);

			File f = new File(p);
			if (f.isFile())
			{
				if (p.toLowerCase().endsWith("_dir.vpk"))
					out.add(p);
				else
					sb.append("- ").append(p).append(": bukan file *_dir.vpk\n");
			}
			else if (f.isDirectory())
			{
				if (hasVcs(f))
					out.add(p);
				else
					sb.append("- ").append(p).append(": tidak ada shaders/fxc/*.vcs di dalamnya\n");
			}
			else
			{
				sb.append("- ").append(p).append(": tidak ditemukan\n");
			}
		}
		return out;
	}

	/**
	 * Dipanggil dari LauncherActivity.startSource sebelum startActivity.
	 * - menaruh path shader lama di extra "vpk" (dibaca ValveActivity2.initNatives),
	 * - menulis overlay kontrol sentuh dan menambah "-insert_search_path" ke extra "argv".
	 */
	public static void applyToIntent(Activity act, Intent intent, String baseArgv)
	{
		SharedPreferences pref = act.getSharedPreferences("mod", 0);
		String argv = baseArgv == null ? "" : baseArgv.trim();

		if (pref.getBoolean(PREF_SHADER_ON, false))
		{
			StringBuilder why = new StringBuilder();
			ArrayList<String> ok = validShaderPaths(pref.getString(PREF_SHADER_PATH, ""), why);
			if (ok.isEmpty())
			{
				Toast.makeText(act, "Shader lama tidak dipakai:\n" + why, Toast.LENGTH_LONG).show();
			}
			else
			{
				StringBuilder j = new StringBuilder();
				for (String s : ok)
				{
					if (j.length() > 0)
						j.append(',');
					j.append(s);
				}
				intent.putExtra("vpk", j.toString());
			}
		}

		if (pref.getBoolean(PREF_TOUCH_ON, false))
		{
			if (argv.contains("-insert_search_path"))
			{
				Toast.makeText(act, "Argumen sudah berisi -insert_search_path; kontrol kustom dilewati.",
					Toast.LENGTH_LONG).show();
			}
			else
			{
				try
				{
					TouchProfile.writeOverlay(act, TouchProfile.load(act));
					argv = (argv + " -insert_search_path " + TouchProfile.overlayPath(act)).trim();
				}
				catch (IOException e)
				{
					Toast.makeText(act, "Gagal menulis kontrol kustom: " + e.getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		}

		if (argv.length() > 0)
			intent.putExtra("argv", argv);
	}
}
