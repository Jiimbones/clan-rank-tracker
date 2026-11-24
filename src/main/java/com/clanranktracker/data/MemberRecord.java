package com.clanranktracker.data;

import java.util.ArrayList;
import java.util.List;

/** What the tracker knows about one current (or departed) clan member. Times are epoch millis. */
public class MemberRecord
{
	public String name;
	public int rank;
	/** Clan join date as reported by the game, as epoch day. Null if the game didn't provide one. */
	public Long joinEpochDay;
	/** When the member reached their current rank, or when tracking began if unknown. */
	public long rankSince;
	/** True when rankSince wasn't observed: it's the clan join date, or when tracking began if that's unknown. */
	public boolean rankSinceEstimated;
	/** Last time a leader marked this member as reviewed (resets the review clock). */
	public Long lastReviewed;
	public long firstSeen;
	public List<String> previousNames = new ArrayList<>();
	/** When they were last demoted. Cleared by a promotion; a review after this resolves it. */
	public Long demotedAt;

	public MemberRecord()
	{
	}

	public MemberRecord(String name, int rank, Long joinEpochDay, long now, boolean estimated)
	{
		this.name = name;
		this.rank = rank;
		this.joinEpochDay = joinEpochDay;
		this.rankSince = now;
		this.rankSinceEstimated = estimated;
		this.firstSeen = now;
	}

	public MemberRecord copy()
	{
		MemberRecord c = new MemberRecord();
		c.name = name;
		c.rank = rank;
		c.joinEpochDay = joinEpochDay;
		c.rankSince = rankSince;
		c.rankSinceEstimated = rankSinceEstimated;
		c.lastReviewed = lastReviewed;
		c.firstSeen = firstSeen;
		c.previousNames = new ArrayList<>(previousNames == null ? List.of() : previousNames);
		c.demotedAt = demotedAt;
		return c;
	}

	/** Demoted and not reviewed since: due for promotion again. */
	public boolean awaitingRePromotion()
	{
		return demotedAt != null && (lastReviewed == null || lastReviewed < demotedAt);
	}

	/** Midnight UTC on the given epoch day. */
	public static long joinMillis(long epochDay)
	{
		return epochDay * 24L * 60 * 60 * 1000;
	}

	/** True when time in rank is a lower bound from when tracking began (no join date known). */
	public boolean countsFromTracking()
	{
		return rankSinceEstimated && joinEpochDay == null;
	}

	/** True when time in rank is taken from the clan join date, not an observed rank change. */
	public boolean countsFromJoin()
	{
		return rankSinceEstimated && joinEpochDay != null;
	}

	/** The point the review clock counts from: the later of reaching the rank and the last review. */
	public long reviewClockStart()
	{
		return lastReviewed != null && lastReviewed > rankSince ? lastReviewed : rankSince;
	}
}
