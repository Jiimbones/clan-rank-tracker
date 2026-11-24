package com.clanranktracker.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.JPanel;

/** Left-to-right chips that wrap, with a height that follows the available width. */
public class ChipFlow extends JPanel implements WidthAware
{
	private static final int H_GAP = 4;
	private static final int V_GAP = 4;

	public ChipFlow()
	{
		setOpaque(false);
		setLayout(new LayoutManager()
		{
			@Override
			public void addLayoutComponent(String name, Component comp)
			{
			}

			@Override
			public void removeLayoutComponent(Component comp)
			{
			}

			@Override
			public Dimension preferredLayoutSize(Container parent)
			{
				int w = parent.getWidth() > 0 ? parent.getWidth() : 200;
				return new Dimension(w, heightFor(w));
			}

			@Override
			public Dimension minimumLayoutSize(Container parent)
			{
				return preferredLayoutSize(parent);
			}

			@Override
			public void layoutContainer(Container parent)
			{
				place(parent.getWidth(), true);
			}
		});
	}

	@Override
	public int heightFor(int width)
	{
		return place(width, false);
	}

	private int place(int width, boolean apply)
	{
		Insets in = getInsets();
		int max = width - in.left - in.right;
		int x = 0;
		int y = 0;
		int rowH = 0;
		for (Component c : getComponents())
		{
			if (!c.isVisible())
			{
				continue;
			}
			Dimension d = c.getPreferredSize();
			int cw = Math.min(d.width, max);
			if (x > 0 && x + cw > max)
			{
				x = 0;
				y += rowH + V_GAP;
				rowH = 0;
			}
			if (apply)
			{
				c.setBounds(in.left + x, in.top + y, cw, d.height);
			}
			x += cw + H_GAP;
			rowH = Math.max(rowH, d.height);
		}
		return in.top + in.bottom + y + rowH;
	}
}
