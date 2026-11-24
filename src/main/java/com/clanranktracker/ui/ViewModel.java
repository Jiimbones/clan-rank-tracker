package com.clanranktracker.ui;

import com.clanranktracker.core.ReviewMath;
import com.clanranktracker.data.ClanState;
import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.RankInfo;
import com.clanranktracker.data.ReviewPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** An immutable copy of a clan's state plus the settings needed to draw it. */
public final class ViewModel
{
	public enum Status
	{
		LOGGED_OUT,
		NO_CLAN,
		LOADING,
		READY
	}

	public final Status status;
	public final ClanState state;
	public final ReviewPeriod defaultPeriod;
	public final boolean staffNeverDue;
	public final int soonPercent;
	public final long now;
	public final ZoneId zone;

	private ViewModel(Status status, ClanState state, ReviewPeriod defaultPeriod, boolean staffNeverDue, int soonPercent, long now, ZoneId zone)
	{
		this.status = status;
		this.state = state;
		this.defaultPeriod = defaultPeriod;
		this.staffNeverDue = staffNeverDue;
		this.soonPercent = soonPercent;
		this.now = now;
		this.zone = zone;
	}

	public static ViewModel of(Status status)
	{
		return new ViewModel(status, null, new ReviewPeriod(30, ReviewPeriod.Unit.DAYS), true, 75, System.currentTimeMillis(), ZoneId.systemDefault());
	}

	public static ViewModel ready(ClanState state, ReviewPeriod defaultPeriod, boolean staffNeverDue, int soonPercent, long now, ZoneId zone)
	{
		return new ViewModel(Status.READY, state, defaultPeriod, staffNeverDue, soonPercent, now, zone);
	}

	/** Never null; amount 0 means never due. */
	public ReviewPeriod period(int rank)
	{
		if (!topRankKnown)
		{
			topRank = ReviewMath.topReviewedRank(state, staffNeverDue);
			topRankKnown = true;
		}
		return ReviewMath.period(state, rank, defaultPeriod, staffNeverDue, topRank);
	}

	private Integer topRank;
	private boolean topRankKnown;

	/** True when this rank is the top of the ladder and has no review because of that default. */
	public boolean isTopByDefault(int rank)
	{
		period(rank);
		return topRank != null && topRank == rank && !state.reviewPeriods.containsKey(rank);
	}

	private List<Integer> ladder;

	/** Reviewed ranks that have members, lowest first: the promotion path. */
	public List<Integer> ladder()
	{
		if (ladder == null)
		{
			java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
			for (MemberRecord m : state.members.values())
			{
				if (!period(m.rank).isOff())
				{
					set.add(m.rank);
				}
			}
			ladder = new ArrayList<>(set);
		}
		return ladder;
	}

	/** True when rank time is estimated from the join date and the ladder, not observed. */
	public boolean isEstimated(MemberRecord m)
	{
		return m.countsFromJoin();
	}

	/** When the member reached their rank: observed, set by hand, or estimated from the ladder. */
	public long rankSince(MemberRecord m)
	{
		if (!m.countsFromJoin())
		{
			return m.rankSince;
		}
		return ReviewMath.estimatedRankSince(m.rankSince, m.rank, ladder(), this::period, now, zone);
	}

	/** Where the review clock starts: the later of reaching the rank and the last review. */
	public long clockStart(MemberRecord m)
	{
		long since = rankSince(m);
		return m.lastReviewed != null && m.lastReviewed > since ? m.lastReviewed : since;
	}

	public int days(MemberRecord m)
	{
		return ReviewMath.days(clockStart(m), now);
	}

	/** Demoted since their last review, at a rank that's reviewed: due to be promoted back. */
	public boolean isDemoted(MemberRecord m)
	{
		return m.awaitingRePromotion() && !period(m.rank).isOff();
	}

	public double fraction(MemberRecord m)
	{
		double f = ReviewMath.fraction(clockStart(m), period(m.rank), now, zone);
		return isDemoted(m) ? Math.max(1, f) : f;
	}

	public boolean isDue(MemberRecord m)
	{
		return isDemoted(m) || ReviewMath.isDue(clockStart(m), period(m.rank), now, zone);
	}

	public boolean isSoon(MemberRecord m)
	{
		ReviewPeriod p = period(m.rank);
		return !p.isOff() && !isDue(m) && fraction(m) * 100 >= soonPercent;
	}

	/** Badly overdue: half a period or more past due. Demotions are shown as due, not late. */
	public boolean isLate(MemberRecord m)
	{
		return isDue(m) && !isDemoted(m) && fraction(m) >= 1.5;
	}

	/** "94 / 30d", "4 / 6mo", "2 / 1y", or "Demoted 3d" after a demotion. */
	public String progress(MemberRecord m)
	{
		if (isDemoted(m))
		{
			return "Demoted " + ago(m.demotedAt);
		}
		ReviewPeriod p = period(m.rank);
		long elapsed = p.elapsed(clockStart(m), now, zone);
		return elapsed + (m.countsFromTracking() ? "+" : "") + " / " + p.shortLabel();
	}

	/** Days until due (negative when overdue). */
	public int daysUntilDue(MemberRecord m)
	{
		long due = period(m.rank).dueAt(clockStart(m), zone);
		return (int) Math.floorDiv(due - now, ReviewMath.DAY_MS);
	}

	public String title(Integer rank)
	{
		if (rank == null)
		{
			return "";
		}
		RankInfo ri = state.ranks.get(rank);
		if (ri != null && ri.title != null && !ri.title.isEmpty())
		{
			return ri.title;
		}
		return "Rank " + rank;
	}


	/** Due members, most overdue first (by fraction of their period). */
	public List<MemberRecord> due()
	{
		List<MemberRecord> out = new ArrayList<>();
		for (MemberRecord m : state.members.values())
		{
			if (isDue(m))
			{
				out.add(m);
			}
		}
		out.sort(Comparator.comparingDouble((MemberRecord m) -> -fraction(m)).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
		return out;
	}

	public List<MemberRecord> soon()
	{
		List<MemberRecord> out = new ArrayList<>();
		for (MemberRecord m : state.members.values())
		{
			if (isSoon(m))
			{
				out.add(m);
			}
		}
		out.sort(Comparator.comparingDouble((MemberRecord m) -> -fraction(m)).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
		return out;
	}
	/** Members grouped by rank, highest rank first. */
	public Map<Integer, List<MemberRecord>> byRank()
	{
		Map<Integer, List<MemberRecord>> map = new TreeMap<>(Comparator.reverseOrder());
		for (MemberRecord m : state.members.values())
		{
			map.computeIfAbsent(m.rank, k -> new ArrayList<>()).add(m);
		}
		for (List<MemberRecord> l : map.values())
		{
			l.sort(Comparator.comparing((MemberRecord m) -> !isDue(m)).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
		}
		return map;
	}

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	public String date(long epochMillis)
	{
		return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
	}

	public static String date(LocalDate d)
	{
		return DATE.format(d);
	}

	/** "today", "1d", "12d", "3mo", "2y". */
	public String ago(long epochMillis)
	{
		int d = ReviewMath.days(epochMillis, now);
		if (d == 0)
		{
			return "today";
		}
		if (d < 60)
		{
			return d + "d";
		}
		if (d < 730)
		{
			return (d / 30) + "mo";
		}
		return (d / 365) + "y";
	}
}
