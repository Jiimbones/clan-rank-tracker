package com.clanranktracker.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything persisted for one clan. Serialised to JSON with Gson. */
public class ClanState
{
	/** 2: rank time counts from the clan join date; review periods replace day thresholds. */
	public static final int CURRENT_VERSION = 2;
	public static final int MAX_EVENTS = 5000;

	public int version = CURRENT_VERSION;
	public String clanName;
	public boolean baselineTaken;
	public long baselineAt;
	public long lastSnapshotAt;
	public long nextId = 1;

	/** Current members keyed by {@link Names#key}. */
	public Map<String, MemberRecord> members = new LinkedHashMap<>();
	/** Members who left, kept so a rejoin or a rename can recover their history. */
	public Map<String, MemberRecord> departed = new LinkedHashMap<>();
	/** Oldest first. */
	public List<RankEvent> events = new ArrayList<>();
	public List<PendingRename> pending = new ArrayList<>();
	/** Rank value to its title. */
	public Map<Integer, RankInfo> ranks = new LinkedHashMap<>();
	/** Rank value to review period. Amount 0 means never due. Missing means use the default. */
	public Map<Integer, ReviewPeriod> reviewPeriods = new LinkedHashMap<>();
	/** Version 1 only: per-rank thresholds in days. Moved into {@link #reviewPeriods} on load. */
	public Map<Integer, Integer> thresholds;

	public long newId()
	{
		return nextId++;
	}

	/** Gson leaves collections null when a field is missing from older files. */
	public ClanState repair()
	{
		if (members == null)
		{
			members = new LinkedHashMap<>();
		}
		if (departed == null)
		{
			departed = new LinkedHashMap<>();
		}
		if (events == null)
		{
			events = new ArrayList<>();
		}
		if (pending == null)
		{
			pending = new ArrayList<>();
		}
		if (ranks == null)
		{
			ranks = new LinkedHashMap<>();
		}
		if (reviewPeriods == null)
		{
			reviewPeriods = new LinkedHashMap<>();
		}
		if (thresholds != null)
		{
			for (Map.Entry<Integer, Integer> t : thresholds.entrySet())
			{
				reviewPeriods.putIfAbsent(t.getKey(), new ReviewPeriod(t.getValue() == null ? 0 : t.getValue(), ReviewPeriod.Unit.DAYS));
			}
			thresholds = null;
		}
		if (version < 2)
		{
			// Members only known from the first snapshot now count from their clan join date.
			for (MemberRecord m : members.values())
			{
				if (m.rankSinceEstimated && m.joinEpochDay != null && m.rankSince == baselineAt)
				{
					m.rankSince = Math.min(m.rankSince, MemberRecord.joinMillis(m.joinEpochDay));
				}
			}
			version = CURRENT_VERSION;
		}
		for (MemberRecord m : members.values())
		{
			if (m.previousNames == null)
			{
				m.previousNames = new ArrayList<>();
			}
		}
		for (MemberRecord m : departed.values())
		{
			if (m.previousNames == null)
			{
				m.previousNames = new ArrayList<>();
			}
		}
		long maxId = 0;
		for (RankEvent e : events)
		{
			maxId = Math.max(maxId, e.id);
		}
		for (PendingRename p : pending)
		{
			maxId = Math.max(maxId, p.id);
		}
		nextId = Math.max(nextId, maxId + 1);
		return this;
	}
}
