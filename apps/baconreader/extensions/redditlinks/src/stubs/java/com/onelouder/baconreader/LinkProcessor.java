// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader;

import android.content.Context;
import android.content.Intent;
import com.onelouder.baconreader.reddit.Link;

public class LinkProcessor {
    public LinkProcessor(Context context, String url) {}

    public static boolean isReddit(String url) {
        return false;
    }

    public static boolean isValidRedditGallery(Link link) {
        return false;
    }

    public Intent getIntent() {
        return null;
    }
}
