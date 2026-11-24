package com.clanranktracker.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Sidebar icon: three ascending rungs, the top one in accent. Drawn, so there's no image to ship. */
public final class NavIcon
{
	private NavIcon()
	{
	}

	public static BufferedImage create()
	{
		return draw(16);
	}

	/** Same mark at any size, used for the Plugin Hub icon too. */
	public static BufferedImage draw(int size)
	{
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		float u = size / 16f;
		int barW = Math.max(2, Math.round(4 * u));
		int gap = Math.max(1, Math.round(1 * u));
		int x0 = Math.round(1 * u);
		int[] heights = {Math.round(6 * u), Math.round(10 * u), Math.round(14 * u)};
		int bottom = size - Math.round(1 * u);
		for (int i = 0; i < 3; i++)
		{
			g.setColor(i == 2 ? Palette.ACCENT : i == 1 ? Palette.NEUTRAL : Palette.LINE_STRONG.brighter());
			int x = x0 + i * (barW + gap);
			g.fillRect(x, bottom - heights[i], barW, heights[i]);
		}
		g.dispose();
		return img;
	}
}
