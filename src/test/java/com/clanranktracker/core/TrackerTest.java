package com.clanranktracker.core;

import static com.clanranktracker.core.Fixtures.DAY;
import static com.clanranktracker.core.Fixtures.MIN;
import static com.clanranktracker.core.Fixtures.T0;
import static com.clanranktracker.core.Fixtures.e;
import static com.clanranktracker.core.Fixtures.roster;
import com.clanranktracker.data.ClanState;
import com.clanranktracker.data.EventType;
import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.PendingRename;
import com.clanranktracker.data.RankEvent;
import com.clanranktracker.data.RenameReason;
import com.clanranktracker.data.ReviewPeriod;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TrackerTest
{
	private static Tracker baseline(long at, com.clanranktracker.data.RosterEntry... es)
	{
		Tracker t = new Tracker(new ClanState());
		t.apply(roster(es), null, Collections.emptyMap(), at);
		return t;
	}

	private static List<EventType> types(Tracker t)
	{
		return t.read(s -> s.events.stream().map(x -> x.type).collect(Collectors.toList()));
	}

	@Test
	public void firstSnapshotIsBaselineWithoutEvents()
	{
		Tracker t = new Tracker(new ClanState());
		Tracker.ApplyResult r = t.apply(roster(e("A", 10, 1L), e("B", 20, 2L)), null, Collections.emptyMap(), T0);
		assertTrue(r.baseline);
		assertEquals(0, (int) t.read(s -> s.events.size()));
		assertEquals(2, (int) t.read(s -> s.members.size()));
		assertTrue(t.read(s -> s.members.get("a").rankSinceEstimated));
	}

	@Test
	public void baselineCountsFromJoinDate()
	{
		Tracker t = new Tracker(new ClanState());
		t.apply(roster(e("A", 10, 100L), e("NoDate", 10, null), e("Future", 10, T0 / DAY + 5)), null, Collections.emptyMap(), T0);
		MemberRecord a = t.read(s -> s.members.get("a"));
		assertEquals(MemberRecord.joinMillis(100), a.rankSince);
		assertTrue(a.countsFromJoin());
		MemberRecord n = t.read(s -> s.members.get("nodate"));
		assertEquals(T0, n.rankSince);
		assertTrue(n.countsFromTracking());
		// A join date after "now" (clock skew) never pushes the clock into the future.
		assertEquals(T0, (long) t.read(s -> s.members.get("future").rankSince));
	}

	@Test
	public void version1FilesMigrateToJoinDateAndPeriods()
	{
		ClanState old = new ClanState();
		old.version = 1;
		old.baselineTaken = true;
		old.baselineAt = T0;
		MemberRecord untouched = new MemberRecord("A", 10, 100L, T0, true);
		MemberRecord manual = new MemberRecord("B", 10, 100L, T0 - DAY, false);
		old.members.put("a", untouched);
		old.members.put("b", manual);
		old.thresholds = new java.util.LinkedHashMap<>(java.util.Map.of(10, 14));
		old.repair();
		assertEquals(MemberRecord.joinMillis(100), untouched.rankSince);
		assertEquals(T0 - DAY, manual.rankSince);
		assertEquals(14, old.reviewPeriods.get(10).amount);
		assertEquals(ReviewPeriod.Unit.DAYS, old.reviewPeriods.get(10).unit);
		assertNull(old.thresholds);
		assertEquals(ClanState.CURRENT_VERSION, old.version);
	}

	@Test
	public void repeatedSnapshotChangesNothing()
	{
		Tracker t = baseline(T0, e("A", 10, 1L));
		Tracker.ApplyResult r = t.apply(roster(e("A", 10, 1L)), null, Collections.emptyMap(), T0 + MIN);
		assertFalse(r.changed);
		assertEquals(0, (int) t.read(s -> s.events.size()));
	}

	@Test
	public void emptyRosterIsIgnored()
	{
		Tracker t = baseline(T0, e("A", 10, 1L));
		Tracker.ApplyResult r = t.apply(roster(), null, Collections.emptyMap(), T0 + MIN);
		assertFalse(r.changed);
		assertEquals(1, (int) t.read(s -> s.members.size()));
	}

	@Test
	public void promotionAndDemotionResetRankClock()
	{
		Tracker t = baseline(T0, e("A", 10, 1L), e("B", 30, 2L));
		t.apply(roster(e("A", 20, 1L), e("B", 10, 2L)), null, Collections.emptyMap(), T0 + MIN);
		assertEquals(List.of(EventType.PROMOTE, EventType.DEMOTE), types(t));
		MemberRecord a = t.read(s -> s.members.get("a"));
		assertEquals(20, a.rank);
		assertEquals(T0 + MIN, a.rankSince);
		assertFalse(a.rankSinceEstimated);
	}

	@Test
	public void changesAfterLongGapAreMarkedApproximate()
	{
		Tracker t = baseline(T0, e("A", 10, 1L));
		t.apply(roster(e("A", 20, 1L)), null, Collections.emptyMap(), T0 + DAY);
		assertTrue(t.read(s -> s.events.get(0).approximate));
	}

	@Test
	public void touchKeepsChangesExact()
	{
		Tracker t = baseline(T0, e("A", 10, 1L));
		t.touch(T0 + DAY - MIN);
		t.apply(roster(e("A", 20, 1L)), null, Collections.emptyMap(), T0 + DAY);
		assertFalse(t.read(s -> s.events.get(0).approximate));
	}

	@Test
	public void leaveThenRejoinRestoresHistory()
	{
		Tracker t = baseline(T0, e("A", 10, 1L), e("B", 10, 2L));
		t.apply(roster(e("B", 10, 2L)), null, Collections.emptyMap(), T0 + MIN);
		assertTrue(t.read(s -> s.departed.containsKey("a")));
		t.apply(roster(e("A", 10, 9L), e("B", 10, 2L)), null, Collections.emptyMap(), T0 + 2 * MIN);
		assertEquals(List.of(EventType.LEAVE, EventType.JOIN), types(t));
		MemberRecord a = t.read(s -> s.members.get("a"));
		assertEquals(T0, a.firstSeen);
		assertEquals(Long.valueOf(9L), a.joinEpochDay);
	}

	@Test
	public void autoRenameCarriesHistoryAndCanBeUndone()
	{
		Tracker t = baseline(T0, e("Fally Knight", 50, 100L));
		t.markReviewed("Fally Knight", T0 + MIN);
		t.apply(roster(e("Fally Kn1ght", 50, 100L)), null, Collections.emptyMap(), T0 + 2 * MIN);

		assertEquals(List.of(EventType.RENAME), types(t));
		MemberRecord rec = t.read(s -> s.members.get("fally kn1ght"));
		assertNotNull(rec);
		assertEquals(MemberRecord.joinMillis(100), rec.rankSince);
		assertEquals(Long.valueOf(T0 + MIN), rec.lastReviewed);
		assertEquals(List.of("Fally Knight"), rec.previousNames);
		assertFalse(t.read(s -> s.members.containsKey("fally knight")));

		long eventId = t.read(s -> s.events.get(0).id);
		assertTrue(t.undoRename(eventId));
		assertEquals(List.of(EventType.LEAVE, EventType.JOIN), types(t));
		MemberRecord split = t.read(s -> s.members.get("fally kn1ght"));
		assertEquals(T0 + 2 * MIN, split.rankSince);
		assertTrue(split.previousNames.isEmpty());
		assertTrue(t.read(s -> s.departed.containsKey("fally knight")));
	}

	@Test
	public void renameWithRankChangeLogsBoth()
	{
		Tracker t = baseline(T0, e("A", 50, 100L));
		t.apply(roster(e("B", 60, 100L)), null, java.util.Map.of("b", "a"), T0 + MIN);
		assertEquals(List.of(EventType.RENAME, EventType.PROMOTE), types(t));
		assertEquals(60, (int) t.read(s -> s.members.get("b").rank));
		long id = t.read(s -> s.events.get(0).id);
		assertTrue(t.undoRename(id));
		assertEquals(List.of(EventType.LEAVE, EventType.JOIN), types(t));
	}

	@Test
	public void possibleRenameIsProvisionalUntilConfirmed()
	{
		Tracker t = baseline(T0, e("A", 50, null), e("B", 50, null));
		t.setRankSince("A", T0 - 10 * DAY);
		Tracker.ApplyResult r = t.apply(roster(e("X", 50, null), e("Y", 50, null)), null, Collections.emptyMap(), T0 + MIN);
		// Two swaps at one rank: ambiguous, nothing linked or queued.
		assertEquals(0, r.newPending.size());

		Tracker t2 = baseline(T0, e("A", 50, 100L));
		t2.setRankSince("A", T0 - 10 * DAY);
		r = t2.apply(roster(e("X", 60, 100L)), null, Collections.emptyMap(), T0 + MIN);
		assertEquals(1, r.newPending.size());
		assertEquals(List.of(EventType.LEAVE, EventType.JOIN), types(t2));
		PendingRename p = r.newPending.get(0);

		assertTrue(t2.confirmRename(p.id, RenameReason.MANUAL));
		assertEquals(List.of(EventType.RENAME, EventType.PROMOTE), types(t2));
		MemberRecord x = t2.read(s -> s.members.get("x"));
		assertEquals(60, x.rank);
		assertEquals(List.of("A"), x.previousNames);
		assertEquals(0, (int) t2.read(s -> s.pending.size()));
		assertFalse(t2.read(s -> s.departed.containsKey("a")));
	}

	@Test
	public void confirmedRenameAtSameRankKeepsRankSince()
	{
		Tracker t = baseline(T0, e("A", 50, 100L), e("B", 50, 100L));
		t.setRankSince("A", T0 - 10 * DAY);
		Tracker.ApplyResult r = t.apply(roster(e("X", 50, 100L), e("B", 50, 100L)), null, Collections.emptyMap(), T0 + MIN);
		// Unique same-date same-rank swap links automatically.
		assertEquals(0, r.newPending.size());
		assertEquals(T0 - 10 * DAY, (long) t.read(s -> s.members.get("x").rankSince));
	}

	@Test
	public void rejectKeepsLeaveAndJoin()
	{
		Tracker t = baseline(T0, e("A", 50, 100L));
		Tracker.ApplyResult r = t.apply(roster(e("X", 60, 100L)), null, Collections.emptyMap(), T0 + MIN);
		long id = r.newPending.get(0).id;
		assertTrue(t.rejectRename(id));
		assertEquals(List.of(EventType.LEAVE, EventType.JOIN), types(t));
		assertTrue(t.read(s -> s.events.stream().allMatch(ev -> ev.pendingId == null)));
		assertFalse(t.rejectRename(id));
	}

	@Test
	public void confirmFailsGracefullyWhenMemberGone()
	{
		Tracker t = baseline(T0, e("A", 50, 100L));
		Tracker.ApplyResult r = t.apply(roster(e("X", 60, 100L)), null, Collections.emptyMap(), T0 + MIN);
		long id = r.newPending.get(0).id;
		t.apply(roster(e("Z", 10, 500L)), null, Collections.emptyMap(), T0 + 2 * MIN);
		assertFalse(t.confirmRename(id, RenameReason.MANUAL));
		RankEvent last = t.read(s -> s.events.get(s.events.size() - 1));
		assertNull(last.pendingId);
	}

	@Test
	public void reviewClockUsesLaterOfRankSinceAndReview()
	{
		Tracker t = baseline(T0, e("A", 10, null));
		MemberRecord a = t.read(s -> s.members.get("a"));
		assertEquals(40, ReviewMath.daysOnClock(a, T0 + 40 * DAY));
		t.markReviewed("A", T0 + 30 * DAY);
		assertEquals(10, ReviewMath.daysOnClock(a, T0 + 40 * DAY));
	}

	@Test
	public void estimatedRankDateWalksTheLadder()
	{
		java.time.ZoneId utc = java.time.ZoneOffset.UTC;
		long joined = java.time.LocalDate.of(2024, 1, 1).atStartOfDay(utc).toInstant().toEpochMilli();
		long now = java.time.LocalDate.of(2026, 1, 1).atStartOfDay(utc).toInstant().toEpochMilli();
		ReviewPeriod six = new ReviewPeriod(6, ReviewPeriod.Unit.MONTHS);
		List<Integer> ladder = List.of(10, 20, 30, 40, 50);

		// Entry rank: counts from joining, so 2 years in and well overdue.
		assertEquals(joined, ReviewMath.estimatedRankSince(joined, 10, ladder, r -> six, now, utc));
		// Third rank: two promotions after joining, so in rank since 1 Jan 2025.
		long rank30 = ReviewMath.estimatedRankSince(joined, 30, ladder, r -> six, now, utc);
		assertEquals(java.time.LocalDate.of(2025, 1, 1).atStartOfDay(utc).toInstant().toEpochMilli(), rank30);
		assertTrue(ReviewMath.isDue(rank30, six, now, utc));
		// Fifth rank: on schedule (4 steps = 2 years), reached exactly now, not due.
		long rank50 = ReviewMath.estimatedRankSince(joined, 50, ladder, r -> six, now, utc);
		assertEquals(now, rank50);
		assertFalse(ReviewMath.isDue(rank50, six, now, utc));
		// Ahead of schedule is capped at now rather than the future.
		List<Integer> longLadder = List.of(10, 20, 30, 40, 50, 60, 70);
		assertEquals(now, ReviewMath.estimatedRankSince(joined, 70, longLadder, r -> six, now, utc));
	}

	@Test
	public void periodsDefaultAndStaff()
	{
		ClanState s = new ClanState();
		ReviewPeriod def = new ReviewPeriod(30, ReviewPeriod.Unit.DAYS);
		assertEquals(30, ReviewMath.period(s, 10, def, true, null).amount);
		assertTrue(ReviewMath.period(s, 100, def, true, null).isOff());
		assertEquals(30, ReviewMath.period(s, 100, def, false, null).amount);
		s.reviewPeriods.put(10, new ReviewPeriod(6, ReviewPeriod.Unit.MONTHS));
		assertEquals(ReviewPeriod.Unit.MONTHS, ReviewMath.period(s, 10, def, true, null).unit);
	}

	@Test
	public void demotionFlagsForRePromotionUntilReviewedOrPromoted()
	{
		Tracker t = baseline(T0, e("A", 30, 1L), e("B", 30, 1L));
		t.apply(roster(e("A", 20, 1L), e("B", 20, 1L)), null, Collections.emptyMap(), T0 + MIN);
		MemberRecord a = t.read(s -> s.members.get("a"));
		assertEquals(Long.valueOf(T0 + MIN), a.demotedAt);
		assertTrue(a.awaitingRePromotion());

		t.markReviewed("A", T0 + 2 * MIN);
		assertFalse(a.awaitingRePromotion());

		t.apply(roster(e("A", 20, 1L), e("B", 30, 1L)), null, Collections.emptyMap(), T0 + 3 * MIN);
		MemberRecord b = t.read(s -> s.members.get("b"));
		assertNull(b.demotedAt);
		assertFalse(b.awaitingRePromotion());
	}

	@Test
	public void topReviewedRankDefaultsToNoReview()
	{
		ClanState s = new ClanState();
		Tracker t = new Tracker(s);
		t.apply(roster(e("Owner", 126, 1L), e("Admin", 100, 1L), e("Templar", 90, 1L), e("Paladin", 80, 1L)), null, Collections.emptyMap(), T0);
		ReviewPeriod def = new ReviewPeriod(6, ReviewPeriod.Unit.MONTHS);

		Integer top = ReviewMath.topReviewedRank(s, true);
		assertEquals(Integer.valueOf(90), top);
		assertTrue(ReviewMath.period(s, 90, def, true, top).isOff());
		assertFalse(ReviewMath.period(s, 80, def, true, top).isOff());
		// When staff ranks are reviewed, the top rank is the highest overall.
		assertEquals(Integer.valueOf(126), ReviewMath.topReviewedRank(s, false));
		// A period set by hand overrides the top-rank default.
		t.setReviewPeriod(90, new ReviewPeriod(1, ReviewPeriod.Unit.YEARS));
		assertFalse(ReviewMath.period(s, 90, def, true, top).isOff());
		assertNull(ReviewMath.topReviewedRank(new ClanState(), true));
	}

	@Test
	public void monthsAndYearsAreCalendarAware()
	{
		java.time.ZoneId utc = java.time.ZoneOffset.UTC;
		long jan31 = java.time.LocalDate.of(2026, 1, 31).atStartOfDay(utc).toInstant().toEpochMilli();
		long feb28 = java.time.LocalDate.of(2026, 2, 28).atStartOfDay(utc).toInstant().toEpochMilli();
		ReviewPeriod month = new ReviewPeriod(1, ReviewPeriod.Unit.MONTHS);
		assertEquals(feb28, month.dueAt(jan31, utc));

		MemberRecord m = new MemberRecord("A", 10, null, jan31, false);
		assertFalse(ReviewMath.isDue(m, month, feb28 - 1, utc));
		assertTrue(ReviewMath.isDue(m, month, feb28, utc));

		ReviewPeriod year = new ReviewPeriod(2, ReviewPeriod.Unit.YEARS);
		long later = java.time.LocalDate.of(2027, 6, 1).atStartOfDay(utc).toInstant().toEpochMilli();
		assertEquals(1, year.elapsed(jan31, later, utc));
		assertFalse(ReviewMath.isDue(m, year, later, utc));
		assertEquals(0.67, ReviewMath.fraction(m, year, later, utc), 0.01);

		assertFalse(ReviewMath.isDue(m, new ReviewPeriod(0, ReviewPeriod.Unit.DAYS), later, utc));
		assertEquals("6mo", new ReviewPeriod(6, ReviewPeriod.Unit.MONTHS).shortLabel());
		assertEquals("1 year", new ReviewPeriod(1, ReviewPeriod.Unit.YEARS).longLabel());
	}

	@Test
	public void eventsAreCapped()
	{
		ClanState s = new ClanState();
		Tracker t = new Tracker(s);
		t.apply(roster(e("A", 10, 1L)), null, Collections.emptyMap(), T0);
		for (int i = 0; i < ClanState.MAX_EVENTS + 10; i++)
		{
			t.apply(roster(e("A", i % 2 == 0 ? 20 : 10, 1L)), null, Collections.emptyMap(), T0 + (i + 1) * MIN);
		}
		assertEquals(ClanState.MAX_EVENTS, (int) t.read(x -> x.events.size()));
	}
}
