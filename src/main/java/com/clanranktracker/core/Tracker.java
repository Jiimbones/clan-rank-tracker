package com.clanranktracker.core;

import com.clanranktracker.data.ClanState;
import com.clanranktracker.data.EventType;
import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.Names;
import com.clanranktracker.data.PendingRename;
import com.clanranktracker.data.RankEvent;
import com.clanranktracker.data.RankInfo;
import com.clanranktracker.data.RenameReason;
import com.clanranktracker.data.ReviewPeriod;
import com.clanranktracker.data.RosterEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Owns one clan's state and applies roster snapshots to it. All access is synchronised,
 * so the client thread, the UI thread and HTTP callbacks can share one instance.
 */
public final class Tracker
{
	/** If the last snapshot is older than this, changes may have happened while we weren't watching. */
	public static final long APPROX_GAP_MS = 15 * 60 * 1000L;

	private final ClanState state;

	public Tracker(ClanState state)
	{
		this.state = state.repair();
	}

	public static final class ApplyResult
	{
		public boolean baseline;
		public boolean changed;
		public final List<PendingRename> newPending = new ArrayList<>();
	}

	public synchronized <T> T read(Function<ClanState, T> reader)
	{
		return reader.apply(state);
	}

	/** Marks that we're watching the roster right now, without persisting. */
	public synchronized void touch(long now)
	{
		if (state.baselineTaken)
		{
			state.lastSnapshotAt = now;
		}
	}

	public synchronized ApplyResult apply(List<RosterEntry> roster, Collection<RankInfo> ranks, Map<String, String> friendPrevNames, long now)
	{
		ApplyResult result = new ApplyResult();
		if (roster == null || roster.isEmpty())
		{
			return result;
		}
		if (ranks != null)
		{
			for (RankInfo ri : ranks)
			{
				state.ranks.put(ri.rank, ri);
			}
		}

		if (!state.baselineTaken)
		{
			for (RosterEntry e : SnapshotDiffer.index(roster).values())
			{
				MemberRecord rec = new MemberRecord(e.name, e.rank, e.joinEpochDay, now, true);
				if (e.joinEpochDay != null)
				{
					// No rank change seen yet: count from the day they joined the clan.
					rec.rankSince = Math.min(now, MemberRecord.joinMillis(e.joinEpochDay));
				}
				state.members.put(e.key(), rec);
			}
			state.baselineTaken = true;
			state.baselineAt = now;
			state.lastSnapshotAt = now;
			result.baseline = true;
			result.changed = true;
			return result;
		}

		boolean approximate = now - state.lastSnapshotAt > APPROX_GAP_MS;
		SnapshotDiffer.Diff d = SnapshotDiffer.diff(state.members, roster);
		state.lastSnapshotAt = now;
		if (d.isEmpty())
		{
			return result;
		}
		result.changed = true;

		for (SnapshotDiffer.RankChange f : d.joinDateFills)
		{
			f.record.joinEpochDay = f.entry.joinEpochDay;
		}

		for (SnapshotDiffer.RankChange c : d.rankChanges)
		{
			addRankChange(c.record, c.record.rank, c.entry.rank, now, approximate);
			setRank(c.record, c.entry.rank, now);
		}

		RenameDetector.Result renames = RenameDetector.detect(d.leaves, d.joins, friendPrevNames);
		for (RenameDetector.Match m : renames.auto)
		{
			linkRename(m.old, m.neu, m.reason, now, approximate);
		}
		for (RenameDetector.Match m : renames.possible)
		{
			PendingRename p = new PendingRename(state.newId(), m.old.name, m.neu.name, m.old.rank, m.neu.rank, now, m.why);
			state.pending.add(p);
			leave(m.old, now, approximate, p.id);
			join(m.neu, now, approximate, p.id);
			result.newPending.add(p);
		}
		for (MemberRecord l : renames.leaves)
		{
			leave(l, now, approximate, null);
		}
		for (RosterEntry j : renames.joins)
		{
			join(j, now, approximate, null);
		}
		trim();
		return result;
	}

	private void leave(MemberRecord old, long now, boolean approximate, Long pendingId)
	{
		String key = Names.key(old.name);
		state.members.remove(key);
		state.departed.put(key, old);
		RankEvent e = new RankEvent(state.newId(), EventType.LEAVE, old.name, now);
		e.fromRank = old.rank;
		e.approximate = approximate;
		e.pendingId = pendingId;
		state.events.add(e);
	}

	private void join(RosterEntry entry, long now, boolean approximate, Long pendingId)
	{
		MemberRecord rec = state.departed.remove(entry.key());
		if (rec != null)
		{
			rec.name = entry.name;
			rec.rank = entry.rank;
			rec.rankSince = now;
			rec.rankSinceEstimated = false;
			rec.lastReviewed = null;
			if (entry.joinEpochDay != null)
			{
				rec.joinEpochDay = entry.joinEpochDay;
			}
		}
		else
		{
			rec = new MemberRecord(entry.name, entry.rank, entry.joinEpochDay, now, false);
		}
		state.members.put(entry.key(), rec);
		RankEvent e = new RankEvent(state.newId(), EventType.JOIN, entry.name, now);
		e.toRank = entry.rank;
		e.approximate = approximate;
		e.pendingId = pendingId;
		state.events.add(e);
	}

	private void addRankChange(MemberRecord rec, int from, int to, long at, boolean approximate)
	{
		RankEvent e = new RankEvent(state.newId(), to > from ? EventType.PROMOTE : EventType.DEMOTE, rec.name, at);
		e.fromRank = from;
		e.toRank = to;
		e.approximate = approximate;
		insertChronologically(e);
	}

	private void linkRename(MemberRecord old, RosterEntry entry, RenameReason reason, long now, boolean approximate)
	{
		state.members.remove(Names.key(old.name));
		MemberRecord rec = old.copy();
		rec.name = entry.name;
		addPrevious(rec, old.name);
		if (entry.joinEpochDay != null)
		{
			rec.joinEpochDay = entry.joinEpochDay;
		}

		RankEvent e = new RankEvent(state.newId(), EventType.RENAME, entry.name, now);
		e.oldName = old.name;
		e.fromRank = old.rank;
		e.toRank = entry.rank;
		e.reason = reason;
		e.approximate = approximate;
		e.undoOld = old.copy();
		state.events.add(e);

		if (entry.rank != old.rank)
		{
			addRankChange(rec, old.rank, entry.rank, now, approximate);
			setRank(rec, entry.rank, now);
		}
		state.members.put(entry.key(), rec);
	}

	/** Moves a member to a new rank. A demotion flags them as due for promotion straight away. */
	private static void setRank(MemberRecord rec, int to, long at)
	{
		rec.demotedAt = to < rec.rank ? Long.valueOf(at) : null;
		rec.rank = to;
		rec.rankSince = at;
		rec.rankSinceEstimated = false;
	}

	public synchronized boolean confirmRename(long pendingId, RenameReason reason)
	{
		PendingRename p = removePending(pendingId);
		if (p == null)
		{
			return false;
		}
		String oldKey = Names.key(p.oldName);
		String newKey = Names.key(p.newName);
		MemberRecord oldRec = state.departed.get(oldKey);
		MemberRecord newRec = state.members.get(newKey);
		if (oldRec == null || newRec == null)
		{
			clearPendingMarks(p.id);
			return false;
		}
		state.departed.remove(oldKey);

		MemberRecord merged = oldRec.copy();
		merged.name = newRec.name;
		addPrevious(merged, oldRec.name);
		if (newRec.joinEpochDay != null)
		{
			merged.joinEpochDay = newRec.joinEpochDay;
		}
		if (newRec.rank != oldRec.rank)
		{
			setRank(merged, newRec.rank, newRec.rankSince);
		}
		state.members.put(newKey, merged);

		boolean approximate = false;
		for (Iterator<RankEvent> it = state.events.iterator(); it.hasNext(); )
		{
			RankEvent e = it.next();
			if (e.pendingId != null && e.pendingId == p.id)
			{
				approximate |= e.approximate;
				it.remove();
			}
		}
		RankEvent e = new RankEvent(state.newId(), EventType.RENAME, newRec.name, p.at);
		e.oldName = oldRec.name;
		e.fromRank = oldRec.rank;
		e.toRank = newRec.rank;
		e.reason = reason;
		e.approximate = approximate;
		e.undoOld = oldRec.copy();
		e.undoNew = newRec.copy();
		insertChronologically(e);
		if (newRec.rank != oldRec.rank)
		{
			addRankChange(merged, oldRec.rank, newRec.rank, p.at, approximate);
		}
		return true;
	}

	public synchronized boolean rejectRename(long pendingId)
	{
		PendingRename p = removePending(pendingId);
		if (p == null)
		{
			return false;
		}
		clearPendingMarks(p.id);
		return true;
	}

	/** Splits a rename back into a leave and a join. */
	public synchronized boolean undoRename(long eventId)
	{
		RankEvent ev = null;
		for (RankEvent e : state.events)
		{
			if (e.id == eventId && e.type == EventType.RENAME)
			{
				ev = e;
				break;
			}
		}
		if (ev == null || ev.undoOld == null)
		{
			return false;
		}
		String newKey = Names.key(ev.name);
		MemberRecord current = state.members.get(newKey);
		if (current == null)
		{
			return false;
		}
		MemberRecord restoredNew;
		if (ev.undoNew != null)
		{
			restoredNew = ev.undoNew.copy();
			restoredNew.rank = current.rank;
		}
		else
		{
			restoredNew = new MemberRecord(current.name, current.rank, current.joinEpochDay, ev.at, false);
		}
		state.members.put(newKey, restoredNew);
		state.departed.put(Names.key(ev.oldName), ev.undoOld.copy());

		int idx = state.events.indexOf(ev);
		state.events.remove(idx);
		// Drop the rank change that was logged against the merged member at the same moment.
		for (Iterator<RankEvent> it = state.events.iterator(); it.hasNext(); )
		{
			RankEvent e = it.next();
			if ((e.type == EventType.PROMOTE || e.type == EventType.DEMOTE) && e.at == ev.at
				&& Names.key(e.name).equals(newKey) && e.fromRank != null && e.fromRank.equals(ev.fromRank))
			{
				it.remove();
			}
		}
		RankEvent leave = new RankEvent(state.newId(), EventType.LEAVE, ev.oldName, ev.at);
		leave.fromRank = ev.fromRank;
		leave.approximate = ev.approximate;
		RankEvent join = new RankEvent(state.newId(), EventType.JOIN, ev.name, ev.at);
		join.toRank = ev.toRank;
		join.approximate = ev.approximate;
		insertChronologically(leave);
		insertChronologically(join);
		return true;
	}

	public synchronized boolean markReviewed(String name, long now)
	{
		MemberRecord m = state.members.get(Names.key(name));
		if (m == null)
		{
			return false;
		}
		m.lastReviewed = now;
		return true;
	}

	public synchronized boolean setRankSince(String name, long when)
	{
		MemberRecord m = state.members.get(Names.key(name));
		if (m == null)
		{
			return false;
		}
		m.rankSince = when;
		m.rankSinceEstimated = false;
		return true;
	}

	public synchronized void setReviewPeriod(int rank, ReviewPeriod period)
	{
		state.reviewPeriods.put(rank, new ReviewPeriod(period.amount, period.unit));
	}

	public synchronized void markWomChecked(long pendingId)
	{
		for (PendingRename p : state.pending)
		{
			if (p.id == pendingId)
			{
				p.womChecked = true;
			}
		}
	}

	public synchronized PendingRename findPending(long id)
	{
		for (PendingRename p : state.pending)
		{
			if (p.id == id)
			{
				return p;
			}
		}
		return null;
	}

	private PendingRename removePending(long id)
	{
		for (Iterator<PendingRename> it = state.pending.iterator(); it.hasNext(); )
		{
			PendingRename p = it.next();
			if (p.id == id)
			{
				it.remove();
				return p;
			}
		}
		return null;
	}

	private void clearPendingMarks(long pendingId)
	{
		for (RankEvent e : state.events)
		{
			if (e.pendingId != null && e.pendingId == pendingId)
			{
				e.pendingId = null;
			}
		}
	}

	private void insertChronologically(RankEvent e)
	{
		int i = state.events.size();
		while (i > 0 && state.events.get(i - 1).at > e.at)
		{
			i--;
		}
		state.events.add(i, e);
	}

	private static void addPrevious(MemberRecord rec, String name)
	{
		String k = Names.key(name);
		for (String p : rec.previousNames)
		{
			if (Names.key(p).equals(k))
			{
				return;
			}
		}
		rec.previousNames.add(name);
	}

	private void trim()
	{
		int excess = state.events.size() - ClanState.MAX_EVENTS;
		if (excess > 0)
		{
			state.events.subList(0, excess).clear();
		}
	}
}
