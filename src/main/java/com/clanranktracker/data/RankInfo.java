package com.clanranktracker.data;

/** A clan rank value with the custom title the clan gave it. */
public class RankInfo
{
	/** Title ids can be negative (staff ranks), so "no title" needs its own marker. */
	public static final int NO_TITLE = Integer.MIN_VALUE;

	public int rank;
	public int titleId;
	public String title;

	public RankInfo()
	{
	}

	public RankInfo(int rank, int titleId, String title)
	{
		this.rank = rank;
		this.titleId = titleId;
		this.title = title;
	}
}
