// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.upstream.DataSource;

public final class ProgressiveMediaSource implements MediaSource {
    public static final class Factory implements MediaSourceFactory {
        public Factory(DataSource.Factory dataSourceFactory) {}

        @Override
        public native ProgressiveMediaSource createMediaSource(MediaItem mediaItem);
    }
}
