# Clan Rank Tracker

Keeps the history the OSRS clan system doesn't: who was promoted or demoted and when, who joined, left or changed name, and who has sat in their rank long enough to be reviewed.

## Tabs

- **Due**: members past their rank's review time, with a bar showing how far over they are. Members close to it appear under *Coming up*. Possible renames that need a decision show at the top.
- **Members**: your clan as a rank ladder. Members who are due are outlined. Click a rank's review time to change it (0 turns reviews off for that rank). Click a member to see their dates, previous names and history, mark them reviewed, or set when they reached their rank.
- **History**: a timeline of promotions, demotions, joins, leaves and renames. You can filter it and export it to CSV.

## How it works

- The plugin reads the clan member list the game already sends to your client. It never changes ranks or sends anything to the game.
- The first time it sees a clan it takes a baseline. Time in rank counts from then until you set real dates.
- Changes made while you're logged out are picked up next time you log in and marked as *noticed*.
- **Renames** are matched by clan join date, which carries over when a player changes name, and by your friends list. A clear match links automatically and can be undone. Anything unsure is queued for you to confirm or reject.
- History is stored locally, one file per clan, in the plugin's data folder.

## Wise Old Man (optional, off by default)

Turn on *Confirm renames with Wise Old Man* to check queued renames against Wise Old Man's name change records. When it's on, the plugin sends the new name of each possible rename to api.wiseoldman.net, and your IP address is visible to that server. Nothing else is sent.

## License

BSD 2-Clause. See `LICENSE`.
