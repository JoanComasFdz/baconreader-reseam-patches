// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.posts

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.slideshow.bindings.FixSlideShow
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.postMediaText

// The Slide Show View shows each post in a page of fixed height, which BaconReader works out from
// the display's size. On newer Android versions that size is the whole screen, status and
// navigation bars included, so the page runs on behind the bottom bar: the end of an image post's
// text, a gallery's thumbnails and the page's border don't show, and the media sits low. An image
// post's text, expanded, also grows past the page's bottom, where it is cut off.
val fixSlideShow =
    patch(BaconReaderStrings.patchFixSlideShow) {
        description(BaconReaderStrings.patchFixSlideShowDescription)
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            // The scroll view the text goes in is made in code: this id names it, so the e2e tests
            // find it.
            resources.addId("baconreader_patches_post_text_scroll")
            pageLayoutParams.after {
                returnValue(FixSlideShow.pageLayoutParams(thisObject, result))
            }
            postMediaText.before { FixSlideShow.scrollText(capture("textView")) }
        }
    }

private val pageLayoutParams =
    klass("com.onelouder.baconreader.adapters.PortraitPageLayout").method("getLayoutParams")
