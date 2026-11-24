package com.clanranktracker.core;

import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.RosterEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Fixtures
{
	static final long T0 = 1_700_000_000_000L;
	static final long MIN = 60_000L;
	static final long DAY = ReviewMath.DAY_MS;

	private Fixtures()
	{
	}

	static RosterEntry e(String name, int rank, Long joinDay)
	{
		return new RosterEntry(name, rank, joinDay);
	}

	static RosterEntry e(String name, int rank)
	{
		return new RosterEntry(name, rank, null);
	}

	static MemberRecord m(String name, int rank, Long joinDay)
	{
		return new MemberRecord(name, rank, joinDay, T0, false);
	}

	static List<RosterEntry> roster(RosterEntry... es)
	{
		return new ArrayList<>(Arrays.asList(es));
	}
}
