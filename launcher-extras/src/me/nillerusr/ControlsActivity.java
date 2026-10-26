package me.nillerusr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;

import me.nillerusr.TouchProfile.Btn;

/**
 * Editor tata letak kontrol sentuh. Dibuka dari launcher; hasilnya ditulis ke
 * files/touch_profile.txt dan files/overlay/cfg/touch.cfg (dimuat engine saat game dijalankan).
 */
public class ControlsActivity extends Activity implements ControlsEditorView.Listener
{
	private TouchProfile profile;
	private ControlsEditorView editor;
	private boolean dirty = false;
	private Button btnProps, btnDelete, btnDuplicate;

	@Override
	protected void onCreate(Bundle b)
	{
		super.onCreate(b);
		profile = TouchProfile.load(this);

		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(0xFF101010);

		HorizontalScrollView sv = new HorizontalScrollView(this);
		LinearLayout bar = new LinearLayout(this);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		sv.addView(bar);
		root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		addBtn(bar, "Tambah", new View.OnClickListener() { public void onClick(View v) { showAddDialog(); } });
		btnProps = addBtn(bar, "Properti", new View.OnClickListener() { public void onClick(View v) { showPropsDialog(); } });
		btnDuplicate = addBtn(bar, "Duplikat", new View.OnClickListener() { public void onClick(View v) { duplicateSelected(); } });
		btnDelete = addBtn(bar, "Hapus", new View.OnClickListener() { public void onClick(View v) { deleteSelected(); } });
		addBtn(bar, "Pengaturan", new View.OnClickListener() { public void onClick(View v) { showSettingsDialog(); } });
		addBtn(bar, "Persegikan", new View.OnClickListener() { public void onClick(View v) {
			profile.squareAll(TouchProfile.screenAspect(ControlsActivity.this), false);
			dirty = true;
			editor.invalidate();
			Toast.makeText(ControlsActivity.this, "Semua tombol bergambar dibuat persegi.", Toast.LENGTH_SHORT).show();
		} });
		addBtn(bar, "Reset", new View.OnClickListener() { public void onClick(View v) { confirmReset(); } });
		addBtn(bar, "Simpan", new View.OnClickListener() { public void onClick(View v) { save(true); } });
		addBtn(bar, "Keluar", new View.OnClickListener() { public void onClick(View v) { onBackPressed(); } });

		CheckBox cbRatio = new CheckBox(this);
		cbRatio.setText("Kunci rasio");
		cbRatio.setTextColor(0xFFFFFFFF);
		cbRatio.setChecked(true);
		bar.addView(cbRatio);

		CheckBox cbSnap = new CheckBox(this);
		cbSnap.setText("Magnet grid");
		cbSnap.setTextColor(0xFFFFFFFF);
		cbSnap.setChecked(true);
		bar.addView(cbSnap);

		editor = new ControlsEditorView(this);
		editor.setScreenAspect(TouchProfile.screenAspect(this));
		editor.setProfile(profile);
		editor.setListener(this);
		root.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		cbRatio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton v, boolean on) { editor.setKeepAspect(on); }
		});
		cbSnap.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton v, boolean on) { editor.setSnap(on); }
		});

		setContentView(root);
		updateButtons(null);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && Build.VERSION.SDK_INT >= 19)
		{
			getWindow().getDecorView().setSystemUiVisibility(
				View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
	}

	private Button addBtn(LinearLayout bar, String label, View.OnClickListener l)
	{
		Button bt = new Button(this);
		bt.setText(label);
		bt.setAllCaps(false);
		bt.setOnClickListener(l);
		bar.addView(bt);
		return bt;
	}

	private void updateButtons(Btn sel)
	{
		boolean on = sel != null;
		btnProps.setEnabled(on);
		btnDelete.setEnabled(on);
		btnDuplicate.setEnabled(on && !sel.isZone());
	}

	// ---- Listener -----------------------------------------------------

	public void onSelectionChanged(Btn sel) { updateButtons(sel); }
	public void onModified() { dirty = true; }

	// ---- aksi ---------------------------------------------------------

	private void showAddDialog()
	{
		final String[] labels = new String[TouchProfile.PRESETS.length];
		for (int i = 0; i < labels.length; i++)
			labels[i] = TouchProfile.PRESETS[i].label + (TouchProfile.PRESETS[i].command.length() > 0
				? "  (" + TouchProfile.PRESETS[i].command + ")" : "");

		new AlertDialog.Builder(this).setTitle("Tambah tombol")
			.setItems(labels, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int which)
				{
					TouchProfile.Preset p = TouchProfile.PRESETS[which];
					float asp = TouchProfile.screenAspect(ControlsActivity.this);
					float w = 0.08f, h = w * asp; // kira-kira persegi di layar
					Btn nb = new Btn(profile.uniqueName(p.name), p.texture, p.command,
						0.46f, 0.40f, 0.46f + w, 0.40f + h);
					profile.buttons.add(nb);
					dirty = true;
					editor.select(nb);
					if (p.command.length() == 0)
						showPropsDialog();
				}
			}).show();
	}

	private void duplicateSelected()
	{
		Btn s = editor.getSelected();
		if (s == null || s.isZone())
			return;
		Btn c = s.copy();
		c.name = profile.uniqueName(s.name);
		float w = c.x2 - c.x1, h = c.y2 - c.y1;
		c.x1 = Math.min(1f - w, c.x1 + 0.03f);
		c.y1 = Math.min(1f - h, c.y1 + 0.05f);
		c.x2 = c.x1 + w;
		c.y2 = c.y1 + h;
		profile.buttons.add(c);
		dirty = true;
		editor.select(c);
	}

	private void deleteSelected()
	{
		final Btn s = editor.getSelected();
		if (s == null)
			return;
		String msg = "Hapus tombol \"" + s.name + "\"?";
		if (s.isZone())
			msg += "\n\nArea ini wajib ada; kalau dihapus, akan dibuat ulang dengan ukuran bawaan saat disimpan.";
		new AlertDialog.Builder(this).setMessage(msg)
			.setPositiveButton("Hapus", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w)
				{
					profile.buttons.remove(s);
					dirty = true;
					editor.select(null);
				}
			}).setNegativeButton("Batal", null).show();
	}

	private void confirmReset()
	{
		new AlertDialog.Builder(this).setMessage("Kembalikan semua tombol ke tata letak bawaan?")
			.setPositiveButton("Reset", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w)
				{
					profile.copyFrom(TouchProfile.defaults(TouchProfile.screenAspect(ControlsActivity.this)));
					dirty = true;
					editor.setProfile(profile);
					updateButtons(null);
				}
			}).setNegativeButton("Batal", null).show();
	}

	private int dp(int v)
	{
		return (int)(v * getResources().getDisplayMetrics().density);
	}

	private LinearLayout form()
	{
		LinearLayout l = new LinearLayout(this);
		l.setOrientation(LinearLayout.VERTICAL);
		l.setPadding(dp(16), dp(8), dp(16), dp(8));
		return l;
	}

	private EditText field(LinearLayout parent, String label, String value, int inputType)
	{
		TextView t = new TextView(this);
		t.setText(label);
		parent.addView(t);
		EditText e = new EditText(this);
		e.setText(value);
		e.setSingleLine(true);
		e.setInputType(inputType);
		parent.addView(e);
		return e;
	}

	private void showPropsDialog()
	{
		final Btn s = editor.getSelected();
		if (s == null)
			return;

		LinearLayout l = form();
		final EditText name = field(l, "Nama", s.name, InputType.TYPE_CLASS_TEXT);
		final EditText cmd = field(l, s.isZone() ? "Perintah (tetap)" : "Perintah (mis. +attack, impulse 100)",
			s.command, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		cmd.setEnabled(!s.isZone());

		final Spinner tex = new Spinner(this);
		final String[] names = new String[TouchProfile.TEXTURES.length + 1];
		names[0] = "(tanpa gambar)";
		int sel = 0;
		for (int i = 0; i < TouchProfile.TEXTURES.length; i++)
		{
			names[i + 1] = TouchProfile.TEXTURES[i];
			if (TouchProfile.TEXTURES[i].equals(s.texture))
				sel = i + 1;
		}
		tex.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, names));
		tex.setSelection(sel);
		if (!s.isZone())
		{
			TextView t = new TextView(this);
			t.setText("Gambar tombol");
			l.addView(t);
			l.addView(tex);
		}

		final TextView alphaLabel = new TextView(this);
		alphaLabel.setText("Transparansi (alpha): " + s.a);
		final SeekBar alpha = new SeekBar(this);
		alpha.setMax(255);
		alpha.setProgress(s.a);
		alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			public void onProgressChanged(SeekBar sb, int p, boolean u) { alphaLabel.setText("Transparansi (alpha): " + p); }
			public void onStartTrackingTouch(SeekBar sb) {}
			public void onStopTrackingTouch(SeekBar sb) {}
		});
		if (!s.isZone())
		{
			l.addView(alphaLabel);
			l.addView(alpha);
		}

		new AlertDialog.Builder(this).setTitle("Properti tombol").setView(l)
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w)
				{
					String n = TouchProfile.clean(name.getText().toString());
					if (n.length() > 0 && !n.equals(s.name))
					{
						int other = profile.indexOfName(n);
						s.name = (other >= 0 && profile.buttons.get(other) != s) ? profile.uniqueName(n) : n;
					}
					if (!s.isZone())
					{
						s.command = TouchProfile.clean(cmd.getText().toString());
						int pos = tex.getSelectedItemPosition();
						s.texture = pos <= 0 ? "" : TouchProfile.TEXTURES[pos - 1];
						s.a = alpha.getProgress();
					}
					dirty = true;
					editor.invalidate();
				}
			}).setNegativeButton("Batal", null).show();
	}

	private static String num(float v)
	{
		return String.format(Locale.US, "%.3f", v);
	}

	private static float parse(EditText e, float def)
	{
		try
		{
			return Float.parseFloat(e.getText().toString().trim().replace(',', '.'));
		}
		catch (NumberFormatException x)
		{
			return def;
		}
	}

	private void showSettingsDialog()
	{
		int dec = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
		LinearLayout l = form();
		final EditText sens = field(l, "Sensitivitas (touch_sensitivity)", num(profile.sensitivity), dec);
		final EditText pitch = field(l, "Kecepatan lihat vertikal (touch_pitch)", num(profile.pitch), dec);
		final EditText yaw = field(l, "Kecepatan lihat horizontal (touch_yaw)", num(profile.yaw), dec);
		final EditText fz = field(l, "Zona maju/mundur (touch_forwardzone)", num(profile.forwardZone), dec);
		final EditText sz = field(l, "Zona geser kiri/kanan (touch_sidezone)", num(profile.sideZone), dec);
		final CheckBox draw = new CheckBox(this);
		draw.setText("Tampilkan tombol di game (touch_draw)");
		draw.setChecked(profile.draw);
		l.addView(draw);

		new AlertDialog.Builder(this).setTitle("Pengaturan sentuhan").setView(l)
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w)
				{
					profile.sensitivity = Math.max(0.1f, parse(sens, profile.sensitivity));
					profile.pitch = Math.max(1f, parse(pitch, profile.pitch));
					profile.yaw = Math.max(1f, parse(yaw, profile.yaw));
					profile.forwardZone = Math.max(0f, Math.min(0.5f, parse(fz, profile.forwardZone)));
					profile.sideZone = Math.max(0f, Math.min(0.5f, parse(sz, profile.sideZone)));
					profile.draw = draw.isChecked();
					dirty = true;
				}
			}).setNegativeButton("Batal", null).show();
	}

	private boolean save(boolean toast)
	{
		try
		{
			TouchProfile.save(this, profile);
			TouchProfile.writeOverlay(this, profile);
			SharedPreferences.Editor ed = getSharedPreferences("mod", 0).edit();
			ed.putBoolean(LauncherExtras.PREF_TOUCH_ON, true);
			ed.commit();
			dirty = false;
			if (toast)
				Toast.makeText(this, "Tersimpan. Kontrol dipakai saat game dijalankan.", Toast.LENGTH_LONG).show();
			return true;
		}
		catch (IOException e)
		{
			Toast.makeText(this, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
			return false;
		}
	}

	@Override
	public void onBackPressed()
	{
		if (!dirty)
		{
			finish();
			return;
		}
		new AlertDialog.Builder(this).setMessage("Ada perubahan yang belum disimpan.")
			.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w) { if (save(false)) finish(); }
			})
			.setNegativeButton("Buang", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w) { dirty = false; finish(); }
			})
			.setNeutralButton("Batal", null).show();
	}
}
