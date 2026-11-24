package com.clanranktracker.ui;

import com.clanranktracker.data.MemberRecord;
import com.clanranktracker.data.Names;
import com.clanranktracker.data.RankEvent;
import com.clanranktracker.data.RankInfo;
import com.clanranktracker.data.ReviewPeriod;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** One member: rank, review clock, dates, previous names and their own history. */
final class DetailView
{
	private DetailView()
	{
	}

	static JComponent build(RankTrackerPanel panel, ViewModel vm, String key)
	{
		MemberRecord m = vm.state.members.get(key);
		Stack s = new Stack();
		if (m == null)
		{
			return s;
		}
		Font f = Palette.small();

		s.add(Parts.line(back(panel), null, 16));
		s.add(Ui.gap(8));

		s.add(Parts.line(Ui.label(m.name, Palette.bold(), Palette.TEXT_HI), null, 18));
		JPanel rank = new JPanel();
		rank.setOpaque(false);
		rank.setLayout(new BoxLayout(rank, BoxLayout.X_AXIS));
		RankInfo ri = vm.state.ranks.get(m.rank);
		rank.add(new Parts.RankBadge(ri == null ? null : panel.controller().rankIcon(ri)));
		rank.add(Box.createHorizontalStrut(5));
		rank.add(Ui.label(vm.title(m.rank), f, Palette.TEXT));
		s.add(Parts.line(rank, null, 16));
		s.add(Ui.gap(10));

		ReviewPeriod p = vm.period(m.rank);
		Stack stats = new Stack();
		stats.add(stat(vm.isEstimated(m) ? "In rank (est.)" : "In rank", inRank(vm, m), Palette.TEXT_HI));
		stats.add(stat("Review every", p.isOff() ? "Never" : p.longLabel(), Palette.TEXT));
		stats.add(stat("Review", review(vm, m, p), reviewColor(vm, m, p)));
		if (!p.isOff())
		{
			Color bar = vm.isDue(m) ? (vm.isLate(m) ? Palette.OVERDUE : Palette.ACCENT) : Palette.BAR_IDLE;
			stats.add(Ui.gap(3));
			stats.add(new Parts.PressureBar(vm.fraction(m), bar));
			stats.add(Ui.gap(5));
		}
		stats.add(stat("Last reviewed", m.lastReviewed == null ? "Never" : vm.date(m.lastReviewed), Palette.TEXT));
		stats.add(stat("Joined clan", m.joinEpochDay == null ? "Unknown" : ViewModel.date(LocalDate.ofEpochDay(m.joinEpochDay)), Palette.TEXT));
		stats.add(stat("Tracked since", vm.date(m.firstSeen), Palette.TEXT));
		s.add(stats);
		s.add(Ui.gap(10));

		s.add(Parts.line(Parts.actions(
			Ui.action("Mark reviewed", Palette.ACCENT, () -> panel.controller().markReviewed(m.name)),
			Ui.action("Set rank date", Palette.MUTED, () ->
			{
				panel.ui().editingRankDate = true;
				panel.rebuild();
			})), null, 16));

		if (panel.ui().editingRankDate)
		{
			s.add(Ui.gap(6));
			s.add(rankDateEditor(panel, vm, m));
		}

		if (!m.previousNames.isEmpty())
		{
			s.add(Ui.gap(12));
			s.add(Parts.section("Previous names", null));
			ChipFlow names = new ChipFlow();
			for (int i = m.previousNames.size() - 1; i >= 0; i--)
			{
				names.add(new Chip(m.previousNames.get(i), Chip.Style.NORMAL, null));
			}
			s.add(names);
		}

		List<RankEvent> mine = eventsFor(vm, m);
		s.add(Ui.gap(12));
		s.add(Parts.section("History", mine.isEmpty() ? null : String.valueOf(mine.size())));
		if (mine.isEmpty())
		{
			s.add(new Parts.Wrapped("No changes recorded for " + m.name + " yet.", Palette.MUTED, false));
		}
		for (int i = 0; i < mine.size(); i++)
		{
			s.add(HistoryView.row(panel, vm, mine.get(i), i == 0, i == mine.size() - 1, true));
		}
		return s;
	}

	private static String inRank(ViewModel vm, MemberRecord m)
	{
		int d = com.clanranktracker.core.ReviewMath.days(vm.rankSince(m), vm.now);
		return span(d) + (m.countsFromTracking() ? "+" : "");
	}

	/** "1 day", "45 days", "5 months", "3 years". */
	static String span(int days)
	{
		if (days < 60)
		{
			return days + (days == 1 ? " day" : " days");
		}
		if (days < 730)
		{
			return (days / 30) + " months";
		}
		return (days / 365) + " years";
	}

	private static String review(ViewModel vm, MemberRecord m, ReviewPeriod p)
	{
		if (p.isOff())
		{
			return "Not reviewed at this rank";
		}
		if (vm.isDemoted(m))
		{
			return "Due, demoted " + vm.ago(m.demotedAt);
		}
		int left = vm.daysUntilDue(m);
		if (left < 0)
		{
			return span(-left) + " overdue";
		}
		if (left == 0)
		{
			return "Due today";
		}
		return "Due in " + span(left);
	}

	private static Color reviewColor(ViewModel vm, MemberRecord m, ReviewPeriod p)
	{
		if (p.isOff())
		{
			return Palette.MUTED;
		}
		if (vm.isDue(m))
		{
			return vm.isLate(m) ? Palette.OVERDUE : Palette.ACCENT;
		}
		return Palette.TEXT;
	}


	private static JComponent stat(String k, String v, Color vc)
	{
		JLabel key = Ui.label(k, Palette.small(), Palette.MUTED);
		JLabel val = Ui.label(v, Palette.small(), vc);
		return Parts.line(key, val, 17);
	}

	private static JComponent rankDateEditor(RankTrackerPanel panel, ViewModel vm, MemberRecord m)
	{
		Stack box = new Stack();
		JLabel err = Ui.label(" ", Palette.small(), Palette.OVERDUE);
		String initial = java.time.Instant.ofEpochMilli(vm.rankSince(m)).atZone(vm.zone).toLocalDate().toString();
		InlineField field = new InlineField(initial, "yyyy-mm-dd", v ->
		{
			LocalDate d;
			try
			{
				d = LocalDate.parse(v);
			}
			catch (DateTimeParseException ex)
			{
				return "Use the format 2026-11-24";
			}
			LocalDate today = java.time.Instant.ofEpochMilli(vm.now).atZone(vm.zone).toLocalDate();
			if (d.isAfter(today))
			{
				return "That date is in the future";
			}
			if (m.joinEpochDay != null && d.toEpochDay() < m.joinEpochDay)
			{
				return "That's before they joined the clan";
			}
			long millis = d.atStartOfDay(vm.zone == null ? ZoneId.systemDefault() : vm.zone).toInstant().toEpochMilli();
			panel.ui().editingRankDate = false;
			if (!panel.controller().setRankSince(m.name, millis))
			{
				return "This member isn't in the clan any more";
			}
			return null;
		}, () ->
		{
			panel.ui().editingRankDate = false;
			panel.rebuild();
		}, msg -> err.setText(msg == null ? " " : msg));
		field.setPreferredSize(new Dimension(10, 22));
		javax.swing.SwingUtilities.invokeLater(field::requestFocusInWindow);
		box.add(new Parts.Wrapped("When did " + m.name + " reach " + vm.title(m.rank) + "? Enter to save, Esc to cancel.", Palette.MUTED, false));
		box.add(Ui.gap(4));
		box.add(field);
		box.add(Parts.line(err, null, 15));
		return box;
	}

	private static List<RankEvent> eventsFor(ViewModel vm, MemberRecord m)
	{
		Set<String> keys = new HashSet<>();
		keys.add(Names.key(m.name));
		for (String p : m.previousNames)
		{
			keys.add(Names.key(p));
		}
		List<RankEvent> out = new ArrayList<>();
		for (int i = vm.state.events.size() - 1; i >= 0; i--)
		{
			RankEvent e = vm.state.events.get(i);
			if (keys.contains(Names.key(e.name)) || (e.oldName != null && keys.contains(Names.key(e.oldName))))
			{
				out.add(e);
			}
		}
		return out;
	}

	private static JComponent back(RankTrackerPanel panel)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		JComponent chevron = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = Ui.smooth(g);
				g2.setColor(Palette.MUTED);
				int cy = getHeight() / 2;
				g2.drawLine(5, cy - 3, 2, cy);
				g2.drawLine(2, cy, 5, cy + 3);
				g2.dispose();
			}
		};
		chevron.setPreferredSize(new Dimension(9, 14));
		chevron.setMaximumSize(new Dimension(9, 14));
		p.add(chevron);
		p.add(Ui.action("Back to " + (panel.ui().tab == RankTrackerPanel.Tab.DUE ? "due list" : panel.ui().tab == RankTrackerPanel.Tab.HISTORY ? "history" : "members"),
			Palette.MUTED, panel::closeMember));
		return p;
	}
}
