// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.data;

import android.content.Context;
import com.onelouder.baconreader.connectivity.Tasks;
import com.onelouder.baconreader.reddit.More;
import java.util.List;
import java.util.Map;

/** Its private static threads map is read by reflection. */
public class ThreadManager {
    public static synchronized void loadMore(
            Context context, Object owner, ThreadKey key, More more, Tasks.OnCompleteListener listener) {}

    public static class Thread {
        public List replies;
        public Map<String, Integer> states;
    }
}
