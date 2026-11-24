package com.clanranktracker.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * Vertical stack: every child gets the full width and its preferred height for that width.
 * Tracks the viewport width so nothing ever scrolls sideways.
 */
public class Stack extends JPanel implements Scrollable, WidthAware
{
	public Stack()
	{
		super(new Layout());
		setOpaque(false);
	}

	@Override
	public int heightFor(int width)
	{
		Insets in = getInsets();
		int w = width - in.left - in.right;
		int h = in.top + in.bottom;
		for (Component c : getComponents())
		{
			if (c.isVisible())
			{
				h += Layout.heightOf(c, w);
			}
		}
		return h;
	}

	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return 16;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return orientation == SwingConstants.VERTICAL ? visibleRect.height - 32 : visibleRect.width;
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}

	static final class Layout implements LayoutManager
	{
		@Override
		public void addLayoutComponent(String name, Component comp)
		{
		}

		@Override
		public void removeLayoutComponent(Component comp)
		{
		}

		private static int width(Container parent)
		{
			int w = parent.getWidth();
			if (w <= 0 && parent.getParent() != null)
			{
				w = parent.getParent().getWidth();
			}
			if (w <= 0)
			{
				w = net.runelite.client.ui.PluginPanel.PANEL_WIDTH;
			}
			return w;
		}

		private static int heightOf(Component c, int w)
		{
			if (c instanceof WidthAware)
			{
				return ((WidthAware) c).heightFor(w);
			}
			return c.getPreferredSize().height;
		}

		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			int w = width(parent);
			return new Dimension(w, ((Stack) parent).heightFor(w));
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(Container parent)
		{
			Insets in = parent.getInsets();
			int w = parent.getWidth() - in.left - in.right;
			int y = in.top;
			for (Component c : parent.getComponents())
			{
				if (!c.isVisible())
				{
					continue;
				}
				int h = heightOf(c, w);
				c.setBounds(in.left, y, w, h);
				y += h;
			}
		}
	}
}
