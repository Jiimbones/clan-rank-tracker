package com.clanranktracker.ui;

import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.Names;
import com.clanranktracker.data.RankInfo;
import com.clanranktracker.data.ReviewPeriod;
import java.awt.Dimension;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Members tab: search, then the clan as a rank ladder of chips. */
final class MembersView
{
	private static final int COLLAPSED_CHIPS = 18;
	/** Never collapse just to hide one or two names. */
	private static final int MIN_HIDDEN = 3;

	private MembersView()
	{
	}

	static JComponent build(RankTrackerPanel panel, ViewModel vm)
	{
		Stack s = new Stack();
		Stack sections = new Stack();

		InlineField search = new InlineField(panel.ui().search, "Search members", v -> null, null, null);
		search.setPreferredSize(new Dimension(10, 22));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			private void changed()
			{
				panel.ui().search = search.getText().trim();
				fill(panel, vm, sections);
				sections.revalidate();
				sections.repaint();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				changed();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				changed();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				changed();
			}
		});
		s.add(search);
		s.add(Ui.gap(10));
		s.add(sections);
		fill(panel, vm, sections);
		return s;
	}

	private static void fill(RankTrackerPanel panel, ViewModel vm, Stack into)
	{
		into.removeAll();
		String q = panel.ui().search.toLowerCase(Locale.ROOT);
		boolean searching = !q.isEmpty();
		int shown = 0;

		for (Map.Entry<Integer, List<MemberRecord>> e : vm.byRank().entrySet())
		{
			int rank = e.getKey();
			List<MemberRecord> all = e.getValue();
			List<MemberRecord> list = all;
			if (searching)
			{
				list = new java.util.ArrayList<>();
				for (MemberRecord m : all)
				{
					if (matches(m, q))
					{
						list.add(m);
					}
				}
				if (list.isEmpty())
				{
					continue;
				}
			}
			shown += list.size();
			boolean anyDue = list.stream().anyMatch(vm::isDue);
			Parts.Ruled sec = new Parts.Ruled(anyDue ? Palette.ACCENT : Palette.LINE_STRONG);
			sec.add(header(panel, vm, rank, all.size()));
			if (panel.ui().editingThreshold != null && panel.ui().editingThreshold == rank)
			{
				sec.add(Ui.gap(4));
				sec.add(periodEditor(panel, vm, rank));
			}
			sec.add(Ui.gap(5));

			ChipFlow chips = new ChipFlow();
			boolean expanded = searching || panel.ui().expanded.contains(rank);
			int limit = expanded || list.size() <= COLLAPSED_CHIPS + MIN_HIDDEN ? Integer.MAX_VALUE : COLLAPSED_CHIPS;
			for (int i = 0; i < list.size() && i < limit; i++)
			{
				MemberRecord m = list.get(i);
				String key = Names.key(m.name);
				Chip c = new Chip(m.name, vm.isDue(m) ? Chip.Style.ATTENTION : Chip.Style.NORMAL, () -> panel.openMember(key));
				c.setToolTipText(m.name + Palette.dot(Palette.small()) + vm.progress(m) + (vm.isEstimated(m) ? " (estimated)" : ""));
				chips.add(c);
			}
			if (list.size() > limit)
			{
				chips.add(new Chip("+" + (list.size() - limit), Chip.Style.QUIET, () ->
				{
					panel.ui().expanded.add(rank);
					panel.rebuild();
				}));
			}
			else if (!searching && panel.ui().expanded.contains(rank) && list.size() > COLLAPSED_CHIPS + MIN_HIDDEN)
			{
				chips.add(new Chip("Show less", Chip.Style.QUIET, () ->
				{
					panel.ui().expanded.remove(rank);
					panel.rebuild();
				}));
			}
			sec.add(chips);
			into.add(sec);
			into.add(Ui.gap(8));
		}

		if (searching && shown == 0)
		{
			into.add(Parts.empty("No matches", "No current member matches \"" + panel.ui().search + "\". Previous names are searched too."));
		}
	}

	private static boolean matches(MemberRecord m, String q)
	{
		if (m.name.toLowerCase(Locale.ROOT).contains(q))
		{
			return true;
		}
		for (String p : m.previousNames)
		{
			if (p.toLowerCase(Locale.ROOT).contains(q))
			{
				return true;
			}
		}
		return false;
	}

	private static JComponent header(RankTrackerPanel panel, ViewModel vm, int rank, int count)
	{
		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
		RankInfo ri = vm.state.ranks.get(rank);
		left.add(new Parts.RankBadge(ri == null ? null : panel.controller().rankIcon(ri)));
		left.add(javax.swing.Box.createHorizontalStrut(5));
		left.add(Ui.label(vm.title(rank), Palette.small(), Palette.TEXT_HI));
		left.add(javax.swing.Box.createHorizontalStrut(5));
		left.add(Ui.label(String.valueOf(count), Palette.small(), Palette.MUTED));

		JComponent right;
		ReviewPeriod p = vm.period(rank);
		if (panel.ui().editingThreshold != null && panel.ui().editingThreshold == rank)
		{
			right = Ui.action("Cancel", Palette.MUTED, () ->
			{
				panel.ui().editingThreshold = null;
				panel.rebuild();
			});
		}
		else
		{
			JLabel a = Ui.action(p.isOff() ? "No review" : "Every " + p.shortLabel(), Palette.MUTED, () -> panel.editPeriod(rank, p));
			a.setToolTipText(vm.isTopByDefault(rank)
				? vm.title(rank) + " is your top rank, so it has no review by default. Click to set one."
				: "Change how long members stay at " + vm.title(rank) + " before a review");
			right = a;
		}
		return Parts.line(left, right, 16);
	}

	/** Amount field plus a days / months / years switch. Enter saves, Esc cancels. */
	private static JComponent periodEditor(RankTrackerPanel panel, ViewModel vm, int rank)
	{
		RankTrackerPanel.UiState ui = panel.ui();
		Stack box = new Stack();
		JLabel err = Ui.label(" ", Palette.small(), Palette.OVERDUE);

		InlineField f = new InlineField(ui.editingAmount, "0", v ->
		{
			int amount;
			try
			{
				amount = Integer.parseInt(v);
			}
			catch (NumberFormatException ex)
			{
				return "Enter a whole number";
			}
			int max = ui.editingUnit == ReviewPeriod.Unit.DAYS ? 3650 : ui.editingUnit == ReviewPeriod.Unit.MONTHS ? 120 : 10;
			if (amount < 0 || amount > max)
			{
				return "Use 0 to " + max + " " + ui.editingUnit.plural;
			}
			ui.editingThreshold = null;
			panel.controller().setReviewPeriod(rank, new ReviewPeriod(amount, ui.editingUnit));
			return null;
		}, () ->
		{
			ui.editingThreshold = null;
			panel.rebuild();
		}, msg -> err.setText(msg == null ? " " : msg));
		f.getDocument().addDocumentListener(new DocumentListener()
		{
			private void sync()
			{
				ui.editingAmount = f.getText();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				sync();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				sync();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				sync();
			}
		});
		f.setPreferredSize(new Dimension(40, 18));
		f.setMaximumSize(new Dimension(40, 18));

		JPanel row = new JPanel();
		row.setOpaque(false);
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.add(f);
		for (ReviewPeriod.Unit u : ReviewPeriod.Unit.values())
		{
			row.add(javax.swing.Box.createHorizontalStrut(4));
			row.add(new Chip(u.toString(), u == ui.editingUnit ? Chip.Style.SELECTED : Chip.Style.NORMAL, () ->
			{
				ui.editingUnit = u;
				panel.rebuild();
			}));
		}
		row.add(javax.swing.Box.createHorizontalGlue());
		box.add(Parts.line(row, null, 18));
		box.add(Ui.gap(3));
		box.add(new Parts.Wrapped("Time at " + vm.title(rank) + " before a review. 0 turns reviews off. Enter to save.", Palette.MUTED, false));
		box.add(Parts.line(err, null, 14));
		javax.swing.SwingUtilities.invokeLater(f::requestFocusInWindow);
		return box;
	}
}
