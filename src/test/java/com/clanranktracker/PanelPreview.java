package com.clanranktracker;

import com.clanranktracker.core.ReviewMath;
import com.clanranktracker.core.Tracker;
import com.clanranktracker.data.ClanState;
import com.clanranktracker.data.Names;
import com.clanranktracker.data.RankInfo;
import com.clanranktracker.data.RosterEntry;
import com.clanranktracker.ui.NavIcon;
import com.clanranktracker.ui.PanelController;
import com.clanranktracker.ui.RankTrackerPanel;
import com.clanranktracker.ui.ViewModel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * Renders the real panel offscreen with made-up data, so the UI can be checked against the
 * design without logging in. Test-only; not part of the plugin.
 */
public class PanelPreview
{
	private static final long DAY = ReviewMath.DAY_MS;
	private static final long NOW = LocalDate.of(2026, 11, 24).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 15 * 3600_000L;

	public static void main(String[] args) throws Exception
	{
		File out = new File(args.length > 0 ? args[0] : "build/preview");
		out.mkdirs();

		ClanState state = fixture();
		RankTrackerPanel[] holder = new RankTrackerPanel[1];
		PanelController stub = new PanelController()
		{
			public void markReviewed(String name)
			{
			}

			public boolean setRankSince(String name, long epochMillis)
			{
				return true;
			}

			public void setReviewPeriod(int rank, com.clanranktracker.data.ReviewPeriod period)
			{
			}

			public void confirmRename(long pendingId)
			{
			}

			public void rejectRename(long pendingId)
			{
			}

			public void undoRename(long eventId)
			{
			}

			public void exportCsv(Component parent)
			{
			}

			public BufferedImage rankIcon(RankInfo rank)
			{
				return null;
			}
		};

		onEdt(() -> holder[0] = new RankTrackerPanel(stub));
		RankTrackerPanel p = holder[0];
		ViewModel vm = ViewModel.ready(state, new com.clanranktracker.data.ReviewPeriod(30, com.clanranktracker.data.ReviewPeriod.Unit.DAYS), true, 75, NOW, ZoneOffset.UTC);

		shot(p, vm, out, "1-due", x -> { });
		shot(p, vm, out, "2-members", x -> click(x, "MEMBERS"));
		shot(p, vm, out, "3-history", x -> click(x, "HISTORY"));
		shot(p, vm, out, "4-detail", x -> x.openMember(Names.key("Fally Kn1ght")));
		shot(p, vm, out, "5-detail-due", x -> x.openMember(Names.key("Iron Mallard")));
		shot(p, vm, out, "6-period-editor", x -> x.editPeriod(20, new com.clanranktracker.data.ReviewPeriod(3, com.clanranktracker.data.ReviewPeriod.Unit.MONTHS)));

		ClanState fresh = new ClanState();
		fresh.clanName = "Bones Clan";
		new Tracker(fresh).apply(List.of(new RosterEntry("Jiimbones", 126, 19000L), new RosterEntry("Gnome Child", 10, 20400L)),
			List.of(new RankInfo(126, 0, "Owner"), new RankInfo(10, 1, "Recruit")), Collections.emptyMap(), NOW);
		ViewModel freshVm = ViewModel.ready(fresh, new com.clanranktracker.data.ReviewPeriod(30, com.clanranktracker.data.ReviewPeriod.Unit.DAYS), true, 75, NOW, ZoneOffset.UTC);
		shot(p, freshVm, out, "7-fresh-due", x -> click(x, "DUE"));
		shot(p, freshVm, out, "8-fresh-history", x -> click(x, "HISTORY"));

		ImageIO.write(NavIcon.draw(48), "png", new File(out, "icon-48.png"));
		ImageIO.write(NavIcon.draw(16), "png", new File(out, "icon-16.png"));
		System.out.println("Wrote previews to " + out.getAbsolutePath());
		System.exit(0);
	}

	private static void click(RankTrackerPanel p, String tab)
	{
		p.showTab(RankTrackerPanel.Tab.valueOf(tab));
	}

	private static void shot(RankTrackerPanel p, ViewModel vm, File dir, String name, Consumer<RankTrackerPanel> before) throws Exception
	{
		onEdt(() ->
		{
			p.render(vm);
			before.accept(p);
		});
		onEdt(() -> { });
		int w = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;
		int h = 720;
		BufferedImage img = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
		onEdt(() ->
		{
			p.setSize(w, h);
			for (int i = 0; i < 3; i++)
			{
				layout(p);
			}
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.scale(2, 2);
			p.paint(g);
			g.dispose();
		});
		ImageIO.write(img, "png", new File(dir, name + ".png"));
	}

	private static void layout(Component c)
	{
		if (c instanceof Container)
		{
			Container k = (Container) c;
			k.doLayout();
			for (Component ch : k.getComponents())
			{
				layout(ch);
			}
		}
	}

	private static void onEdt(Runnable r) throws Exception
	{
		SwingUtilities.invokeAndWait(r);
	}

	private static long day(int ago)
	{
		return NOW - ago * DAY;
	}

	/** A plausible clan: 48 members, a few months of history, one rename waiting. */
	static ClanState fixture()
	{
		ClanState s = new ClanState();
		s.clanName = "Bones Clan";
		Tracker t = new Tracker(s);
		List<RankInfo> ranks = List.of(
			new RankInfo(126, 0, "Owner"), new RankInfo(125, 1, "Deputy owner"), new RankInfo(100, 2, "Administrator"),
			new RankInfo(30, 3, "Sergeant"), new RankInfo(20, 4, "Corporal"), new RankInfo(10, 5, "Recruit"));

		List<RosterEntry> r = new ArrayList<>();
		r.add(new RosterEntry("Jiimbones", 126, 19000L));
		r.add(new RosterEntry("Draynor Dee", 125, 19100L));
		r.add(new RosterEntry("Ardy Al", 30, 19300L));
		r.add(new RosterEntry("Iron Mallard", 30, 19400L));
		r.add(new RosterEntry("Varrock V", 30, 19420L));
		r.add(new RosterEntry("Fally Knight", 30, 19500L));
		r.add(new RosterEntry("Zulrah Snack", 20, 19600L));
		r.add(new RosterEntry("Gnome Child", 10, 20000L));
		r.add(new RosterEntry("B0wfa Bob", 10, 20100L));
		r.add(new RosterEntry("Old Timer", 30, 18000L));
		r.add(new RosterEntry("Seers Sam", 100, 19200L));
		String[] corporals = {"Karamja Kat", "Rimmy Ron", "Taverley T", "Burthorpe B", "Catherby C", "Yanille Y", "Canifis Cal", "Morty Mo"};
		for (int i = 0; i < corporals.length; i++)
		{
			r.add(new RosterEntry(corporals[i], 20, 19700L + i * 20));
		}
		String[] recruits = {"Lumby Lass", "Edge Ed", "Al Kharid A", "Port Sarim P", "Rellekka R", "Brimhaven Bri", "Shilo Shi", "Nardah N",
			"Pollnivneach", "Sophanem S", "Menaphos M", "Prif Pri", "Lletya L", "Hosidius H", "Arceuus Arc", "Lovakengj L",
			"Piscarilius P", "Shayzien S", "Kourend K", "Varlamore V", "Civitas C", "Aldarin A", "Sunset S", "Fortis F", "Tal Teklan", "Quetzal Q", "Hunter H", "Mistrock M"};
		for (int i = 0; i < recruits.length; i++)
		{
			r.add(new RosterEntry(recruits[i], 10, 20200L + i * 5));
		}
		t.apply(r, ranks, Collections.emptyMap(), day(60));

		t.setRankSince("Iron Mallard", day(94));
		t.setRankSince("Zulrah Snack", day(71));
		t.setRankSince("Varrock V", day(12));
		t.setRankSince("Ardy Al", day(5));
		for (int i = 0; i < corporals.length; i++)
		{
			t.setRankSince(corporals[i], day(3 + i * 2));
		}
		for (int i = 0; i < recruits.length; i++)
		{
			t.setRankSince(recruits[i], day(Math.min(27, 2 + i)));
		}

		List<RosterEntry> r2 = new ArrayList<>(r);
		r2.removeIf(e -> e.name.equals("Old Timer"));
		t.apply(r2, ranks, Collections.emptyMap(), day(30));

		replace(r2, "Lumby Lass", new RosterEntry("Lumby Lass", 10, 20200L));
		t.apply(r2, ranks, Collections.emptyMap(), day(27));

		replace(r2, "B0wfa Bob", new RosterEntry("B0wfa Bob", 10, 20100L));
		t.setRankSince("B0wfa Bob", day(29));

		replace(r2, "Ardy Al", new RosterEntry("Ardy Al", 20, 19300L));
		t.apply(r2, ranks, Collections.emptyMap(), day(5));

		replace(r2, "Fally Knight", new RosterEntry("Fally Kn1ght", 30, 19500L));
		t.apply(r2, ranks, Collections.emptyMap(), day(2));

		replace(r2, "Zulrah Snack", new RosterEntry("Zulrah Snack", 20, 19600L));
		replace(r2, "Gnome Child", new RosterEntry("Gnome Child", 20, 20000L));
		t.apply(r2, ranks, Collections.emptyMap(), NOW - 3600_000L);

		replace(r2, "Canifis Cal", new RosterEntry("Canifis Callum", 30, 19700L + 6 * 20));
		t.apply(r2, ranks, Collections.emptyMap(), NOW - 600_000L);

		t.setRankSince("Gnome Child", NOW - 3600_000L);
		t.markReviewed("Varrock V", day(1));
		return t.read(x -> x);
	}

	private static void replace(List<RosterEntry> list, String name, RosterEntry with)
	{
		for (int i = 0; i < list.size(); i++)
		{
			if (list.get(i).name.equals(name))
			{
				list.set(i, with);
				return;
			}
		}
	}
}
