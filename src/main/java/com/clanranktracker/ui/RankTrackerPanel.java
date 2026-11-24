package com.clanranktracker.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/** The side panel: a header, three tabs, and a member detail view. */
public class RankTrackerPanel extends PluginPanel
{
	static final String DEVELOPER_NAME = "Jiimbones";
	static final String DONATION_URL = "https://www.paypal.com/ncp/payment/FQTFP8N8H7P2W";

	public enum Tab
	{
		DUE,
		MEMBERS,
		HISTORY
	}

	/** View state that survives re-renders. Swing thread only. */
	static final class UiState
	{
		Tab tab = Tab.DUE;
		String search = "";
		final Set<Integer> expanded = new HashSet<>();
		HistoryView.Filter filter = HistoryView.Filter.ALL;
		int historyLimit = HistoryView.PAGE;
		/** Key of the member whose detail is open, or null. */
		String detail;
		Integer editingThreshold;
		com.clanranktracker.data.ReviewPeriod.Unit editingUnit = com.clanranktracker.data.ReviewPeriod.Unit.DAYS;
		String editingAmount = "";
		boolean editingRankDate;
		boolean showAllSoon;
	}

	private final PanelController controller;
	private final UiState uiState = new UiState();
	private final Header header = new Header();
	private final TabStrip tabs = new TabStrip();
	private final JScrollPane scroll;
	private ViewModel vm = ViewModel.of(ViewModel.Status.LOGGED_OUT);
	private Tab lastBuiltTab;
	private String lastBuiltDetail;

	public RankTrackerPanel(PanelController controller)
	{
		super(false);
		this.controller = controller;
		setLayout(new BorderLayout());
		setBackground(Palette.BG);

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(header, BorderLayout.NORTH);
		top.add(tabs, BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);

		scroll = new JScrollPane();
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getViewport().setBackground(Palette.BG);
		scroll.setBackground(Palette.BG);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
		add(footer(), BorderLayout.SOUTH);
		rebuild();
	}

	/** Swing thread only. */
	public void render(ViewModel next)
	{
		vm = next;
		if (uiState.detail != null && (vm.state == null || !vm.state.members.containsKey(uiState.detail)))
		{
			uiState.detail = null;
		}
		rebuild();
	}

	public void showTab(Tab t)
	{
		uiState.tab = t;
		uiState.detail = null;
		uiState.editingThreshold = null;
		uiState.editingRankDate = false;
		rebuild();
	}

	/** Opens the review period editor for a rank in the Members tab. */
	public void editPeriod(int rank, com.clanranktracker.data.ReviewPeriod current)
	{
		uiState.tab = Tab.MEMBERS;
		uiState.detail = null;
		uiState.editingThreshold = rank;
		uiState.editingUnit = current.unit;
		uiState.editingAmount = String.valueOf(current.amount);
		rebuild();
	}

	public void openMember(String key)
	{
		uiState.detail = key;
		uiState.editingRankDate = false;
		rebuild();
	}

	void closeMember()
	{
		uiState.detail = null;
		uiState.editingRankDate = false;
		rebuild();
	}

	PanelController controller()
	{
		return controller;
	}

	UiState ui()
	{
		return uiState;
	}

	ViewModel vm()
	{
		return vm;
	}

	void rebuild()
	{
		header.repaint();
		tabs.repaint();
		boolean sameView = uiState.tab == lastBuiltTab && java.util.Objects.equals(uiState.detail, lastBuiltDetail);
		int keepY = sameView ? scroll.getViewport().getViewPosition().y : 0;

		JComponent body;
		if (vm.status != ViewModel.Status.READY)
		{
			body = statusView();
		}
		else if (uiState.detail != null)
		{
			body = DetailView.build(this, vm, uiState.detail);
		}
		else
		{
			switch (uiState.tab)
			{
				case MEMBERS:
					body = MembersView.build(this, vm);
					break;
				case HISTORY:
					body = HistoryView.build(this, vm);
					break;
				default:
					body = DueView.build(this, vm);
			}
		}
		lastBuiltTab = uiState.tab;
		lastBuiltDetail = uiState.detail;

		JPanel holder = new Stack();
		holder.setOpaque(true);
		holder.setBackground(Palette.BG);
		holder.setBorder(Ui.pad(Palette.PAD, Palette.PAD, Palette.PAD, Palette.PAD));
		holder.add(body);
		scroll.setViewportView(holder);
		revalidate();
		repaint();
		final int y = keepY;
		SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new java.awt.Point(0, Math.min(y, Math.max(0, holder.getHeight() - scroll.getViewport().getHeight())))));
	}

	/** Pinned to the bottom: credit and a quiet donate link. */
	private static JComponent footer()
	{
		JPanel f = new JPanel(new BorderLayout())
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				g.setColor(Palette.HEADER);
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(Palette.LINE);
				g.fillRect(0, 0, getWidth(), 1);
			}
		};
		f.setOpaque(false);
		f.setBorder(Ui.pad(6, Palette.PAD, 6, Palette.PAD));
		f.add(Ui.label("Developed by " + DEVELOPER_NAME, Palette.small(), Palette.MUTED), BorderLayout.WEST);
		javax.swing.JLabel donate = Ui.action("Donate" + Palette.glyph(Palette.small(), " ♥", ""), Palette.ACCENT,
			() -> net.runelite.client.util.LinkBrowser.browse(DONATION_URL));
		donate.setToolTipText("Support development (opens PayPal)");
		f.add(donate, BorderLayout.EAST);
		return f;
	}

	private JComponent statusView()
	{
		switch (vm.status)
		{
			case NO_CLAN:
				return Parts.empty("No clan found", "Join a clan, or open the clan tab once so the game sends the member list.");
			case LOADING:
				return Parts.empty("Loading history", "Reading this clan's saved history.");
			default:
				return Parts.empty("Log in to start", "Rank tracking starts once you're logged in to an account in a clan.");
		}
	}

	private int dueCount()
	{
		return vm.status == ViewModel.Status.READY ? vm.due().size() : 0;
	}

	private final class Header extends JComponent
	{
		Header()
		{
			setPreferredSize(new Dimension(10, 30));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(Palette.HEADER);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(Palette.LINE);
			g.fillRect(0, getHeight() - 1, getWidth(), 1);

			String right = "";
			String name = "Clan Rank Tracker";
			if (vm.status == ViewModel.Status.READY && vm.state != null)
			{
				name = vm.state.clanName;
				int n = vm.state.members.size();
				right = n + (n == 1 ? " member" : " members");
			}
			g.setFont(Palette.small());
			FontMetrics sm = g.getFontMetrics();
			int rw = sm.stringWidth(right);
			int baseline = (getHeight() - sm.getHeight()) / 2 + sm.getAscent();
			g.setColor(Palette.MUTED);
			g.drawString(right, getWidth() - Palette.PAD - rw, baseline);

			g.setFont(Palette.bold());
			FontMetrics bm = g.getFontMetrics();
			g.setColor(Palette.TEXT_HI);
			int nameBase = (getHeight() - bm.getHeight()) / 2 + bm.getAscent();
			g.drawString(Ui.fit(name, bm, getWidth() - Palette.PAD * 3 - rw), Palette.PAD, nameBase);
		}
	}

	private final class TabStrip extends JComponent
	{
		private int hover = -1;

		TabStrip()
		{
			setPreferredSize(new Dimension(10, 26));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			MouseAdapter ma = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					int h = index(e.getX());
					if (h != hover)
					{
						hover = h;
						repaint();
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = -1;
					repaint();
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (vm.status == ViewModel.Status.READY)
					{
						showTab(Tab.values()[index(e.getX())]);
					}
				}
			};
			addMouseListener(ma);
			addMouseMotionListener(ma);
		}

		private int index(int x)
		{
			return Math.max(0, Math.min(2, x * 3 / Math.max(1, getWidth())));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(Palette.HEADER);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setFont(Palette.small());
			FontMetrics fm = g.getFontMetrics();
			int due = dueCount();
			String[] labels = {"Due", "Members", "History"};
			boolean ready = vm.status == ViewModel.Status.READY;
			for (int i = 0; i < 3; i++)
			{
				int x0 = getWidth() * i / 3;
				int x1 = getWidth() * (i + 1) / 3;
				boolean active = ready && uiState.tab.ordinal() == i;
				g.setColor(active ? Palette.ACCENT : Palette.LINE);
				g.fillRect(x0, getHeight() - 2, x1 - x0, 2);

				String label = labels[i];
				String count = i == 0 && due > 0 ? " " + due : "";
				int w = fm.stringWidth(label + count);
				int x = x0 + (x1 - x0 - w) / 2;
				int y = (getHeight() - 2 - fm.getHeight()) / 2 + fm.getAscent();
				g.setColor(!ready ? Palette.LINE_STRONG : active || hover == i ? Palette.TEXT_HI : Palette.MUTED);
				g.drawString(label, x, y);
				if (!count.isEmpty())
				{
					g.setColor(ready ? Palette.ACCENT : Palette.LINE_STRONG);
					g.drawString(count, x + fm.stringWidth(label), y);
				}
			}
		}
	}
}
