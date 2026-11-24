package com.clanranktracker.core;

import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.RosterEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure comparison of what we knew against what the game reports now. */
public final class SnapshotDiffer
{
	private SnapshotDiffer()
	{
	}

	public static final class RankChange
	{
		public final MemberRecord record;
		public final RosterEntry entry;

		RankChange(MemberRecord record, RosterEntry entry)
		{
			this.record = record;
			this.entry = entry;
		}
	}

	public static final class Diff
	{
		public final List<MemberRecord> leaves = new ArrayList<>();
		public final List<RosterEntry> joins = new ArrayList<>();
		public final List<RankChange> rankChanges = new ArrayList<>();
		/** Members still present whose join date we didn't have before. */
		public final List<RankChange> joinDateFills = new ArrayList<>();

		public boolean isEmpty()
		{
			return leaves.isEmpty() && joins.isEmpty() && rankChanges.isEmpty() && joinDateFills.isEmpty();
		}
	}

	/** Drops duplicate names, keeping the first, so a malformed roster can't create phantom events. */
	public static Map<String, RosterEntry> index(List<RosterEntry> roster)
	{
		Map<String, RosterEntry> byKey = new LinkedHashMap<>();
		for (RosterEntry e : roster)
		{
			if (e == null || e.key().isEmpty())
			{
				continue;
			}
			byKey.putIfAbsent(e.key(), e);
		}
		return byKey;
	}

	public static Diff diff(Map<String, MemberRecord> known, List<RosterEntry> roster)
	{
		Map<String, RosterEntry> now = index(roster);
		Diff d = new Diff();

		for (Map.Entry<String, MemberRecord> k : known.entrySet())
		{
			RosterEntry e = now.get(k.getKey());
			if (e == null)
			{
				d.leaves.add(k.getValue());
				continue;
			}
			if (e.rank != k.getValue().rank)
			{
				d.rankChanges.add(new RankChange(k.getValue(), e));
			}
			if (k.getValue().joinEpochDay == null && e.joinEpochDay != null)
			{
				d.joinDateFills.add(new RankChange(k.getValue(), e));
			}
		}

		for (Map.Entry<String, RosterEntry> e : now.entrySet())
		{
			if (!known.containsKey(e.getKey()))
			{
				d.joins.add(e.getValue());
			}
		}
		return d;
	}
}
