package com.clanranktracker;

import com.clanranktracker.data.ReviewPeriod;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(ClanRankTrackerConfig.GROUP)
public interface ClanRankTrackerConfig extends Config
{
	String GROUP = "clan-rank-tracker";

	@ConfigSection(
		name = "Reviews",
		description = "When members show up as due for a rank review",
		position = 0
	)
	String reviews = "reviews";

	@ConfigSection(
		name = "Renames",
		description = "How name changes are detected",
		position = 1
	)
	String renames = "renames";

	@Range(min = 1, max = 3650)
	@ConfigItem(
		keyName = "defaultReviewAmount",
		name = "Default review time",
		description = "How long a member stays in a rank before they're due for review. Override it per rank in the Members tab.",
		section = reviews,
		position = 0
	)
	default int defaultReviewAmount()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "defaultReviewUnit",
		name = "Review time unit",
		description = "Whether the default review time is in days, months or years.",
		section = reviews,
		position = 1
	)
	default ReviewPeriod.Unit defaultReviewUnit()
	{
		return ReviewPeriod.Unit.DAYS;
	}

	@ConfigItem(
		keyName = "staffNeverDue",
		name = "Skip staff ranks",
		description = "Administrator rank and above are never due unless you set days for that rank.",
		section = reviews,
		position = 2
	)
	default boolean staffNeverDue()
	{
		return true;
	}

	@Range(min = 50, max = 99)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "comingUpPercent",
		name = "Coming up at",
		description = "List members under 'Coming up' once they've spent this much of their review time.",
		section = reviews,
		position = 3
	)
	default int comingUpPercent()
	{
		return 75;
	}

	@ConfigItem(
		keyName = "womConfirmRenames",
		name = "Confirm renames with Wise Old Man",
		description = "Look up possible renames on Wise Old Man to confirm them automatically. Sends only the new name of each possible rename.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = renames,
		position = 0
	)
	default boolean womConfirmRenames()
	{
		return false;
	}
}
