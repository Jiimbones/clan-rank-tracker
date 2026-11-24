package com.clanranktracker.data;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/** How long a member stays at a rank before a review. Amount 0 means never. */
public class ReviewPeriod
{
	public enum Unit
	{
		DAYS("d", "day", "days", ChronoUnit.DAYS),
		MONTHS("mo", "month", "months", ChronoUnit.MONTHS),
		YEARS("y", "year", "years", ChronoUnit.YEARS);

		public final String suffix;
		public final String singular;
		public final String plural;
		final ChronoUnit chrono;

		Unit(String suffix, String singular, String plural, ChronoUnit chrono)
		{
			this.suffix = suffix;
			this.singular = singular;
			this.plural = plural;
			this.chrono = chrono;
		}

		@Override
		public String toString()
		{
			return Character.toUpperCase(plural.charAt(0)) + plural.substring(1);
		}
	}

	public int amount;
	public Unit unit = Unit.DAYS;

	public ReviewPeriod()
	{
	}

	public ReviewPeriod(int amount, Unit unit)
	{
		this.amount = Math.max(0, amount);
		this.unit = unit == null ? Unit.DAYS : unit;
	}

	public boolean isOff()
	{
		return amount <= 0;
	}

	/** Calendar-aware: 1 month from 31 Jan is 28/29 Feb. */
	public long dueAt(long start, ZoneId zone)
	{
		ZonedDateTime s = Instant.ofEpochMilli(start).atZone(zone);
		return s.plus(amount, unit.chrono).toInstant().toEpochMilli();
	}

	/** Whole units elapsed since start, e.g. 4 for "4 of 6 months". */
	public long elapsed(long start, long now, ZoneId zone)
	{
		if (now <= start)
		{
			return 0;
		}
		return unit.chrono.between(Instant.ofEpochMilli(start).atZone(zone), Instant.ofEpochMilli(now).atZone(zone));
	}

	/** "30d", "6mo", "1y". */
	public String shortLabel()
	{
		return amount + unit.suffix;
	}

	/** "30 days", "1 year". */
	public String longLabel()
	{
		return amount + " " + (amount == 1 ? unit.singular : unit.plural);
	}
}
