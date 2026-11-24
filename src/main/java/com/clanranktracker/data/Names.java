package com.clanranktracker.data;

import java.util.Locale;

/**
 * Jagex display names treat spaces, underscores and non-breaking spaces as the same
 * character and are case-insensitive. Every lookup in the tracker goes through {@link #key}.
 */
public final class Names
{
	private Names()
	{
	}

	public static String key(String name)
	{
		if (name == null)
		{
			return "";
		}
		return clean(name).toLowerCase(Locale.ROOT);
	}

	/** Display form: same characters the game shows, with odd whitespace normalised. */
	public static String clean(String name)
	{
		if (name == null)
		{
			return "";
		}
		return name.replace(' ', ' ').replace('_', ' ').trim();
	}
}
