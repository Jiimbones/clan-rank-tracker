package com.clanranktracker.ui;

import com.clanranktracker.data.EventType;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One event on the history rail. The rail and node are painted, so shapes never depend
 * on which glyphs the RuneScape font happens to include.
 */
public class TimelineRow extends JPanel
{
	private static final int RAIL_X = 5;
	private static final int NODE = 9;
	private static final int TEXT_X = 18;
	private static final int H = 34;

	private final EventType type;
	private final boolean first;
	private final boolean last;
	private final boolean pending;

	public TimelineRow(EventType type, boolean pending, JComponent title, String detail, JComponent action, boolean first, boolean last)
	{
		super(null);
		this.type = type;
		this.first = first;
		this.last = last;
		this.pending = pending;
		setOpaque(false);

		JPanel text = Parts.line(title, action, 16);
		JLabel d = Ui.label(detail, Palette.small(), Palette.MUTED);
		add(text);
		add(d);
		setPreferredSize(new Dimension(10, H));
	}

	@Override
	public void doLayout()
	{
		int w = getWidth() - TEXT_X;
		getComponent(0).setBounds(TEXT_X, 2, w, 16);
		getComponent(1).setBounds(TEXT_X, 17, w, 14);
	}

	public static Color colorFor(EventType t)
	{
		switch (t)
		{
			case PROMOTE:
				return Palette.PROMOTE;
			case DEMOTE:
				return Palette.OVERDUE;
			case RENAME:
				return Palette.ACCENT;
			default:
				return Palette.NEUTRAL;
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = Ui.smooth(g);
		int cy = 9;
		int x = RAIL_X - 4;
		int y = cy - NODE / 2;
		int top = first ? cy : 0;
		int bottom = last ? cy : getHeight();
		g2.setColor(Palette.LINE_STRONG);
		g2.fillRect(x + NODE / 2, top, 1, bottom - top);
		Color c = pending ? Palette.MUTED : colorFor(type);
		g2.setColor(c);
		switch (type)
		{
			case PROMOTE:
				g2.fillPolygon(new Polygon(new int[]{x + NODE / 2, x + NODE, x}, new int[]{y, y + NODE, y + NODE}, 3));
				break;
			case DEMOTE:
				g2.fillPolygon(new Polygon(new int[]{x, x + NODE, x + NODE / 2}, new int[]{y, y, y + NODE}, 3));
				break;
			case RENAME:
				g2.fillPolygon(new Polygon(new int[]{x + NODE / 2, x + NODE, x + NODE / 2, x}, new int[]{y, y + NODE / 2, y + NODE, y + NODE / 2}, 4));
				break;
			case JOIN:
				g2.fillRect(x + 1, y + 1, NODE - 2, NODE - 2);
				break;
			case LEAVE:
			default:
				g2.setColor(Palette.BG);
				g2.fillRect(x + 1, y + 1, NODE - 2, NODE - 2);
				g2.setColor(c);
				g2.drawRect(x + 1, y + 1, NODE - 3, NODE - 3);
				break;
		}
		g2.dispose();
	}
}
