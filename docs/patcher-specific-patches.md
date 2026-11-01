<!-- SPDX-FileCopyrightText: 2026 JoanComasFdz -->
<!-- SPDX-License-Identifier: GPL-3.0-only -->

# Patcher-specific: patches (Reseam)

What the patches in [patches.md](patches.md) do differently in Reseam: settings and their keys, options, and differences from ReVanced.

## ReVanced source

Ported from [ReVanced Patches](https://gitlab.com/ReVanced/revanced-patches): the `dev` branch at commit `c9583e2`, and the BaconReader merge requests 6989 and 6998 at commit `7486631`. See [NOTICE](../NOTICE).

## Reseam-only patches

| Patch | What it does |
|---|---|
| Patches entry in settings | Adds "Patches" at the end of BaconReader's settings menu, which opens the patches' settings. Shows the patches' version at the bottom of the drawer, under the menu, such as "Patches 0.1.1 (Reseam 0.28.0)", to tell which build is installed; a bundle built without a release tag shows "unknown". |

To patch a version other than 6.1.4, pass `--ignore-versions`.

## Settings screen

Patches with settings add them to one settings screen, made by Reseam's app runtime. Open it from BaconReader's settings → Patches. Its pages are Images and videos, Links, Comments and Chat; a page shows once a selected patch has settings on it. The screen also has an about card and exports and imports the settings.

Errors of the patches show in a dialog over the current screen, with the details to copy or save to Downloads. That includes any error that escapes a patch's code, on any thread: the dialog shows it and the app stays open. A crash that doesn't come from the patches still closes BaconReader, as it would without them, and its details show in the dialog when the app opens again.

### Embed comment images settings

| Page | Setting | Key | Default |
|---|---|---|---|
| Images and videos | Show images | `embed_comment_images.show_images` | On |
| Images and videos | Full width by default | `embed_comment_images.images_full_width` | Off |
| Images and videos | Long press to download | `embed_comment_images.images_long_press_download` | On |
| Images and videos | Load images only on Wi-Fi | `embed_comment_images.images_wifi_only` | Off |
| Images and videos | Play videos in comments | `embed_comment_images.show_videos` | On |
| Links | Show YouTube video titles | `embed_comment_images.youtube_titles` | On |
| Links | Show YouTube thumbnails | `embed_comment_images.youtube_thumbnails` | Off |
| Links | Show X posts | `embed_comment_images.x_posts` | On |
| Links | Show Bluesky posts | `embed_comment_images.bluesky_posts` | On |

Tap an image to switch between full width and 200×200, double tap it to open it in full screen, and long press it to save it to Downloads/BaconReader.

### Improve post media loading settings

On the Links page, under Link posts:

| Setting | Key | Default |
|---|---|---|
| JavaScript in link posts | `post_media.link_preview_javascript` | On: every site runs JavaScript, except the blocked ones. Off: only the allowed ones |
| Blocked sites | `post_media.link_preview_javascript_blocked` | Empty |
| Allowed sites | `post_media.link_preview_javascript_allowed` | BaconReader's own list without the sites that shut down, plus Bluesky and x.com |

Sites go one per line, and include their subdomains.

### Video autoplay in detail view settings

On the Images and videos page, in the Detail view section:

| Setting | Key | Default |
|---|---|---|
| Autoplay videos in Detail View | `detail_video.autoplay_videos_in_detail_view` | On |

Off, the detail view of a post shows the post's preview image (the largest of Reddit's preview resolutions) with a play button in place of a video shown by ExoPlayer (v.redd.it, and the MP4 and GIFV videos of Imgur and RedGIFs), and doesn't load the video. A tap on it gives the video back to BaconReader, which shows and plays it as it does with the setting on. The detail view is the one whose `PostMediaHandler` listener is `DetailLinkFragment$6`: the full-screen photo viewer and the browser keep autoplaying, and so do the videos in comments. A post without a preview image shows a black page with the play button.

### Show user avatars settings

On the Comments page, in the User avatars section:

| Setting | Key | Default |
|---|---|---|
| Show user avatars | `user_avatars.user_avatars` | Off |
| Avatar size: Normal, Medium, Large or Extra large | `user_avatars.user_avatar_size` | Normal (`0`) |
| Avatar background | `user_avatars.user_avatar_background` | On |

### Load more comments settings

On the Comments page, in the Load more comments section:

| Setting | Key | Default |
|---|---|---|
| Comments to load: a number from 200 to 500 | `load_more_comments.comment_limit` | `500` |
| Auto-load "load more comments": Off, or up to 1 to 5 replies | `load_more_comments.auto_load_more_comments` | Up to 2 replies (`2`) |

### Reddit chat settings

On the Chat page, in the Reddit chat section:

| Setting | Key | Default |
|---|---|---|
| Chat messages apart from the inbox | `reddit_chat.check_chat_messages` | On |

## Spoof client options

| Key | Default | Empty value |
|---|---|---|
| `clientId` | RedReader's, `yH0aTnJEt6qUgGn835B4vg` | Not allowed |
| `redirectUri` | `redreader://rr_oauth_redir` | Keeps `http://baconreader.com/auth` |
| `userAgent` | `org.quantumbadger.redreader/1.25.1` | Keeps BaconReader's |

To use your own Reddit app, create an "Installed app" at https://www.reddit.com/prefs/apps, then set `clientId` and the `redirectUri` you gave it.

## Differences from ReVanced

- **Versions.** ReVanced's patches run on any version. These are pinned, like every patch in Reseam's own bundle.
- **Extension.** ReVanced's Java extension and shared Redgifs code are rewritten in Kotlin. The shared extension init hook isn't needed.
- **Settings screen.** ReVanced adds a "ReVanced" entry to BaconReader's settings and shows its settings in `GoogleApiActivity`, in BaconReader's theme. This port adds a "Patches" entry that opens Reseam's settings screen, which follows the system's light or dark theme. Settings whose summary changed with the switch have one summary here, and settings ReVanced greys out under another stay editable.
- **Errors that escape a patch.** ReVanced's dialog only shows the errors a patch catches itself, so any other error in a patch closes the app. Here every error from a patch's code shows in the dialog and the app stays open, and other crashes show in it the next time the app opens.
- **Error dialog.** ReVanced's dialog has a close icon, and Copy and Save keep it open. This port uses Reseam's branded dialog, where Copy, Save to file and Close each close it. The "Patches" line shows the bundle's version and the Reseam version.
- **User agent.** ReVanced returns early from the two `getUserAgent` methods. This port replaces their bodies with the same constant return.
- **Empty options.** ReVanced skips the redirect URI and user agent when unset. Reseam always applies an option's default, so an empty value skips them here.
- **Redgifs hook.** ReVanced deletes the five instructions that add BaconReader's interceptor and replaces the first `build()` call. This port redirects the `addInterceptor` call instead, so the new interceptor also applies on the method's second `build()` path, the one taken when setting up SSL fails.
- **Redgifs `/info`.** ReVanced's shared Redgifs code fakes the removed `/info` endpoint for Sync. BaconReader never calls it, so the port leaves it out.
- **List thumbnails.** ReVanced replaces the thumbnail's `into` call and the `isGallery` reads instruction by instruction. This port returns from `loadThumbnail` just before the app's `into` call, and overwrites the value read from `isGallery` right after the read. ReVanced reads the posts' fields by reflection; this port reads them through stubs, and passes the post itself where ReVanced passes the parameter BaconReader overwrites with the URL of Imgur posts.
- **Imgur base URL.** ReVanced replaces the first instructions of `ImgurV3Constants.getBaseURL` with a constant return. This port replaces the method's body with the same constant return.
- **Embed comment images settings.** ReVanced's Gestures row only shows text. Here the gestures are described in Show images' summary.
- **Embed comment images hooks.** ReVanced saves the comment from the start of `CommentHolder.updateView` and reads its markdown by reflection when the text is set. This port passes the comment's `body` to the extension at the `setText` call itself. ReVanced's reflection on `ExpandableTextView`, `ImagesRetriever`, `ImgurImage` and ExoPlayer is replaced by direct calls, against stubs; only the protected `ImagesRetriever.doInBackground` is still called by reflection. ExoPlayer 2.12.1, the version BaconReader bundles, isn't on Maven Central or Google's Maven, so its classes are stubbed too.
- **Post media.** ReVanced's extension reads BaconReader's private fields and calls its private methods by reflection. This port passes the fields from the patched code, which also calls `showPage`, `loadToCache` and `handleCurrentItemSaving` itself, and shows the gallery's image page with the view flipper directly. Without that reflection, a failure in them no longer falls back to Glide. ExoPlayer 2.12.1 and PhotoView aren't on Maven Central, so the two ExoPlayer methods and the one PhotoView method the extension calls are stubbed.
- **JavaScript settings.** ReVanced greys out the blocked sites while JavaScript in link posts is off, and the allowed sites while it is on, and changes the switch's summary with it. Here both lists stay editable, and the summaries say when each applies. As in ReVanced, only the list that applies is used.
- **Open Reddit links row.** ReVanced adds a row on its Links page that shows how many Reddit links open in BaconReader and opens Android's "Open by default" screen. Reseam's settings screen has no rows that run code, so the port leaves it out. The prompt says to use Android's App info → Open by default instead. Whether the prompt was answered is kept in the app's `baconreader_patches` preferences, not as a setting.
- **Links prompt.** ReVanced shows the prompt as a plain Android dialog. The port uses the bundle's branded dialog, with the same buttons; answering or backing out stops it, as in ReVanced.
- **Saved files.** ReVanced reads and writes the service's `completeFile` and the `DocumentFile` methods by reflection. This port passes `completeFile` from the patched code, which writes back the file the extension returns. AndroidX DocumentFile 1.0.0 isn't on Maven Central, so the four methods the extension calls are stubbed.
- **Removed list entries.** For the Random feeds and the "Hide Reddit Awards" settings, ReVanced replaces the `list.add` call with a `nop`. Reseam has no edit that removes a call, so the port skips it with `skipWhen` on a constant `true`, which leaves the call behind a branch that is never taken.
- **Request errors.** ReVanced rebuilds the request task without its `IOException` try block. Reseam has no edit for try blocks, so the port turns the handler into `move-exception` and `throw`: the same error reaches the app, through the handler.
- **User avatars.** ReVanced greys out Avatar size and Avatar background while Show user avatars is off. Here they stay editable and only apply while it's on. Avatar images are downloaded with the user agent `android:com.onelouder.baconreader`, without ReVanced's "(ReVanced)".
- **Comments to load.** ReVanced edits the number in a dialog with a number box and a slider, clamps it to 200–500 when saving, and shows it in the summary. Here it's a plain text setting: the patch clamps the value to 200–500 when a post loads, and uses 500 when it isn't a number. The summary names the range instead of the current number.
- **"Mark unread".** ReVanced removes it from the inbox item's menu just before the menu shows, by its type, through reflection. The port skips the call that adds it to the menu, the same way as the removed list entries above.
- **Videos with sound.** ReVanced reads the service's post and the video's fields by reflection; this port passes the post from the patched code and reads the rest through stubs. The audio download's user agent names the BaconReader patches instead of ReVanced.
- **Tables and accessibility.** ReVanced draws a table on one character of the text, so TalkBack and UI tests see nothing of it. Here each cell and the "↔ Expand" / "Fit to screen" button are virtual views of the text, with their text and where they show, and the button and the cells with a link can be clicked.
- **Links in spoilers.** In ReVanced, a tap on a link in a hidden spoiler opens the link without showing the spoiler: the app's touch handler takes the link first. Here the link waits until the spoiler shows.
- **Hidden spoilers and accessibility.** ReVanced only paints a hidden spoiler over, so TalkBack reads it aloud. Here, while a spoiler is hidden, the text's description reads it as "spoiler".
- **Comment search buttons.** ReVanced's find bar names only its text field and its filter button. Here its arrows and its close button are named too: "Previous match", "Next match" and "Close search".

