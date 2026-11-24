package com.clanranktracker.data;

/** A leave and a join that might be the same player renaming, waiting for a decision. */
public class PendingRename
{
	public long id;
	public String oldName;
	public String newName;
	public int oldRank;
	public int newRank;
	public long at;
	/** Why the pair was flagged, e.g. same join date but a different rank. */
	public String why;
	public boolean womChecked;

	public PendingRename()
	{
	}

	public PendingRename(long id, String oldName, String newName, int oldRank, int newRank, long at, String why)
	{
		this.id = id;
		this.oldName = oldName;
		this.newName = newName;
		this.oldRank = oldRank;
		this.newRank = newRank;
		this.at = at;
		this.why = why;
	}
}
