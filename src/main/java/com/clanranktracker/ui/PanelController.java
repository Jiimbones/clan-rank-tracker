package com.clanranktracker.ui;

import com.clanranktracker.data.RankInfo;
import com.clanranktracker.data.ReviewPeriod;
import java.awt.Component;
import java.awt.image.BufferedImage;

/** Everything the panel can ask the plugin to do. Called on the Swing thread. */
public interface PanelController
{
	void markReviewed(String name);

	/** @return false if the member is gone */
	boolean setRankSince(String name, long epochMillis);

	void setReviewPeriod(int rank, ReviewPeriod period);

	void confirmRename(long pendingId);

	void rejectRename(long pendingId);

	void undoRename(long eventId);

	void exportCsv(Component parent);

	/** The game's icon for a rank, or null if not loaded. */
	BufferedImage rankIcon(RankInfo rank);
}
