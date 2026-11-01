// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.reddit;

import android.content.Context;
import com.onelouder.baconreader.connectivity.Tasks;
import java.util.List;

public class RedditApi {
    public static void getComments(
            Context context,
            String linkId,
            String commentId,
            int contextLines,
            String sort,
            int limit,
            Tasks.OnCompleteListener<List<Listing>> listener) {}
}
