# PNG Icons — required files

The app no longer uses emoji for buttons, menu tiles and status icons.
Instead it loads PNG files from `app/src/main/res/drawable/`.

## How to add the files

1. Make each PNG a **square**, solid white icon on a transparent background
   (a single color is best because the code can tint it with any theme color).
2. Recommended size: **96x96 px** (Android scales it down for each density;
   if you want perfect sharpness also provide 48/72/96/144/192 px versions in
   `drawable-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi` — but a single 96x96 in
   `drawable/` is enough to build).
3. Save each file with the **exact lowercase name** shown below, directly in
   `app/src/main/res/drawable/`.
4. Build → the app picks them up automatically via `R.drawable.<name>`.

Good sources for white icon PNGs: [fonts.google.com/icons](https://fonts.google.com/icons)
(choose "Download → PNG", color white) or any icon pack.

## Menu tile icons (Player + Admin dashboards)

| File name | Used for |
|---|---|
| `ic_menu_calendar.png` | Schedule / Seasons tile |
| `ic_menu_trophy.png` | Leaderboard tile |
| `ic_menu_chat.png` | Team Chat tile |
| `ic_menu_mic.png` | Voice Chat tile |
| `ic_menu_call.png` | Voice Call tile (admin) |
| `ic_menu_person.png` | My Profile tile + avatar placeholder |
| `ic_menu_friends.png` | Friends tile |
| `ic_menu_medal.png` | Seasons + Achievements tiles |
| `ic_menu_announce.png` | Announcements / Broadcast tile |
| `ic_menu_gift.png` | Daily Rewards tile |
| `ic_menu_fire.png` | Daily Challenges tile |
| `ic_menu_ticket.png` | Battle Pass tile |
| `ic_menu_sparkles.png` | My Titles tile |
| `ic_menu_chart.png` | Performance tile |
| `ic_menu_bell.png` | Notifications / Notify tile + header bell |
| `ic_menu_settings.png` | Settings tile |
| `ic_menu_logout.png` | Logout tile |
| `ic_menu_edit.png` | Daily Input / Edit tile |
| `ic_menu_people.png` | All Stats / Manage players icon |
| `ic_menu_channels.png` | Voice Channels admin tile |
| `ic_menu_crown.png` | Admin crown in top bar |

## Small action icons

| File name | Used for |
|---|---|
| `ic_action_delete.png` | Delete buttons (chat clear, schedule, season, channel) |
| `ic_action_send.png` | Chat send button |
| `ic_action_pause.png` | Disable/pause voice channel |

Total: **24 PNG files**. If any file is missing the build fails with
`Unresolved reference: ic_menu_xxx` — just add the missing PNG and rebuild.

## Tip

One easy way to generate all of them: download the Material Symbols you like
as white PNGs and rename them to the names above. Only the file name matters —
any square icon works.
