package com.clanranktracker.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;

/** Small building blocks shared by every tab. */
public final class Ui
{
	private Ui()
	{
	}

	public static JLabel label(String text, Font font, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		return l;
	}

	public static Border pad(int top, int left, int bottom, int right)
	{
		return BorderFactory.createEmptyBorder(top, left, bottom, right);
	}

	/** A text link: muted at rest, bright and underlined on hover. */
	public static JLabel action(String text, Color color, Runnable onClick)
	{
		JLabel l = new JLabel(text)
		{
			private boolean hover;

			{
				addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseEntered(MouseEvent e)
					{
						hover = true;
						setForeground(Palette.TEXT_HI);
					}

					@Override
					public void mouseExited(MouseEvent e)
					{
						hover = false;
						setForeground(color);
					}

					@Override
					public void mouseClicked(MouseEvent e)
					{
						onClick.run();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				if (hover)
				{
					Insets in = getInsets();
					int y = getHeight() - in.bottom - 2;
					int w = getFontMetrics(getFont()).stringWidth(getText());
					g.setColor(Palette.TEXT_HI);
					g.drawLine(in.left, y, in.left + w - 1, y);
				}
			}
		};
		l.setFont(Palette.small());
		l.setForeground(color);
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return l;
	}

	public static void hand(JComponent c, Runnable onClick)
	{
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		c.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}
		});
	}

	public static JPanel transparent()
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		return p;
	}

	public static Graphics2D smooth(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g2;
	}

	/** A fixed-height gap for vertical stacks. */
	public static Component gap(int h)
	{
		JPanel p = transparent();
		p.setPreferredSize(new Dimension(1, h));
		return p;
	}

	/** Truncates with an ellipsis-like ".." so long names never push layout wider than the panel. */
	public static String fit(String s, java.awt.FontMetrics fm, int width)
	{
		if (s == null)
		{
			return "";
		}
		if (fm.stringWidth(s) <= width)
		{
			return s;
		}
		String dots = "..";
		int end = s.length();
		while (end > 0 && fm.stringWidth(s.substring(0, end) + dots) > width)
		{
			end--;
		}
		return s.substring(0, end) + dots;
	}
}
