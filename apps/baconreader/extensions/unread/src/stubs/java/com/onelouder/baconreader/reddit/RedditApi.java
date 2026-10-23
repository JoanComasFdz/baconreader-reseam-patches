// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.reddit;

import android.content.Context;
import com.onelouder.baconreader.connectivity.Tasks;

public class RedditApi {
    public static void getMessages(
            Context context,
            String where,
            String after,
            String before,
            int limit,
            Tasks.OnCompleteListener<Listing> listener) {}
}
