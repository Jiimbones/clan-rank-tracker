package com.clanranktracker.ui;

import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.Names;
import com.clanranktracker.data.PendingRename;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Due tab: possible renames on top, then pressure bars for who needs a review. */
final class DueView
{
	private static final int MAX_CALLOUTS = 3;
	private static final int MAX_SOON = 5;

	private DueView()
	{
	}

	static JComponent build(RankTrackerPanel panel, ViewModel vm)
	{
		Stack s = new Stack();

		List<PendingRename> pending = vm.state.pending;
		for (int i = 0; i < Math.min(MAX_CALLOUTS, pending.size()); i++)
		{
			s.add(callout(panel, vm, pending.get(i)));
			s.add(Ui.gap(6));
		}
		if (pending.size() > MAX_CALLOUTS)
		{
			JLabel more = Ui.label("+" + (pending.size() - MAX_CALLOUTS) + " more possible renames", Palette.small(), Palette.MUTED);
			s.add(Parts.line(more, null, 16));
			s.add(Ui.gap(6));
		}

		List<MemberRecord> due = vm.due();
		List<MemberRecord> soon = vm.soon();

		boolean anyFromJoin = vm.state.members.values().stream().anyMatch(MemberRecord::countsFromJoin);
		boolean anyFromTracking = vm.state.members.values().stream().anyMatch(MemberRecord::countsFromTracking);
		if (due.isEmpty() && soon.isEmpty())
		{
			s.add(Parts.empty("No one's due for review",
				"Members show up here once they've spent their rank's review time. Change it per rank in the Members tab."));
		}
		if (!due.isEmpty())
		{
			s.add(Parts.section("Due for review", String.valueOf(due.size())));
			for (MemberRecord m : due)
			{
				s.add(row(panel, vm, m, true));
			}
		}
		if (!soon.isEmpty())
		{
			if (!due.isEmpty())
			{
				s.add(Ui.gap(6));
			}
			s.add(Parts.section("Coming up", String.valueOf(soon.size())));
			boolean all = panel.ui().showAllSoon;
			int limit = all ? soon.size() : Math.min(MAX_SOON, soon.size());
			for (int i = 0; i < limit; i++)
			{
				s.add(row(panel, vm, soon.get(i), false));
			}
			if (soon.size() > MAX_SOON)
			{
				s.add(Ui.gap(4));
				s.add(Parts.line(Ui.action(all ? "Show fewer" : "Show " + (soon.size() - MAX_SOON) + " more", Palette.MUTED, () ->
				{
					panel.ui().showAllSoon = !all;
					panel.rebuild();
				}), null, 16));
			}
		}

		if (anyFromJoin || anyFromTracking)
		{
			StringBuilder note = new StringBuilder("Until a rank change is seen, rank dates are estimated from each member's join date, assuming one promotion per review period.");
			if (anyFromTracking)
			{
				note.append(" Times marked + have no join date and count from ").append(vm.date(vm.state.baselineAt)).append('.');
			}
			note.append(" Set a rank date on a member if you know it.");
			s.add(Ui.gap(10));
			s.add(new Parts.Wrapped(note.toString(), Palette.MUTED, false));
		}
		return s;
	}

	private static JComponent row(RankTrackerPanel panel, ViewModel vm, MemberRecord m, boolean due)
	{
		Color tone;
		Color bar;
		if (due)
		{
			tone = vm.isLate(m) ? Palette.OVERDUE : Palette.ACCENT;
			bar = tone;
		}
		else
		{
			tone = Palette.NEUTRAL;
			bar = Palette.BAR_SOON;
		}

		String key = Names.key(m.name);
		Parts.HoverRow r = new Parts.HoverRow(() -> panel.openMember(key));
		r.setBorder(Ui.pad(5, 4, 5, 4));

		JLabel name = Ui.label(m.name, Palette.small(), due ? Palette.TEXT_HI : Palette.TEXT);
		JLabel count = Ui.label(vm.progress(m), Palette.small(), tone);
		r.add(Parts.line(name, count, 15));
		r.add(Ui.gap(2));
		r.add(new Parts.PressureBar(vm.fraction(m), bar));
		r.add(Ui.gap(2));

		JLabel rank = Ui.label(vm.title(m.rank), Palette.small(), Palette.MUTED);
		JComponent action = due ? Ui.action("Mark reviewed", Palette.MUTED, () -> panel.controller().markReviewed(m.name)) : null;
		r.add(Parts.line(rank, action, 15));
		return r;
	}

	private static JComponent callout(RankTrackerPanel panel, ViewModel vm, PendingRename p)
	{
		Parts.Callout c = new Parts.Callout();
		Font f = Palette.small();
		c.add(Parts.line(Ui.label("Possible rename", f, Palette.ACCENT), Ui.label(vm.ago(p.at), f, Palette.MUTED), 15));
		c.add(Parts.line(Ui.label(p.oldName + Palette.arrow(f) + p.newName, f, Palette.TEXT_HI), null, 15));

		String ranks = p.oldRank == p.newRank
			? vm.title(p.newRank)
			: vm.title(p.oldRank) + Palette.arrow(f) + vm.title(p.newRank);
		c.add(new Parts.Wrapped(ranks + Palette.dot(f) + p.why, Palette.MUTED, false));
		c.add(Ui.gap(3));

		JPanel actions = Parts.actions(
			Ui.action("Reject", Palette.MUTED, () -> panel.controller().rejectRename(p.id)),
			Ui.action("Confirm", Palette.PROMOTE, () -> panel.controller().confirmRename(p.id)));
		c.add(Parts.line(Ui.transparent(), actions, 15));
		return c;
	}
}
