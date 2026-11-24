package com.clanranktracker.io;

import com.clanranktracker.data.Names;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Looks up a player's approved name changes on Wise Old Man. Opt-in only.
 * Sends nothing but the username being checked.
 */
@Slf4j
public class WomNameClient
{
	private static final HttpUrl BASE = HttpUrl.get("https://api.wiseoldman.net/v2/players/");
	/** WOM allows 20 requests a minute without an API key. Stay well under it. */
	private static final int MAX_PER_MINUTE = 10;
	private static final long CACHE_MS = 60 * 60 * 1000L;

	public enum Verdict
	{
		CONFIRMED,
		NOT_FOUND,
		ERROR
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
	private final Deque<Long> recent = new ArrayDeque<>();

	private static final class CacheEntry
	{
		final long at;
		final List<String[]> changes;

		CacheEntry(long at, List<String[]> changes)
		{
			this.at = at;
			this.changes = changes;
		}
	}

	public WomNameClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/** Calls back on an OkHttp thread. */
	public void check(String oldName, String newName, Consumer<Verdict> callback)
	{
		String key = Names.key(newName);
		CacheEntry hit = cache.get(key);
		if (hit != null && System.currentTimeMillis() - hit.at < CACHE_MS)
		{
			callback.accept(matches(hit.changes, oldName, newName) ? Verdict.CONFIRMED : Verdict.NOT_FOUND);
			return;
		}
		if (!takeSlot())
		{
			callback.accept(Verdict.ERROR);
			return;
		}

		HttpUrl url = BASE.newBuilder()
			.addPathSegment(Names.clean(newName))
			.addPathSegment("names")
			.build();
		Request req = new Request.Builder()
			.url(url)
			.header("User-Agent", "clan-rank-tracker-runelite-plugin")
			.build();

		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("WOM lookup failed for {}", newName, e);
				callback.accept(Verdict.ERROR);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (response.code() == 404)
					{
						cache.put(key, new CacheEntry(System.currentTimeMillis(), List.of()));
						callback.accept(Verdict.NOT_FOUND);
						return;
					}
					if (!response.isSuccessful() || body == null)
					{
						callback.accept(Verdict.ERROR);
						return;
					}
					List<String[]> changes = parse(gson, body.string());
					cache.put(key, new CacheEntry(System.currentTimeMillis(), changes));
					callback.accept(matches(changes, oldName, newName) ? Verdict.CONFIRMED : Verdict.NOT_FOUND);
				}
				catch (IOException | JsonParseException | IllegalStateException e)
				{
					log.debug("Bad WOM response for {}", newName, e);
					callback.accept(Verdict.ERROR);
				}
			}
		});
	}

	/** @return approved changes as {oldName, newName} pairs */
	static List<String[]> parse(Gson gson, String json)
	{
		List<String[]> out = new ArrayList<>();
		JsonElement root = gson.fromJson(json, JsonElement.class);
		if (root == null || !root.isJsonArray())
		{
			return out;
		}
		JsonArray arr = root.getAsJsonArray();
		for (JsonElement el : arr)
		{
			if (!el.isJsonObject())
			{
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			String status = str(o, "status");
			if (status != null && !"approved".equalsIgnoreCase(status))
			{
				continue;
			}
			String oldN = str(o, "oldName");
			String newN = str(o, "newName");
			if (oldN != null && newN != null)
			{
				out.add(new String[]{oldN, newN});
			}
		}
		return out;
	}

	/**
	 * True if the history contains a step from oldName to newName, either directly or
	 * through intermediate names (A to B to C still links A and C).
	 */
	static boolean matches(List<String[]> changes, String oldName, String newName)
	{
		String target = Names.key(newName);
		String cur = Names.key(oldName);
		for (int guard = 0; guard < changes.size() + 1; guard++)
		{
			String next = null;
			for (String[] c : changes)
			{
				if (Names.key(c[0]).equals(cur))
				{
					next = Names.key(c[1]);
					break;
				}
			}
			if (next == null)
			{
				return false;
			}
			if (next.equals(target))
			{
				return true;
			}
			cur = next;
		}
		return false;
	}

	private synchronized boolean takeSlot()
	{
		long now = System.currentTimeMillis();
		while (!recent.isEmpty() && now - recent.peekFirst() > 60_000)
		{
			recent.pollFirst();
		}
		if (recent.size() >= MAX_PER_MINUTE)
		{
			return false;
		}
		recent.addLast(now);
		return true;
	}

	private static String str(JsonObject o, String k)
	{
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}
}
