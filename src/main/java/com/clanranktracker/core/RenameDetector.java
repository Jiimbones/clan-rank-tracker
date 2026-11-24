package com.clanranktracker.core;

import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.Names;
import com.clanranktracker.data.RenameReason;
import com.clanranktracker.data.RosterEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pairs members who vanished with members who appeared in the same update.
 *
 * <p>A rename keeps the member's clan entry, so their join date carries over. Pairs are
 * only considered when both join dates match, or at least one side has no join date.
 * Strong signals link automatically, weaker ones are returned as possible matches for a
 * leader to confirm.</p>
 */
public final class RenameDetector
{
	private RenameDetector()
	{
	}

	public static final class Match
	{
		public final MemberRecord old;
		public final RosterEntry neu;
		public final RenameReason reason;
		public final String why;

		Match(MemberRecord old, RosterEntry neu, RenameReason reason, String why)
		{
			this.old = old;
			this.neu = neu;
			this.reason = reason;
			this.why = why;
		}
	}

	public static final class Result
	{
		public final List<Match> auto = new ArrayList<>();
		public final List<Match> possible = new ArrayList<>();
		public final List<MemberRecord> leaves = new ArrayList<>();
		public final List<RosterEntry> joins = new ArrayList<>();
	}

	/**
	 * @param friendPrevNames new-name key to previous-name key, from the friends list
	 */
	public static Result detect(List<MemberRecord> leaves, List<RosterEntry> joins, Map<String, String> friendPrevNames)
	{
		Result r = new Result();
		List<MemberRecord> L = new ArrayList<>(leaves);
		List<RosterEntry> J = new ArrayList<>(joins);

		// 1. Friends list knows the previous name outright.
		if (friendPrevNames != null && !friendPrevNames.isEmpty())
		{
			for (RosterEntry j : new ArrayList<>(J))
			{
				String prev = friendPrevNames.get(j.key());
				if (prev == null)
				{
					continue;
				}
				MemberRecord l = findByKey(L, prev);
				if (l != null)
				{
					r.auto.add(new Match(l, j, RenameReason.FRIENDS_LIST, null));
					L.remove(l);
					J.remove(j);
				}
			}
		}

		// 2. Same join date.
		Map<Long, List<MemberRecord>> leavesByDate = new LinkedHashMap<>();
		for (MemberRecord l : L)
		{
			if (l.joinEpochDay != null)
			{
				leavesByDate.computeIfAbsent(l.joinEpochDay, k -> new ArrayList<>()).add(l);
			}
		}
		for (Map.Entry<Long, List<MemberRecord>> g : leavesByDate.entrySet())
		{
			List<MemberRecord> gl = g.getValue();
			List<RosterEntry> gj = new ArrayList<>();
			for (RosterEntry j : J)
			{
				if (g.getKey().equals(j.joinEpochDay))
				{
					gj.add(j);
				}
			}
			if (gj.isEmpty())
			{
				continue;
			}
			if (gl.size() == 1 && gj.size() == 1)
			{
				MemberRecord l = gl.get(0);
				RosterEntry j = gj.get(0);
				if (l.rank == j.rank)
				{
					r.auto.add(new Match(l, j, RenameReason.JOIN_DATE, null));
				}
				else
				{
					r.possible.add(new Match(l, j, RenameReason.POSSIBLE, "Same join date, different rank"));
				}
				L.remove(l);
				J.remove(j);
				continue;
			}
			// Several people share the date: only pair where the rank makes it unambiguous.
			for (MemberRecord l : new ArrayList<>(gl))
			{
				List<MemberRecord> sameRankL = filterRank(gl, l.rank);
				List<RosterEntry> sameRankJ = filterRankJ(gj, l.rank);
				if (sameRankL.size() == 1 && sameRankJ.size() == 1 && J.contains(sameRankJ.get(0)) && L.contains(l))
				{
					RosterEntry j = sameRankJ.get(0);
					r.possible.add(new Match(l, j, RenameReason.POSSIBLE, "Same join date and rank"));
					L.remove(l);
					J.remove(j);
				}
			}
		}

		// 3. No join date to go on: fall back to rank.
		List<MemberRecord> undatedL = new ArrayList<>();
		for (MemberRecord l : L)
		{
			if (hasCompatibleJoin(l, J))
			{
				undatedL.add(l);
			}
		}
		if (L.size() == 1 && J.size() == 1 && compatible(L.get(0), J.get(0)) && L.get(0).rank == J.get(0).rank)
		{
			r.auto.add(new Match(L.get(0), J.get(0), RenameReason.SOLE_SWAP, null));
			L.clear();
			J.clear();
		}
		else
		{
			for (MemberRecord l : undatedL)
			{
				List<MemberRecord> sameRankL = new ArrayList<>();
				for (MemberRecord x : L)
				{
					if (x.rank == l.rank && hasCompatibleJoin(x, J))
					{
						sameRankL.add(x);
					}
				}
				List<RosterEntry> sameRankJ = new ArrayList<>();
				for (RosterEntry j : J)
				{
					if (j.rank == l.rank && compatible(l, j))
					{
						sameRankJ.add(j);
					}
				}
				if (sameRankL.size() == 1 && sameRankJ.size() == 1)
				{
					RosterEntry j = sameRankJ.get(0);
					r.possible.add(new Match(l, j, RenameReason.POSSIBLE, "Only leave and join at this rank"));
					L.remove(l);
					J.remove(j);
				}
			}
		}

		r.leaves.addAll(L);
		r.joins.addAll(J);
		return r;
	}

	private static boolean compatible(MemberRecord l, RosterEntry j)
	{
		return l.joinEpochDay == null || j.joinEpochDay == null || Objects.equals(l.joinEpochDay, j.joinEpochDay);
	}

	private static boolean hasCompatibleJoin(MemberRecord l, List<RosterEntry> joins)
	{
		for (RosterEntry j : joins)
		{
			if (compatible(l, j))
			{
				return true;
			}
		}
		return false;
	}

	private static MemberRecord findByKey(List<MemberRecord> list, String key)
	{
		for (MemberRecord m : list)
		{
			if (Names.key(m.name).equals(key))
			{
				return m;
			}
		}
		return null;
	}

	private static List<MemberRecord> filterRank(List<MemberRecord> list, int rank)
	{
		List<MemberRecord> out = new ArrayList<>();
		for (MemberRecord m : list)
		{
			if (m.rank == rank)
			{
				out.add(m);
			}
		}
		return out;
	}

	private static List<RosterEntry> filterRankJ(List<RosterEntry> list, int rank)
	{
		List<RosterEntry> out = new ArrayList<>();
		for (RosterEntry m : list)
		{
			if (m.rank == rank)
			{
				out.add(m);
			}
		}
		return out;
	}
}
