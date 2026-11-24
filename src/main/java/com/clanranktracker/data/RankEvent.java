package com.clanranktracker.data;

/** One entry in the clan history timeline. */
public class RankEvent
{
	public long id;
	public EventType type;
	/** The member's name at the time of the event (the new name for renames). */
	public String name;
	/** Renames only: the previous name. */
	public String oldName;
	public Integer fromRank;
	public Integer toRank;
	public long at;
	/** True when the change happened while the plugin wasn't watching, so {@link #at} is when it was noticed. */
	public boolean approximate;
	/** Set on the provisional JOIN/LEAVE pair while a possible rename waits for a decision. */
	public Long pendingId;
	/** Renames only: why the link was made. */
	public RenameReason reason;
	/** Renames only: the old member record before it was merged, so the link can be undone. */
	public MemberRecord undoOld;
	/** Renames only: the new member's fields before the merge. */
	public MemberRecord undoNew;

	public RankEvent()
	{
	}

	public RankEvent(long id, EventType type, String name, long at)
	{
		this.id = id;
		this.type = type;
		this.name = name;
		this.at = at;
	}
}
