package com.clanranktracker.ui;

import java.awt.Color;
import java.awt.Font;
import net.runelite.client.ui.FontManager;

/** The panel's single source of colour and type. One accent, RuneLite's orange. */
public final class Palette
{
	public static final Color BG = new Color(0x1E1E1E);
	public static final Color HEADER = new Color(0x171717);
	public static final Color ROW_HOVER = new Color(0x242424);
	public static final Color CHIP = new Color(0x2B2B2B);
	public static final Color CHIP_HOVER = new Color(0x363636);
	public static final Color LINE = new Color(0x2A2A2A);
	public static final Color LINE_STRONG = new Color(0x3A3A3A);
	public static final Color ACCENT = new Color(0xDC8A00);
	public static final Color ACCENT_BG = new Color(0x262119);
	public static final Color OVERDUE = new Color(0xE05A3A);
	public static final Color PROMOTE = new Color(0x5FB35F);
	public static final Color NEUTRAL = new Color(0x8A8A8A);
	public static final Color MUTED = new Color(0x7A7A7A);
	public static final Color TEXT = new Color(0xC8C8C8);
	public static final Color TEXT_HI = new Color(0xE6E6E6);
	public static final Color BAR_TRACK = new Color(0x2E2E2E);
	public static final Color BAR_SOON = new Color(0x6E6048);
	public static final Color BAR_IDLE = new Color(0x5A5A5A);

	public static final int PAD = 8;

	private Palette()
	{
	}

	public static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	public static Font body()
	{
		return FontManager.getRunescapeFont();
	}

	public static Font bold()
	{
		return FontManager.getRunescapeBoldFont();
	}

	/** Uses a glyph only if the RuneScape font can draw it. */
	public static String glyph(Font f, String preferred, String fallback)
	{
		return f.canDisplayUpTo(preferred) == -1 ? preferred : fallback;
	}

	public static String dot(Font f)
	{
		return glyph(f, " · ", " - ");
	}

	public static String arrow(Font f)
	{
		return glyph(f, " → ", " > ");
	}
}
