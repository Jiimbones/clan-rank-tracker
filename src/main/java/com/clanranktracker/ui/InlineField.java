package com.clanranktracker.ui;

import java.awt.Graphics;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Function;
import javax.swing.JTextField;

/**
 * A flat text field. Enter commits, Escape cancels. The validator returns an error message,
 * or null when the value was accepted.
 */
public class InlineField extends JTextField
{
	private final String placeholder;
	private boolean error;

	public InlineField(String initial, String placeholder, Function<String, String> commit, Runnable cancel, java.util.function.Consumer<String> onError)
	{
		super(initial);
		this.placeholder = placeholder;
		setFont(Palette.small());
		setForeground(Palette.TEXT_HI);
		setBackground(Palette.CHIP);
		setCaretColor(Palette.TEXT_HI);
		setSelectionColor(Palette.ACCENT);
		setSelectedTextColor(Palette.BG);
		setBorder(Ui.pad(3, 6, 3, 6));
		setOpaque(true);
		addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					String msg = commit.apply(getText().trim());
					error = msg != null;
					if (onError != null)
					{
						onError.accept(msg);
					}
					repaint();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && cancel != null)
				{
					cancel.run();
				}
				else if (error)
				{
					error = false;
					if (onError != null)
					{
						onError.accept(null);
					}
					repaint();
				}
			}
		});
		addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				selectAll();
				repaint();
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				repaint();
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (getText().isEmpty() && placeholder != null)
		{
			g.setColor(Palette.MUTED);
			g.setFont(getFont());
			int y = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
			g.drawString(placeholder, getInsets().left, y);
		}
		if (error || hasFocus())
		{
			g.setColor(error ? Palette.OVERDUE : Palette.ACCENT);
			g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		}
	}
}
