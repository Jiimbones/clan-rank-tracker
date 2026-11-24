package com.clanranktracker.core;

import static com.clanranktracker.core.Fixtures.e;
import static com.clanranktracker.core.Fixtures.m;
import static com.clanranktracker.core.Fixtures.roster;
import com.clanranktracker.data.MemberRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SnapshotDifferTest
{
	private static Map<String, MemberRecord> known(MemberRecord... ms)
	{
		Map<String, MemberRecord> map = new LinkedHashMap<>();
		for (MemberRecord r : ms)
		{
			map.put(com.clanranktracker.data.Names.key(r.name), r);
		}
		return map;
	}

	@Test
	public void identicalRosterIsEmpty()
	{
		SnapshotDiffer.Diff d = SnapshotDiffer.diff(known(m("Alice", 10, 1L)), roster(e("Alice", 10, 1L)));
		assertTrue(d.isEmpty());
	}

	@Test
	public void detectsJoinLeaveAndRankChange()
	{
		SnapshotDiffer.Diff d = SnapshotDiffer.diff(
			known(m("Alice", 10, 1L), m("Bob", 20, 2L)),
			roster(e("Alice", 30, 1L), e("Cara", 10, 3L)));
		assertEquals(1, d.leaves.size());
		assertEquals("Bob", d.leaves.get(0).name);
		assertEquals(1, d.joins.size());
		assertEquals("Cara", d.joins.get(0).name);
		assertEquals(1, d.rankChanges.size());
		assertEquals(30, d.rankChanges.get(0).entry.rank);
	}

	@Test
	public void namesMatchAcrossCaseSpacesAndUnderscores()
	{
		SnapshotDiffer.Diff d = SnapshotDiffer.diff(known(m("Iron Mallard", 10, 1L)), roster(e("iron_mallard", 10, 1L)));
		assertTrue(d.isEmpty());
		d = SnapshotDiffer.diff(known(m("Iron Mallard", 10, 1L)), roster(e("Iron Mallard", 10, 1L)));
		assertTrue(d.isEmpty());
	}

	@Test
	public void duplicateRosterNamesCountOnce()
	{
		SnapshotDiffer.Diff d = SnapshotDiffer.diff(known(), roster(e("Alice", 10), e("alice", 20)));
		assertEquals(1, d.joins.size());
		assertEquals(10, d.joins.get(0).rank);
	}

	@Test
	public void fillsMissingJoinDate()
	{
		SnapshotDiffer.Diff d = SnapshotDiffer.diff(known(m("Alice", 10, null)), roster(e("Alice", 10, 5L)));
		assertEquals(1, d.joinDateFills.size());
		assertEquals(0, d.rankChanges.size());
	}
}
