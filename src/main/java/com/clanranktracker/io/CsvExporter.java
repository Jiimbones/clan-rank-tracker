package com.clanranktracker.io;

import com.clanranktracker.data.ClanState;
import com.clanranktracker.data.RankEvent;
import com.clanranktracker.data.RankInfo;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class CsvExporter
{
	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private CsvExporter()
	{
	}

	public static String history(ClanState s, ZoneId zone)
	{
		StringBuilder sb = new StringBuilder("time,event,name,previous name,from rank,to rank,approximate,link reason\n");
		for (RankEvent e : s.events)
		{
			sb.append(TS.format(Instant.ofEpochMilli(e.at).atZone(zone))).append(',')
				.append(e.type.name().toLowerCase()).append(',')
				.append(cell(e.name)).append(',')
				.append(cell(e.oldName)).append(',')
				.append(cell(title(s, e.fromRank))).append(',')
				.append(cell(title(s, e.toRank))).append(',')
				.append(e.approximate ? "yes" : "no").append(',')
				.append(cell(e.reason == null ? null : e.reason.label()))
				.append('\n');
		}
		return sb.toString();
	}

	static String title(ClanState s, Integer rank)
	{
		if (rank == null)
		{
			return null;
		}
		RankInfo ri = s.ranks.get(rank);
		return ri != null && ri.title != null ? ri.title : "Rank " + rank;
	}

	/** Quotes when needed, and defuses spreadsheet formula injection. */
	static String cell(String v)
	{
		if (v == null)
		{
			return "";
		}
		String out = v;
		if (!out.isEmpty() && "=+-@".indexOf(out.charAt(0)) >= 0)
		{
			out = "'" + out;
		}
		if (out.contains(",") || out.contains("\"") || out.contains("\n"))
		{
			out = "\"" + out.replace("\"", "\"\"") + "\"";
		}
		return out;
	}
}
