package com.clanranktracker.ui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/** A square-cornered tag. Outlined in accent when it needs attention. */
public class Chip extends JComponent
{
	public enum Style
	{
		NORMAL,
		ATTENTION,
		SELECTED,
		QUIET
	}

	private static final int H = 18;
	private static final int PAD_X = 5;

	private final String text;
	private final Style style;
	private boolean hover;

	public Chip(String text, Style style, Runnable onClick)
	{
		this.text = text;
		this.style = style;
		setFont(Palette.small());
		setToolTipText(text);
		if (onClick != null)
		{
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					onClick.run();
				}
			});
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		FontMetrics fm = getFontMetrics(getFont());
		return new Dimension(fm.stringWidth(text) + PAD_X * 2, H);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return getPreferredSize();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		int w = getWidth();
		int h = getHeight();
		boolean outlined = style == Style.ATTENTION || style == Style.SELECTED;
		if (!outlined || hover)
		{
			g.setColor(hover ? Palette.CHIP_HOVER : Palette.CHIP);
			g.fillRect(0, 0, w, h);
		}
		if (outlined)
		{
			g.setColor(Palette.ACCENT);
			g.drawRect(0, 0, w - 1, h - 1);
		}
		FontMetrics fm = g.getFontMetrics(getFont());
		g.setFont(getFont());
		switch (style)
		{
			case ATTENTION:
				g.setColor(Palette.ACCENT);
				break;
			case SELECTED:
				g.setColor(Palette.TEXT_HI);
				break;
			case QUIET:
				g.setColor(Palette.MUTED);
				break;
			default:
				g.setColor(hover ? Palette.TEXT_HI : Palette.TEXT);
		}
		String t = Ui.fit(text, fm, w - PAD_X * 2);
		int y = (h - fm.getHeight()) / 2 + fm.getAscent();
		g.drawString(t, PAD_X, y);
	}
}
