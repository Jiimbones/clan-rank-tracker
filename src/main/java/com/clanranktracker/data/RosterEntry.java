package com.clanranktracker.data;

/** One member as the game reports them right now. */
public final class RosterEntry
{
	public final String name;
	public final int rank;
	public final Long joinEpochDay;

	public RosterEntry(String name, int rank, Long joinEpochDay)
	{
		this.name = Names.clean(name);
		this.rank = rank;
		this.joinEpochDay = joinEpochDay;
	}

	public String key()
	{
		return Names.key(name);
	}
}
