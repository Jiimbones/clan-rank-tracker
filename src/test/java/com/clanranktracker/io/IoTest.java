package com.clanranktracker.io;

import com.clanranktracker.core.Tracker;
import com.clanranktracker.data.ClanState;
import com.clanranktracker.data.RosterEntry;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import net.runelite.client.util.Filepath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IoTest
{
	private final Gson gson = new Gson();

	private static String fixture(String name) throws IOException
	{
		try (InputStream in = IoTest.class.getResourceAsStream("/wom/" + name))
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	public void womParsesApprovedOnly() throws IOException
	{
		List<String[]> c = WomNameClient.parse(gson, fixture("names.json"));
		assertEquals(2, c.size());
		assertTrue(WomNameClient.matches(c, "Fally Knight", "Fally Kn1ght"));
		assertTrue(WomNameClient.matches(c, "fally_knight", "FALLY KN1GHT"));
	}

	@Test
	public void womFollowsChains() throws IOException
	{
		List<String[]> c = WomNameClient.parse(gson, fixture("chain.json"));
		assertTrue(WomNameClient.matches(c, "First", "Third"));
		assertFalse(WomNameClient.matches(c, "Third", "First"));
	}

	@Test
	public void womNoMatchAndJunk() throws IOException
	{
		List<String[]> c = WomNameClient.parse(gson, fixture("names.json"));
		assertFalse(WomNameClient.matches(c, "Someone", "Fally Kn1ght"));
		assertTrue(WomNameClient.parse(gson, "{\"message\":\"Player not found\"}").isEmpty());
		assertFalse(WomNameClient.matches(List.of(), "a", "b"));
	}

	@Test
	public void storeRoundTrip() throws IOException
	{
		Path dir = Files.createTempDirectory("crt");
		RankStore store = new RankStore(Filepath.Unchecked.getRooted(dir), gson);
		Tracker t = new Tracker(store.load("Bones Clan"));
		t.apply(List.of(new RosterEntry("A", 10, 5L), new RosterEntry("B", 20, null)), null, Collections.emptyMap(), 1000L);
		t.apply(List.of(new RosterEntry("A", 20, 5L)), null, Collections.emptyMap(), 2000L);
		t.setReviewPeriod(10, new com.clanranktracker.data.ReviewPeriod(6, com.clanranktracker.data.ReviewPeriod.Unit.MONTHS));
		store.save("Bones Clan", t.read(store::toJson));

		ClanState back = store.load("Bones Clan");
		assertTrue(back.baselineTaken);
		assertEquals(1, back.members.size());
		assertEquals(20, back.members.get("a").rank);
		assertEquals(Long.valueOf(5L), back.members.get("a").joinEpochDay);
		assertEquals(2, back.events.size());
		assertEquals(6, back.reviewPeriods.get(10).amount);
		assertEquals(com.clanranktracker.data.ReviewPeriod.Unit.MONTHS, back.reviewPeriods.get(10).unit);
		assertTrue(back.departed.containsKey("b"));
		assertTrue(back.nextId > back.events.get(1).id);
	}

	@Test
	public void corruptFileStartsFreshAndIsKept() throws IOException
	{
		Path dir = Files.createTempDirectory("crt");
		Files.writeString(dir.resolve(RankStore.fileName("Bones Clan")), "{not json");
		RankStore store = new RankStore(Filepath.Unchecked.getRooted(dir), gson);
		ClanState s = store.load("Bones Clan");
		assertFalse(s.baselineTaken);
		try (var files = Files.list(dir))
		{
			assertTrue(files.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")));
		}
	}

	@Test
	public void fileNamesAreSafe()
	{
		assertEquals("bones_clan.json", RankStore.fileName("Bones Clan"));
		assertEquals("clan.json", RankStore.fileName("../.."));
		assertEquals("clan.json", RankStore.fileName(null));
	}

	@Test
	public void csvEscapesAndDefusesFormulas()
	{
		assertEquals("\"a,b\"", CsvExporter.cell("a,b"));
		assertEquals("'=SUM(A1)", CsvExporter.cell("=SUM(A1)"));
		ClanState s = new ClanState();
		Tracker t = new Tracker(s);
		t.apply(List.of(new RosterEntry("A", 10, 5L)), null, Collections.emptyMap(), 0L);
		t.apply(List.of(new RosterEntry("A", 20, 5L)), null, Collections.emptyMap(), 60_000L);
		String csv = t.read(x -> CsvExporter.history(x, ZoneOffset.UTC));
		assertTrue(csv.startsWith("time,event"));
		assertTrue(csv.contains("1970-01-01 00:01,promote,A,,Rank 10,Rank 20,no,"));
	}
}
