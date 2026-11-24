package com.clanranktracker.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Painted parts used across tabs. */
public final class Parts
{
	private Parts()
	{
	}

	/** One line with a left and an optional right component. */
	public static JPanel line(JComponent left, JComponent right, int height)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.add(left, BorderLayout.CENTER);
		if (right != null)
		{
			p.add(right, BorderLayout.EAST);
		}
		p.setPreferredSize(new Dimension(10, height));
		return p;
	}

	/** Right-aligned text actions with even spacing and no trailing gap. */
	public static JPanel actions(JComponent... items)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.X_AXIS));
		for (int i = 0; i < items.length; i++)
		{
			if (i > 0)
			{
				p.add(javax.swing.Box.createHorizontalStrut(10));
			}
			p.add(items[i]);
		}
		return p;
	}

	/** Small muted heading with an optional count, used above lists. */
	public static JPanel section(String title, String right)
	{
		JLabel l = Ui.label(title, Palette.small(), Palette.TEXT_HI);
		JLabel r = right == null ? null : Ui.label(right, Palette.small(), Palette.MUTED);
		JPanel p = line(l, r, 22);
		p.setBorder(Ui.pad(4, 0, 2, 0));
		return p;
	}

	/** A stack with a coloured 2px rule on its left edge. */
	public static class Ruled extends Stack
	{
		private final Color rule;

		public Ruled(Color rule)
		{
			this.rule = rule;
			setBorder(Ui.pad(4, 10, 6, 0));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			g.setColor(rule);
			g.fillRect(0, 0, 2, getHeight());
		}
	}

	/** Filled card with an accent rule, for things waiting on the user. */
	public static class Callout extends Stack
	{
		public Callout()
		{
			setBorder(Ui.pad(6, 9, 6, 7));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(Palette.ACCENT_BG);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(Palette.ACCENT);
			g.fillRect(0, 0, 2, getHeight());
			super.paintComponent(g);
		}
	}

	/** Progress toward the review threshold. */
	public static class PressureBar extends JComponent
	{
		private final double fraction;
		private final Color fill;

		public PressureBar(double fraction, Color fill)
		{
			this.fraction = Math.max(0, Math.min(1, fraction));
			this.fill = fill;
			setPreferredSize(new Dimension(10, 4));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(Palette.BAR_TRACK);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(fill);
			g.fillRect(0, 0, (int) Math.round(getWidth() * fraction), getHeight());
		}
	}

	/** Rank icon from the game when available, otherwise a drawn pip. */
	public static class RankBadge extends JComponent
	{
		private final BufferedImage image;

		public RankBadge(BufferedImage image)
		{
			this.image = image;
			setPreferredSize(new Dimension(13, 13));
			setMaximumSize(new Dimension(13, 13));
			setMinimumSize(new Dimension(13, 13));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			int s = Math.min(getWidth(), getHeight());
			int y = (getHeight() - s) / 2;
			if (image != null)
			{
				g.drawImage(image, 0, y, s, s, null);
				return;
			}
			Graphics2D g2 = Ui.smooth(g);
			g2.setColor(Palette.NEUTRAL);
			int c = s / 2;
			int[] xs = {c, s - 2, c, 2};
			int[] ys = {y + 2, y + c, y + s - 2, y + c};
			g2.fillPolygon(xs, ys, 4);
			g2.dispose();
		}
	}

	/** A row whose background lightens on hover and which opens something on click. */
	public static class HoverRow extends Stack
	{
		private boolean hover;

		public HoverRow(Runnable onClick)
		{
			if (onClick != null)
			{
				Ui.hand(this, onClick);
			}
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
					if (!contains(e.getPoint()))
					{
						hover = false;
						repaint();
					}
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			if (hover)
			{
				g.setColor(Palette.ROW_HOVER);
				g.fillRect(0, 0, getWidth(), getHeight());
			}
			super.paintComponent(g);
		}
	}

	/** Centered title and body for empty tabs. */
	public static JComponent empty(String title, String body)
	{
		Stack s = new Stack();
		s.setBorder(Ui.pad(28, 6, 12, 6));
		JLabel t = Ui.label(title, Palette.bold(), Palette.TEXT_HI);
		t.setHorizontalAlignment(JLabel.CENTER);
		s.add(t);
		s.add(Ui.gap(4));
		s.add(new Wrapped(body, Palette.MUTED, true));
		return s;
	}

	/** Multi-line text that wraps to the width it's given. */
	public static class Wrapped extends JComponent implements WidthAware
	{
		private final String text;
		private final Color color;
		private final boolean center;

		public Wrapped(String text, Color color, boolean center)
		{
			this.text = text;
			this.color = color;
			this.center = center;
			setFont(Palette.small());
		}

		private java.util.List<String> lines(int width)
		{
			FontMetrics fm = getFontMetrics(getFont());
			java.util.List<String> out = new java.util.ArrayList<>();
			StringBuilder cur = new StringBuilder();
			for (String word : text.split(" "))
			{
				String trial = cur.length() == 0 ? word : cur + " " + word;
				if (fm.stringWidth(trial) > width && cur.length() > 0)
				{
					out.add(cur.toString());
					cur = new StringBuilder(word);
				}
				else
				{
					cur = new StringBuilder(trial);
				}
			}
			if (cur.length() > 0)
			{
				out.add(cur.toString());
			}
			return out;
		}

		@Override
		public int heightFor(int width)
		{
			Insets in = getInsets();
			FontMetrics fm = getFontMetrics(getFont());
			return lines(Math.max(20, width - in.left - in.right)).size() * fm.getHeight() + in.top + in.bottom;
		}

		@Override
		public Dimension getPreferredSize()
		{
			int w = getWidth() > 0 ? getWidth() : 200;
			return new Dimension(w, heightFor(w));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Insets in = getInsets();
			FontMetrics fm = g.getFontMetrics(getFont());
			g.setFont(getFont());
			g.setColor(color);
			int w = getWidth() - in.left - in.right;
			int y = in.top + fm.getAscent();
			for (String l : lines(w))
			{
				int x = in.left + (center ? (w - fm.stringWidth(l)) / 2 : 0);
				g.drawString(l, x, y);
				y += fm.getHeight();
			}
		}
	}
}
