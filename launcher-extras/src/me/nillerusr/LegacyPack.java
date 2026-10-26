package me.nillerusr;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * "Paket legacy": zip berisi file game versi lama (terutama shaders/fxc/*.vcs) yang dipasang
 * ke files/legacy_pack/ dan dimuat engine lebih dulu daripada file di folder game.
 *
 * Launcher TIDAK membawa file Valve. Paket dibuat pengguna dari salinan game miliknya
 * (tools/make_legacy_pack.py) dan diunduh dari alamat yang pengguna tentukan sendiri,
 * atau dipasang dari file zip di perangkat.
 */
public class LegacyPack
{
	public static final long MAX_TOTAL_BYTES = 768L * 1024 * 1024;
	public static final int MAX_ENTRIES = 20000;

	/** Hanya tipe data; tidak ada .so/.dex/.jar/.apk/skrip. */
	private static final String[] ALLOWED_EXT = {
		".vcs", ".txt", ".res", ".vmt", ".vtf", ".bsp", ".mdl", ".vtx", ".vvd", ".phy", ".ani",
		".cfg", ".pcf", ".nav", ".lst", ".vpk", ".wav", ".mp3", ".vfe", ".vcd", ".ttf"
	};

	public interface Progress
	{
		void onMessage(String msg);
	}

	public static File dir(Context ctx)
	{
		return new File(ctx.getFilesDir(), "legacy_pack");
	}

	/** Jumlah file .vcs di dalam paket terpasang (0 = belum terpasang). */
	public static int shaderCount(File packDir)
	{
		File fxc = new File(packDir, "shaders/fxc");
		String[] names = fxc.list();
		int n = 0;
		if (names != null)
			for (String s : names)
				if (s.toLowerCase().endsWith(".vcs"))
					n++;
		return n;
	}

	public static boolean isInstalled(Context ctx)
	{
		return shaderCount(dir(ctx)) > 0;
	}

	public static String describe(Context ctx)
	{
		File d = dir(ctx);
		int n = shaderCount(d);
		if (n == 0)
			return "Belum ada paket legacy terpasang.";
		return "Terpasang: " + n + " file shader (" + (dirSize(d) / (1024 * 1024)) + " MB) di " + d.getPath();
	}

	private static long dirSize(File f)
	{
		if (f.isFile())
			return f.length();
		long t = 0;
		File[] list = f.listFiles();
		if (list != null)
			for (File c : list)
				t += dirSize(c);
		return t;
	}

	public static void deleteRecursive(File f)
	{
		File[] list = f.listFiles();
		if (list != null)
			for (File c : list)
				deleteRecursive(c);
		f.delete();
	}

	public static void remove(Context ctx)
	{
		deleteRecursive(dir(ctx));
	}

	// ------------------------------------------------------------------
	// ekstraksi aman
	// ------------------------------------------------------------------

	static String safeName(String raw)
	{
		if (raw == null)
			return null;
		String n = raw.replace('\\', '/');
		while (n.startsWith("/"))
			n = n.substring(1);
		if (n.length() == 0 || n.endsWith("/"))
			return null;
		for (String part : n.split("/"))
			if (part.equals("..") || part.equals(".") || part.length() == 0 || part.indexOf('\0') >= 0)
				return null;
		String low = n.toLowerCase();
		for (String e : ALLOWED_EXT)
			if (low.endsWith(e))
				return n;
		return null;
	}

	/**
	 * Ekstrak zip ke destDir (lewat folder sementara, lalu diganti utuh).
	 * @return jumlah file yang diekstrak
	 */
	public static int extractZip(InputStream in, File destDir, Progress pr) throws IOException
	{
		File tmp = new File(destDir.getPath() + ".tmp");
		deleteRecursive(tmp);
		if (!tmp.mkdirs())
			throw new IOException("Tidak bisa membuat folder sementara");

		int files = 0, skipped = 0;
		long total = 0;
		boolean ok = false;
		try
		{
			ZipInputStream zin = new ZipInputStream(in);
			ZipEntry e;
			byte[] buf = new byte[64 * 1024];
			while ((e = zin.getNextEntry()) != null)
			{
				if (e.isDirectory())
					continue;
				if (++files + skipped > MAX_ENTRIES)
					throw new IOException("Zip berisi terlalu banyak file");

				String name = safeName(e.getName());
				if (name == null)
				{
					skipped++;
					files--;
					continue;
				}

				File out = new File(tmp, name);
				// pastikan tetap di dalam tmp (jaga-jaga)
				if (!out.getCanonicalPath().startsWith(tmp.getCanonicalPath() + File.separator))
					throw new IOException("Nama file tidak aman: " + name);
				File parent = out.getParentFile();
				if (!parent.isDirectory() && !parent.mkdirs())
					throw new IOException("Gagal membuat folder " + parent);

				FileOutputStream fo = new FileOutputStream(out);
				try
				{
					int r;
					while ((r = zin.read(buf)) > 0)
					{
						total += r;
						if (total > MAX_TOTAL_BYTES)
							throw new IOException("Isi zip melebihi batas ukuran");
						fo.write(buf, 0, r);
					}
				}
				finally
				{
					fo.close();
				}
				if (pr != null && files % 200 == 0)
					pr.onMessage("Mengekstrak... " + files + " file");
			}

			if (shaderCount(tmp) == 0)
				throw new IOException("Zip tidak berisi shaders/fxc/*.vcs");

			deleteRecursive(destDir);
			if (!tmp.renameTo(destDir))
				throw new IOException("Gagal memasang paket");
			ok = true;
			return files;
		}
		finally
		{
			if (!ok)
				deleteRecursive(tmp);
		}
	}

	public static int installFromFile(Context ctx, File zip, Progress pr) throws IOException
	{
		if (!zip.isFile())
			throw new IOException("File tidak ditemukan: " + zip);
		InputStream in = new FileInputStream(zip);
		try
		{
			return extractZip(in, dir(ctx), pr);
		}
		finally
		{
			in.close();
		}
	}

	// ------------------------------------------------------------------
	// unduh
	// ------------------------------------------------------------------

	private static String hex(byte[] b)
	{
		StringBuilder sb = new StringBuilder();
		for (byte x : b)
			sb.append(String.format("%02x", x & 0xff));
		return sb.toString();
	}

	/** Salin stream ke file sambil menghitung SHA-256 dan membatasi ukuran. Mengembalikan hash hex. */
	static String copyHashed(InputStream in, File out, long max, long expectedLen, Progress pr) throws IOException
	{
		MessageDigest md;
		try
		{
			md = MessageDigest.getInstance("SHA-256");
		}
		catch (java.security.NoSuchAlgorithmException e)
		{
			throw new IOException("SHA-256 tidak tersedia");
		}

		FileOutputStream fo = new FileOutputStream(out);
		try
		{
			byte[] buf = new byte[64 * 1024];
			long total = 0;
			int r;
			long lastMsg = 0;
			while ((r = in.read(buf)) > 0)
			{
				total += r;
				if (total > max)
					throw new IOException("Unduhan melebihi batas ukuran");
				md.update(buf, 0, r);
				fo.write(buf, 0, r);
				if (pr != null && total - lastMsg > 2 * 1024 * 1024)
				{
					lastMsg = total;
					pr.onMessage("Mengunduh... " + (total / (1024 * 1024)) + " MB"
						+ (expectedLen > 0 ? " / " + (expectedLen / (1024 * 1024)) + " MB" : ""));
				}
			}
		}
		finally
		{
			fo.close();
		}
		return hex(md.digest());
	}

	public static String normalizeSha(String s)
	{
		return s == null ? "" : s.trim().toLowerCase().replace(" ", "");
	}

	/** Verifikasi hash; sha kosong = lewati. Melempar IOException kalau tidak cocok. */
	static void verifySha(String actual, String expected) throws IOException
	{
		String e = normalizeSha(expected);
		if (e.length() == 0)
			return;
		if (!e.equals(actual))
			throw new IOException("SHA-256 tidak cocok.\nDiharapkan: " + e + "\nDiterima:   " + actual);
	}

	/** Unduh https://..., verifikasi SHA-256 (opsional), lalu pasang. Panggil dari thread latar. */
	public static int downloadAndInstall(Context ctx, String url, String sha256, Progress pr) throws IOException
	{
		if (url == null || !url.trim().toLowerCase().startsWith("https://"))
			throw new IOException("Alamat harus diawali https://");

		File part = new File(ctx.getFilesDir(), "legacy_pack.zip.part");
		HttpURLConnection c = null;
		try
		{
			URL u = new URL(url.trim());
			// ikuti redirect secara manual (maks 5) dan hanya ke https
			for (int i = 0; ; i++)
			{
				c = (HttpURLConnection)u.openConnection();
				c.setInstanceFollowRedirects(false);
				c.setConnectTimeout(20000);
				c.setReadTimeout(30000);
				int code = c.getResponseCode();
				if (code >= 300 && code < 400)
				{
					String loc = c.getHeaderField("Location");
					c.disconnect();
					if (loc == null || i >= 5)
						throw new IOException("Redirect tidak valid");
					u = new URL(u, loc);
					if (!u.getProtocol().equals("https"))
						throw new IOException("Redirect ke alamat non-https ditolak");
					continue;
				}
				if (code != 200)
					throw new IOException("Server menjawab HTTP " + code);
				break;
			}

			if (pr != null)
				pr.onMessage("Mengunduh...");
			InputStream in = c.getInputStream();
			String actual;
			try
			{
				actual = copyHashed(in, part, MAX_TOTAL_BYTES, c.getContentLength(), pr);
			}
			finally
			{
				in.close();
			}

			if (pr != null)
				pr.onMessage("SHA-256: " + actual);
			verifySha(actual, sha256);

			if (pr != null)
				pr.onMessage("Mengekstrak...");
			return installFromFile(ctx, part, pr);
		}
		finally
		{
			if (c != null)
				c.disconnect();
			part.delete();
		}
	}
}
