package com.clanranktracker.ui;

import com.clanranktracker.data.EventType;
import com.clanranktracker.data.RankEvent;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** History tab: filter chips, then a timeline rail of every change, newest first. */
final class HistoryView
{
	static final int PAGE = 60;

	enum Filter
	{
		ALL("All", e -> true),
		RANKS("Ranks", e -> e.type == EventType.PROMOTE || e.type == EventType.DEMOTE),
		MEMBERSHIP("Joins", e -> e.type == EventType.JOIN || e.type == EventType.LEAVE),
		RENAMES("Renames", e -> e.type == EventType.RENAME);

		final String label;
		final Predicate<RankEvent> test;

		Filter(String label, Predicate<RankEvent> test)
		{
			this.label = label;
			this.test = test;
		}
	}

	private HistoryView()
	{
	}

	static JComponent build(RankTrackerPanel panel, ViewModel vm)
	{
		Stack s = new Stack();
		ChipFlow filters = new ChipFlow();
		for (Filter f : Filter.values())
		{
			filters.add(new Chip(f.label, panel.ui().filter == f ? Chip.Style.SELECTED : Chip.Style.NORMAL, () ->
			{
				panel.ui().filter = f;
				panel.ui().historyLimit = PAGE;
				panel.rebuild();
			}));
		}
		s.add(filters);
		s.add(Ui.gap(10));

		List<RankEvent> list = new ArrayList<>();
		for (int i = vm.state.events.size() - 1; i >= 0; i--)
		{
			RankEvent e = vm.state.events.get(i);
			if (panel.ui().filter.test.test(e))
			{
				list.add(e);
			}
		}

		if (list.isEmpty())
		{
			if (vm.state.events.isEmpty())
			{
				s.add(Parts.empty("History starts now", "Promotions, demotions, joins, leaves and renames show up here as they happen."));
			}
			else
			{
				s.add(Parts.empty("Nothing here yet", "No " + panel.ui().filter.label.toLowerCase() + " recorded so far."));
			}
			return s;
		}

		int limit = Math.min(list.size(), panel.ui().historyLimit);
		for (int i = 0; i < limit; i++)
		{
			s.add(row(panel, vm, list.get(i), i == 0, i == limit - 1, true));
		}

		s.add(Ui.gap(8));
		JComponent older = list.size() > limit
			? Ui.action("Show older", Palette.MUTED, () ->
			{
				panel.ui().historyLimit += PAGE;
				panel.rebuild();
			})
			: Ui.transparent();
		s.add(Parts.line(older, Ui.action("Export CSV", Palette.MUTED, () -> panel.controller().exportCsv(panel)), 16));
		return s;
	}

	static JComponent row(RankTrackerPanel panel, ViewModel vm, RankEvent e, boolean first, boolean last, boolean allowUndo)
	{
		Font f = Palette.small();
		boolean pending = e.pendingId != null;
		String tag;
		switch (e.type)
		{
			case PROMOTE:
				tag = "promoted";
				break;
			case DEMOTE:
				tag = "demoted";
				break;
			case RENAME:
				tag = "renamed";
				break;
			case JOIN:
				tag = "joined";
				break;
			default:
				tag = "left";
		}
		if (pending)
		{
			tag += " (maybe rename)";
		}

		JPanel title = new JPanel();
		title.setOpaque(false);
		title.setLayout(new BoxLayout(title, BoxLayout.X_AXIS));
		title.add(Ui.label(e.name, f, e.type == EventType.LEAVE ? Palette.NEUTRAL : Palette.TEXT_HI));
		title.add(Box.createHorizontalStrut(4));
		title.add(Ui.label(tag, f, pending ? Palette.MUTED : TimelineRow.colorFor(e.type)));

		String dot = Palette.dot(f);
		String detail;
		switch (e.type)
		{
			case PROMOTE:
			case DEMOTE:
				detail = vm.title(e.fromRank) + Palette.arrow(f) + vm.title(e.toRank);
				break;
			case RENAME:
				detail = "was " + e.oldName;
				break;
			case JOIN:
				detail = "as " + vm.title(e.toRank);
				break;
			default:
				detail = "was " + vm.title(e.fromRank);
		}
		detail += dot + (e.approximate ? "noticed " : "") + vm.ago(e.at);

		JComponent action = allowUndo && e.type == EventType.RENAME && e.undoOld != null
			? Ui.action("Undo", Palette.MUTED, () -> panel.controller().undoRename(e.id))
			: null;
		TimelineRow row = new TimelineRow(e.type, pending, title, detail, action, first, last);
		String tip = vm.date(e.at);
		if (e.approximate)
		{
			tip += ". Happened while the plugin wasn't running, so this is when it was noticed.";
		}
		if (e.type == EventType.RENAME && e.reason != null)
		{
			tip += " Linked by: " + e.reason.label() + ".";
		}
		row.setToolTipText(tip);
		return row;
	}
}
