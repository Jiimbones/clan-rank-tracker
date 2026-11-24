package com.clanranktracker;

import com.clanranktracker.core.Tracker;
import com.clanranktracker.data.ClanState;
import com.clanranktracker.data.Names;
import com.clanranktracker.data.PendingRename;
import com.clanranktracker.data.RankInfo;
import com.clanranktracker.data.RenameReason;
import com.clanranktracker.data.ReviewPeriod;
import com.clanranktracker.data.RosterEntry;
import com.clanranktracker.io.CsvExporter;
import com.clanranktracker.io.RankStore;
import com.clanranktracker.io.WomNameClient;
import com.clanranktracker.ui.NavIcon;
import com.clanranktracker.ui.PanelController;
import com.clanranktracker.ui.RankTrackerPanel;
import com.clanranktracker.ui.ViewModel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Clan Rank Tracker",
	description = "Rank history, joins, leaves, renames and who is due a rank review",
	tags = {"clan", "rank", "ranks", "promotion", "roster", "members", "history"},
	internalName = "clan-rank-tracker"
)
public class ClanRankTrackerPlugin extends Plugin implements PanelController
{
	/** Check the roster every few ticks; it's a cheap fingerprint unless something changed. */
	private static final int TICKS_BETWEEN_CHECKS = 5;
	/** How long to wait without clan settings after login before saying there's no clan. */
	private static final int NO_CLAN_TICKS = 25;

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClanRankTrackerConfig config;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ChatIconManager chatIconManager;

	private RankTrackerPanel panel;
	private NavigationButton navButton;
	private RankStore store;
	private WomNameClient wom;

	/** Swapped from the executor, read on the client and Swing threads. */
	private volatile Tracker tracker;
	private volatile String trackerClan;
	private volatile boolean loading;
	private volatile ViewModel.Status status = ViewModel.Status.LOGGED_OUT;

	// Client thread only.
	private int tick;
	private volatile int lastFingerprint;
	private int ticksWithoutClan;

	@Override
	protected void startUp() throws IOException
	{
		Filepath dir = getPluginDirectory();
		store = new RankStore(dir, gson);
		wom = new WomNameClient(okHttpClient, gson);
		panel = new RankTrackerPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Clan Rank Tracker")
			.icon(NavIcon.create())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		lastFingerprint = 0;
		ticksWithoutClan = 0;
		status = client.getGameState() == GameState.LOGGED_IN ? ViewModel.Status.LOADING : ViewModel.Status.LOGGED_OUT;
		publishStatus();
	}

	@Override
	protected void shutDown()
	{
		saveNow();
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		tracker = null;
		trackerClan = null;
		loading = false;
	}

	@Provides
	ClanRankTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanRankTrackerConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			saveNow();
			status = ViewModel.Status.LOGGED_OUT;
			publish();
		}
		else if (e.getGameState() == GameState.LOGGED_IN && status == ViewModel.Status.LOGGED_OUT)
		{
			status = ViewModel.Status.LOADING;
			ticksWithoutClan = 0;
			lastFingerprint = 0;
			publish();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (ClanRankTrackerConfig.GROUP.equals(e.getGroup()))
		{
			publish();
			if ("womConfirmRenames".equals(e.getKey()) && config.womConfirmRenames())
			{
				checkUnverifiedRenames();
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		if (++tick % TICKS_BETWEEN_CHECKS != 0 || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		ClanSettings cs = client.getClanSettings();
		if (cs == null || cs.getName() == null || cs.getMembers() == null || cs.getMembers().isEmpty())
		{
			ticksWithoutClan += TICKS_BETWEEN_CHECKS;
			if (ticksWithoutClan >= NO_CLAN_TICKS && status != ViewModel.Status.NO_CLAN)
			{
				status = ViewModel.Status.NO_CLAN;
				publish();
			}
			return;
		}
		ticksWithoutClan = 0;

		String clan = cs.getName();
		if (!clan.equals(trackerClan))
		{
			startLoading(clan);
			return;
		}
		Tracker t = tracker;
		if (t == null)
		{
			return;
		}
		if (status != ViewModel.Status.READY)
		{
			status = ViewModel.Status.READY;
			publish();
		}

		long now = System.currentTimeMillis();
		List<ClanMember> members = cs.getMembers();
		int fp = fingerprint(members);
		if (fp == lastFingerprint)
		{
			t.touch(now);
			return;
		}

		List<RosterEntry> roster = new ArrayList<>(members.size());
		Map<Integer, RankInfo> ranks = new LinkedHashMap<>();
		for (ClanMember m : members)
		{
			if (m == null || m.getName() == null || m.getRank() == null)
			{
				continue;
			}
			int rank = m.getRank().getRank();
			LocalDate jd = m.getJoinDate();
			roster.add(new RosterEntry(m.getName(), rank, jd == null ? null : jd.toEpochDay()));
			if (!ranks.containsKey(rank))
			{
				ClanTitle title = cs.titleForRank(m.getRank());
				ranks.put(rank, new RankInfo(rank, title == null ? RankInfo.NO_TITLE : title.getId(), title == null ? null : title.getName()));
			}
		}

		Tracker.ApplyResult r = t.apply(roster, ranks.values(), friendPreviousNames(), now);
		lastFingerprint = fp;
		if (r.changed)
		{
			if (r.baseline)
			{
				log.debug("Clan Rank Tracker baseline taken for {} ({} members)", clan, roster.size());
			}
			persistAndPublish();
			if (config.womConfirmRenames())
			{
				for (PendingRename p : r.newPending)
				{
					checkWithWom(t, p);
				}
			}
		}
	}

	private void startLoading(String clan)
	{
		if (loading)
		{
			return;
		}
		saveNow();
		loading = true;
		tracker = null;
		status = ViewModel.Status.LOADING;
		publish();
		executor.execute(() ->
		{
			ClanState s = store.load(clan);
			tracker = new Tracker(s);
			trackerClan = clan;
			loading = false;
			lastFingerprint = 0;
			if (config.womConfirmRenames())
			{
				checkUnverifiedRenames();
			}
		});
	}

	private static int fingerprint(List<ClanMember> members)
	{
		int h = members.size();
		for (ClanMember m : members)
		{
			if (m == null)
			{
				continue;
			}
			h = 31 * h + (m.getName() == null ? 0 : Names.key(m.getName()).hashCode());
			h = 31 * h + (m.getRank() == null ? 0 : m.getRank().getRank());
			h = 31 * h + (m.getJoinDate() == null ? 0 : (int) m.getJoinDate().toEpochDay());
		}
		return h == 0 ? 1 : h;
	}

	/** New name key to previous name key, for friends who have renamed. */
	private Map<String, String> friendPreviousNames()
	{
		Map<String, String> out = new HashMap<>();
		FriendContainer fc = client.getFriendContainer();
		if (fc == null || fc.getMembers() == null)
		{
			return out;
		}
		for (Friend f : fc.getMembers())
		{
			if (f != null && f.getName() != null && f.getPrevName() != null && !f.getPrevName().isEmpty())
			{
				out.put(Names.key(f.getName()), Names.key(f.getPrevName()));
			}
		}
		return out;
	}

	private void checkUnverifiedRenames()
	{
		Tracker t = tracker;
		if (t == null)
		{
			return;
		}
		List<PendingRename> todo = t.read(s ->
		{
			List<PendingRename> l = new ArrayList<>();
			for (PendingRename p : s.pending)
			{
				if (!p.womChecked)
				{
					l.add(p);
				}
			}
			return l;
		});
		for (PendingRename p : todo)
		{
			checkWithWom(t, p);
		}
	}

	private void checkWithWom(Tracker t, PendingRename p)
	{
		wom.check(p.oldName, p.newName, verdict ->
		{
			if (tracker != t)
			{
				return;
			}
			if (verdict == WomNameClient.Verdict.CONFIRMED)
			{
				if (t.confirmRename(p.id, RenameReason.WISE_OLD_MAN))
				{
					persistAndPublish();
				}
			}
			else if (verdict == WomNameClient.Verdict.NOT_FOUND)
			{
				t.markWomChecked(p.id);
			}
		});
	}

	/** Serialises under the tracker lock, then saves and redraws off the client thread. */
	private void persistAndPublish()
	{
		Tracker t = tracker;
		String clan = trackerClan;
		if (t == null || clan == null)
		{
			return;
		}
		String json = t.read(store::toJson);
		executor.execute(() ->
		{
			store.save(clan, json);
			ClanState copy = gson.fromJson(json, ClanState.class).repair();
			render(copy);
		});
	}

	private void saveNow()
	{
		Tracker t = tracker;
		String clan = trackerClan;
		if (t == null || clan == null || store == null)
		{
			return;
		}
		String json = t.read(store::toJson);
		executor.execute(() -> store.save(clan, json));
	}

	/** Redraws with the latest state, or with the status screen. */
	private void publish()
	{
		Tracker t = tracker;
		if (status == ViewModel.Status.READY && t != null)
		{
			String json = t.read(store::toJson);
			executor.execute(() -> render(gson.fromJson(json, ClanState.class).repair()));
		}
		else
		{
			publishStatus();
		}
	}

	private void publishStatus()
	{
		ViewModel vm = ViewModel.of(status);
		RankTrackerPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.render(vm));
		}
	}

	private void render(ClanState copy)
	{
		ViewModel vm = ViewModel.ready(copy, new ReviewPeriod(config.defaultReviewAmount(), config.defaultReviewUnit()), config.staffNeverDue(), config.comingUpPercent(),
			System.currentTimeMillis(), ZoneId.systemDefault());
		RankTrackerPanel p = panel;
		if (p != null && status == ViewModel.Status.READY)
		{
			SwingUtilities.invokeLater(() -> p.render(vm));
		}
	}

	// PanelController, called on the Swing thread.

	@Override
	public void markReviewed(String name)
	{
		Tracker t = tracker;
		if (t != null && t.markReviewed(name, System.currentTimeMillis()))
		{
			persistAndPublish();
		}
	}

	@Override
	public boolean setRankSince(String name, long epochMillis)
	{
		Tracker t = tracker;
		if (t != null && t.setRankSince(name, epochMillis))
		{
			persistAndPublish();
			return true;
		}
		return false;
	}

	@Override
	public void setReviewPeriod(int rank, ReviewPeriod period)
	{
		Tracker t = tracker;
		if (t != null)
		{
			t.setReviewPeriod(rank, period);
			persistAndPublish();
		}
	}

	@Override
	public void confirmRename(long pendingId)
	{
		Tracker t = tracker;
		if (t != null)
		{
			t.confirmRename(pendingId, RenameReason.MANUAL);
			persistAndPublish();
		}
	}

	@Override
	public void rejectRename(long pendingId)
	{
		Tracker t = tracker;
		if (t != null && t.rejectRename(pendingId))
		{
			persistAndPublish();
		}
	}

	@Override
	public void undoRename(long eventId)
	{
		Tracker t = tracker;
		if (t != null && t.undoRename(eventId))
		{
			persistAndPublish();
		}
	}

	@Override
	public void exportCsv(Component parent)
	{
		Tracker t = tracker;
		String clan = trackerClan;
		if (t == null || clan == null)
		{
			return;
		}
		List<Filepath> files = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Export rank history")
			.addExtensionFilter("CSV file", "csv")
			.setDefaultExtension("csv")
			.setFileName(RankStore.fileName(clan).replace(".json", "") + "-rank-history.csv")
			.showDialog(parent);
		if (files == null || files.isEmpty())
		{
			return;
		}
		String csv = t.read(s -> CsvExporter.history(s, ZoneId.systemDefault()));
		Filepath target = files.get(0);
		executor.execute(() ->
		{
			try
			{
				target.write(csv);
			}
			catch (IOException ex)
			{
				log.warn("Couldn't export rank history", ex);
			}
		});
	}

	@Override
	public BufferedImage rankIcon(RankInfo rank)
	{
		if (rank == null || rank.titleId == RankInfo.NO_TITLE)
		{
			return null;
		}
		try
		{
			return chatIconManager.getRankImage(new ClanTitle(rank.titleId, rank.title));
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}
}
