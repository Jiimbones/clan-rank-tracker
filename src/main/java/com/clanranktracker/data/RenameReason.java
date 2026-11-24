package com.clanranktracker.data;

public enum RenameReason
{
	/** Your friends list reported the previous name. */
	FRIENDS_LIST("Friends list"),
	/** Wise Old Man has the name change on record. */
	WISE_OLD_MAN("Wise Old Man"),
	/** Same clan join date and rank, and the only such pair in the update. */
	JOIN_DATE("Same join date"),
	/** The only member to leave and the only one to join, at the same rank. */
	SOLE_SWAP("Only swap at rank"),
	/** Possible match that needs a leader's decision. */
	POSSIBLE("Possible match"),
	/** A leader confirmed it by hand. */
	MANUAL("Confirmed by you");

	private final String label;

	RenameReason(String label)
	{
		this.label = label;
	}

	public String label()
	{
		return label;
	}
}
