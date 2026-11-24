package com.clanranktracker.core;

import com.clanranktracker.data.ClanState;
import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.ReviewPeriod;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;

public final class ReviewMath
{
	public static final long DAY_MS = 24L * 60 * 60 * 1000;
	/** RuneLite's ClanRank.ADMINISTRATOR value. Ranks at or above are staff. */
	public static final int ADMIN_RANK = 100;

	private ReviewMath()
	{
	}

	public static int days(long from, long to)
	{
		return (int) Math.max(0, (to - from) / DAY_MS);
	}

	/** Days on the review clock for a member. */
	public static int daysOnClock(MemberRecord m, long now)
	{
		return days(m.reviewClockStart(), now);
	}

	/**
	 * The review period for a rank. Never null; amount 0 means the rank is never due.
	 * A rank set by hand always wins. Otherwise staff ranks (if skipped) and the clan's top
	 * reviewed rank default to no review, since there's no rank to promote them to.
	 *
	 * @param topRank from {@link #topReviewedRank}, or null if the clan has no members
	 */
	public static ReviewPeriod period(ClanState s, int rank, ReviewPeriod fallback, boolean staffNeverDue, Integer topRank)
	{
		ReviewPeriod p = s.reviewPeriods.get(rank);
		if (p != null)
		{
			return p;
		}
		if (isStaff(rank, staffNeverDue) || (topRank != null && rank == topRank))
		{
			return new ReviewPeriod(0, fallback.unit);
		}
		return fallback;
	}

	/** The highest rank held by a member, ignoring skipped staff ranks. Null if there's none. */
	public static Integer topReviewedRank(ClanState s, boolean staffNeverDue)
	{
		Integer top = null;
		for (MemberRecord m : s.members.values())
		{
			if (!isStaff(m.rank, staffNeverDue) && (top == null || m.rank > top))
			{
				top = m.rank;
			}
		}
		return top;
	}

	private static boolean isStaff(int rank, boolean staffNeverDue)
	{
		return staffNeverDue && rank >= ADMIN_RANK;
	}

	public static boolean isDue(MemberRecord m, ReviewPeriod p, long now, ZoneId zone)
	{
		return isDue(m.reviewClockStart(), p, now, zone);
	}

	public static boolean isDue(long start, ReviewPeriod p, long now, ZoneId zone)
	{
		return !p.isOff() && now >= p.dueAt(start, zone);
	}

	/** How far through the review period, 0 to well past 1. */
	public static double fraction(MemberRecord m, ReviewPeriod p, long now, ZoneId zone)
	{
		return fraction(m.reviewClockStart(), p, now, zone);
	}

	public static double fraction(long start, ReviewPeriod p, long now, ZoneId zone)
	{
		if (p.isOff())
		{
			return 0;
		}
		long span = Math.max(1, p.dueAt(start, zone) - start);
		return Math.max(0, (double) (now - start) / span);
	}

	/**
	 * When a member probably reached their rank, if no rank change was seen: their join date
	 * plus one review period for each rank below theirs on the ladder. Assumes one promotion
	 * per review period. Never later than now.
	 *
	 * @param ladder rank values that are reviewed, lowest first
	 */
	public static long estimatedRankSince(long joined, int rank, List<Integer> ladder, Function<Integer, ReviewPeriod> periodOf, long now, ZoneId zone)
	{
		long t = joined;
		for (int r : ladder)
		{
			if (r >= rank)
			{
				break;
			}
			t = periodOf.apply(r).dueAt(t, zone);
		}
		return Math.min(t, now);
	}
}
