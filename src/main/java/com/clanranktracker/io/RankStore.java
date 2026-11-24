package com.clanranktracker.io;

import com.clanranktracker.data.ClanState;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/** One JSON file per clan in the plugin's data directory. Never call from the client thread. */
@Slf4j
public class RankStore
{
	private final Filepath dir;
	private final Gson gson;

	public RankStore(Filepath dir, Gson gson)
	{
		this.dir = dir;
		this.gson = gson;
	}

	public static String fileName(String clanName)
	{
		String safe = clanName == null ? "" : clanName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
		safe = safe.replaceAll("^_+|_+$", "");
		if (safe.isEmpty())
		{
			safe = "clan";
		}
		return safe + ".json";
	}

	public ClanState load(String clanName)
	{
		try
		{
			Filepath f = dir.joinSegment(fileName(clanName));
			if (!f.exists())
			{
				return fresh(clanName);
			}
			try (Reader r = f.openBufferedReader())
			{
				ClanState s = gson.fromJson(r, ClanState.class);
				if (s == null)
				{
					return fresh(clanName);
				}
				s.clanName = clanName;
				return s.repair();
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Couldn't read rank history for {}, starting fresh", clanName, e);
			backupCorrupt(clanName);
			return fresh(clanName);
		}
	}

	/** Writes to a temp file and moves it into place so a crash can't leave a half-written file. */
	public void save(String clanName, String json)
	{
		try
		{
			dir.createDirectories();
			Filepath target = dir.joinSegment(fileName(clanName));
			Filepath tmp = dir.joinSegment(fileName(clanName) + ".tmp");
			tmp.write(json);
			tmp.moveTo(target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			log.warn("Couldn't save rank history for {}", clanName, e);
		}
	}

	public String toJson(ClanState s)
	{
		return gson.toJson(s);
	}

	private void backupCorrupt(String clanName)
	{
		try
		{
			Filepath f = dir.joinSegment(fileName(clanName));
			if (f.exists())
			{
				f.moveTo(dir.joinSegment(fileName(clanName) + ".corrupt-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			log.debug("Couldn't back up unreadable file", e);
		}
	}

	private static ClanState fresh(String clanName)
	{
		ClanState s = new ClanState();
		s.clanName = clanName;
		return s;
	}
}
