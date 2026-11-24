package com.clanranktracker.core;

import static com.clanranktracker.core.Fixtures.e;
import static com.clanranktracker.core.Fixtures.m;
import com.clanranktracker.data.RenameReason;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RenameDetectorTest
{
	private static final Map<String, String> NO_FRIENDS = Collections.emptyMap();

	@Test
	public void sameJoinDateAndRankLinksAutomatically()
	{
		RenameDetector.Result r = RenameDetector.detect(
			List.of(m("Fally Knight", 50, 100L)),
			List.of(e("Fally Kn1ght", 50, 100L)), NO_FRIENDS);
		assertEquals(1, r.auto.size());
		assertEquals(RenameReason.JOIN_DATE, r.auto.get(0).reason);
		assertEquals(0, r.leaves.size());
		assertEquals(0, r.joins.size());
	}

	@Test
	public void differentJoinDatesAreNeverLinked()
	{
		RenameDetector.Result r = RenameDetector.detect(
			List.of(m("Old Timer", 50, 100L)),
			List.of(e("New Kid", 50, 200L)), NO_FRIENDS);
		assertEquals(0, r.auto.size());
		assertEquals(0, r.possible.size());
		assertEquals(1, r.leaves.size());
		assertEquals(1, r.joins.size());
	}

	@Test
	public void sameJoinDateButRankChangedIsQueued()
	{
		RenameDetector.Result r = RenameDetector.detect(
			List.of(m("A", 50, 100L)),
			List.of(e("B", 60, 100L)), NO_FRIENDS);
		assertEquals(0, r.auto.size());
		assertEquals(1, r.possible.size());
	}

	@Test
	public void soleUndatedSwapAtSameRankLinks()
	{
		RenameDetector.Result r = RenameDetector.detect(
			List.of(m("A", 50, null)),
			List.of(e("B", 50, null)), NO_FRIENDS);
		assertEquals(1, r.auto.size());
		assertEquals(RenameReason.SOLE_SWAP, r.auto.get(0).reason);
	}

	@Test
	public void ambiguousUndatedSwapsAreQueuedOnlyWhereUnique()
	{
		RenameDetector.Result r = RenameDetector.detect(
			List.of(m("A", 50, null), m("B", 50, null), m("C", 70, null)),
			List.of(e("X", 50, null), e("Y", 50, null), e("Z", 70, null)), NO_FRIENDS);
		assertEquals(0, r.auto.size());
		assertEquals(1, r.possible.size());
		assertEquals("C", r.possible.get(0).old.name);
		assertEquals("Z", r.possible.get(0).neu.name);
		assertEquals(2, r.leaves.size());
		assertEquals(2, r.joins.size());
	}

	@Test
	public void sharedJoinDatePairsByRank()
	{
		RenameDetector.Result r = RenameDetector.detect(
			List.of(m("A", 50, 100L), m("B", 70, 100L)),
			List.of(e("X", 50, 100L), e("Y", 70, 100L)), NO_FRIENDS);
		assertEquals(0, r.auto.size());
		assertEquals(2, r.possible.size());
	}

	@Test
	public void friendsListConfirmsEvenAcrossRankChange()
	{
		RenameDetector.Result r = RenameDetector.detect(
			List.of(m("Old Name", 50, 100L), m("Someone", 50, 300L)),
			List.of(e("New Name", 60, 100L), e("Another", 50, 400L)),
			Map.of("new name", "old name"));
		assertEquals(1, r.auto.size());
		assertEquals(RenameReason.FRIENDS_LIST, r.auto.get(0).reason);
		assertEquals("Old Name", r.auto.get(0).old.name);
		assertEquals(1, r.leaves.size());
		assertEquals(1, r.joins.size());
	}

	@Test
	public void plainLeaveAndJoinAtDifferentRanksStayApart()
	{
		RenameDetector.Result r = RenameDetector.detect(
			List.of(m("A", 50, null)),
			List.of(e("B", 10, null)), NO_FRIENDS);
		assertEquals(0, r.auto.size());
		assertEquals(0, r.possible.size());
	}
}
