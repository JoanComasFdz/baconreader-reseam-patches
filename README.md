# BaconReader patches for Reseam

BaconReader patches for [Reseam](https://reseam.app), ported from ReVanced. Built from the [Reseam patch bundle template](https://git.reseam.app/reseam/patches-template) for Reseam 0.28.0.

| Patch | What it does |
|---|---|
| Spoof client | Logs in to Reddit with another app's client ID, since Reddit no longer accepts BaconReader's own. Defaults to RedReader's client ID, redirect URI and user agent, like ReVanced. Also rewrites `ssl.reddit.com` URLs to `www.reddit.com`. |
| Fix Redgifs API | Replaces BaconReader's Redgifs interceptor with one that fetches the temporary tokens the Redgifs API now requires. |
| Fix list thumbnails | Fixes empty thumbnails in the post list: unescapes the signed preview URLs Reddit sends and tries again when loading fails. Link posts get a thumbnail from Reddit's preview, the embed or YouTube, crossposted galleries the preview of their first image and BaconReader's gallery indicator, and RedGIFs posts theirs from the RedGIFs API. NSFW posts (with "Show NSFW images" off) and spoilers get a blurred thumbnail on Android 12 and later. |
| Show tables | Draws tables in comments and posts as a grid instead of BaconReader's "header: value" lines. Tables wider than the text get an Expand button, and then scroll sideways. Links in cells open as anywhere else in the text. |
| Text post icon | Replaces the button that opens a text post's text in the post list, which looks like a video's play button, with a page icon and a chevron that flips when the text is open. In the list and card views. |
| Fix Imgur API | Calls Imgur's own API instead of the RapidAPI proxy BaconReader used, which refuses every request, so Imgur albums and galleries load again. Also sends image uploads in comments and posts with a content type, which Imgur's API requires. |
| Embed comment images | Shows images, GIFs, Imgur albums and emotes in comments and post text as images, plays videos uploaded in comments and video links in place with BaconReader's ExoPlayer, shows bare YouTube links with the channel, title and a thumbnail, and shows bare X and Bluesky links as the post. Depends on Fix Imgur API, for albums. |
| Improve post media loading | Shows a dimmed still preview with a progress bar and the downloaded size while post media loads, and a red bar when loading fails. GIFs load through BaconReader's own cache loader, which reports the progress. Galleries use small previews for their thumbnail strip, and download the image shown with a progress bar. A deleted or missing RedGIFs video shows a message instead of RedGIFs' broken web page. Settings decide which link post pages run JavaScript. |
| Patches entry in settings | Adds "Patches" at the end of BaconReader's settings menu, which opens the patches' settings. |
| Prompt to open Reddit links | Since Android 12, an app can't turn on its own links, and reddit.com only verifies Reddit's app. While no Reddit link opens in BaconReader, asks once, on the main screen, whether to open Android's "Open by default" screen to select them. |
| Fix lost post votes | Keeps a vote made on a post while its comments load. The copy of the post that comes with the comments was requested before the vote, and replaced the voted post in the post list. |

The patches are pinned to version 6.1.4, BaconReader's last release, of `com.onelouder.baconreader` and `com.onelouder.baconreader.premium`. Only Premium has been tested. To try another version anyway, pass `--ignore-versions`.

## Settings

Patches with settings add them to one settings screen, made by Reseam's app runtime. Open it from BaconReader's settings → Patches. Its pages are Images and videos, Links and Comments; a page shows once a selected patch has settings on it. The screen also has an about card and exports and imports the settings.

Errors of the patches show in a dialog over the current screen, with the details to copy or save to Downloads.

## Embed comment images settings

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
## Improve post media loading settings

On the Links page, under Link posts:

| Setting | Key | Default |
|---|---|---|
| JavaScript in link posts | `post_media.link_preview_javascript` | On: every site runs JavaScript, except the blocked ones. Off: only the allowed ones |
| Blocked sites | `post_media.link_preview_javascript_blocked` | Empty |
| Allowed sites | `post_media.link_preview_javascript_allowed` | BaconReader's own list without the sites that shut down, plus Bluesky and x.com |

Sites go one per line, and include their subdomains.

## Spoof client options

| Key | Default | Empty value |
|---|---|---|
| `clientId` | RedReader's, `yH0aTnJEt6qUgGn835B4vg` | Not allowed |
| `redirectUri` | `redreader://rr_oauth_redir` | Keeps `http://baconreader.com/auth` |
| `userAgent` | `org.quantumbadger.redreader/1.25.1` | Keeps BaconReader's |

To use your own Reddit app, create an "Installed app" at https://www.reddit.com/prefs/apps, then set `clientId` and the `redirectUri` you gave it.

## Differences from ReVanced

- **Redgifs hook.** ReVanced deletes the five instructions that add BaconReader's interceptor and replaces the first `build()` call. This port redirects the `addInterceptor` call instead, so the new interceptor also applies on the method's second `build()` path, the one taken when setting up SSL fails.
- **Redgifs `/info`.** ReVanced's shared Redgifs code fakes the removed `/info` endpoint for Sync. BaconReader never calls it, so the port leaves it out.
- **Imgur base URL.** ReVanced replaces the first instructions of `ImgurV3Constants.getBaseURL` with a constant return. This port replaces the method's body with the same constant return.
- **Embed comment images settings.** ReVanced's Gestures row only shows text. Here the gestures are described in Show images' summary.
- **Embed comment images hooks.** ReVanced saves the comment from the start of `CommentHolder.updateView` and reads its markdown by reflection when the text is set. This port passes the comment's `body` to the extension at the `setText` call itself. ReVanced's reflection on `ExpandableTextView`, `ImagesRetriever`, `ImgurImage` and ExoPlayer is replaced by direct calls, against stubs; only the protected `ImagesRetriever.doInBackground` is still called by reflection. ExoPlayer 2.12.1, the version BaconReader bundles, isn't on Maven Central or Google's Maven, so its classes are stubbed too.
- **User agent.** ReVanced returns early from the two `getUserAgent` methods. This port replaces their bodies with the same constant return.
- **Empty options.** ReVanced skips the redirect URI and user agent when unset. Reseam always applies an option's default, so an empty value skips them here.
- **List thumbnails.** ReVanced replaces the thumbnail's `into` call and the `isGallery` reads instruction by instruction. This port returns from `loadThumbnail` just before the app's `into` call, and overwrites the value read from `isGallery` right after the read. ReVanced reads the posts' fields by reflection; this port reads them through stubs, and passes the post itself where ReVanced passes the parameter BaconReader overwrites with the URL of Imgur posts.
- **Post media.** ReVanced's extension reads BaconReader's private fields and calls its private methods by reflection. This port passes the fields from the patched code, which also calls `showPage`, `loadToCache` and `handleCurrentItemSaving` itself, and shows the gallery's image page with the view flipper directly. Without that reflection, a failure in them no longer falls back to Glide. ExoPlayer 2.12.1 and PhotoView aren't on Maven Central, so the two ExoPlayer methods and the one PhotoView method the extension calls are stubbed.
- **JavaScript settings.** ReVanced greys out the blocked sites while JavaScript in link posts is off, and the allowed sites while it is on, and changes the switch's summary with it. Here both lists stay editable, and the summaries say when each applies. As in ReVanced, only the list that applies is used.
- **Extension.** ReVanced's Java extension and shared Redgifs code are rewritten in Kotlin. The shared extension init hook isn't needed.
- **Settings screen.** ReVanced adds a "ReVanced" entry to BaconReader's settings and shows its settings in `GoogleApiActivity`, in BaconReader's theme. This port adds a "Patches" entry that opens Reseam's settings screen, which follows the system's light or dark theme. Settings whose summary changed with the switch have one summary here, and settings ReVanced greys out under another stay editable.
- **Error dialog.** ReVanced's dialog has a close icon, and Copy and Save keep it open. This port uses Reseam's branded dialog, where Copy, Save to file and Close each close it. The "Patches" line shows the bundle's version and the Reseam version.
- **Open Reddit links row.** ReVanced adds a row on its Links page that shows how many Reddit links open in BaconReader and opens Android's "Open by default" screen. Reseam's settings screen has no rows that run code, so the port leaves it out; the prompt is the only way in. Whether the prompt was answered is kept in the app's `baconreader_patches` preferences, not as a setting.
- **Links prompt.** ReVanced shows the prompt as a plain Android dialog. The port uses the bundle's branded dialog, with the same buttons; answering or backing out stops it, as in ReVanced.
- **Versions.** ReVanced's patches run on any version. These are pinned, like every patch in Reseam's own bundle.

## Build and test

Needs JDK 17, the Android SDK with `ANDROID_HOME` set, and the [`reseam` CLI 0.28.0](https://reseam.app/download/). Create a signing key once with `reseam bundle keygen --out ~/.reseam/bundle-signing.key`, and keep it out of the repository.

```shell
./gradlew bundle
reseam patch BaconReader.apk \
  --bundle build/reseam/baconreader-patches.reseam \
  --trust <your public key> \
  --output patched.apk
```

Set an option with `--option com.joancomasfdz.patches.baconreader.account.spoofClient.clientId=<id>`.

## Release

`.github/workflows/release.yml`, from the template, runs on `v*` tags. It builds and signs the bundle, then publishes it with its `patches.json` on a GitHub release.

1. Add the signing key as the repository secret `BUNDLE_SIGNING_KEY_B64`: `base64 -w0 ~/.reseam/bundle-signing.key`.
2. Push a tag: `git tag v0.1.0 && git push origin v0.1.0`.

Users add the bundle to Reseam Manager with this link, then check the signing key it shows against the one published here:

```text
https://reseam.app/add/?url=https%3A%2F%2Fgithub.com%2FJoanComasFdz%2Fbaconreader-reseam-patches
```

## Layout and style

[STYLE.md](STYLE.md) has the full guidelines, and [AGENTS.md](AGENTS.md) the checks every change passes. In short, the layout follows Reseam's own bundle: `core/BaconReader.kt` holds the packages and pinned versions, patches sit in one package per area (`account`, `media`), and targets are private values after the patch that uses them. Kotlin is formatted with ktfmt's kotlinlang style, which Reseam uses:

```shell
java -jar ktfmt.jar --kotlinlang-style $(git ls-files 'apps/*.kt' 'apps/*.kts')
```

## License

GPL-3.0-only, like [ReVanced Patches](https://gitlab.com/ReVanced/revanced-patches), which the patches and extension are ported from. The files from the Reseam template stay AGPL-3.0-or-later, with the additional terms in [NOTICE](NOTICE), and the Gradle wrapper is Apache-2.0. [REUSE.toml](REUSE.toml) and each file's SPDX header say which applies, and [LICENSES/](LICENSES) holds the texts.
