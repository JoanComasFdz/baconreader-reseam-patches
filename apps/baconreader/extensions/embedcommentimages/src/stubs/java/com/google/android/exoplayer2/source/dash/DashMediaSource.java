// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.google.android.exoplayer2.source.dash;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;

public final class DashMediaSource implements MediaSource {
    public static final class Factory implements MediaSourceFactory {
        public Factory(DataSource.Factory dataSourceFactory) {}

        @Override
        public native DashMediaSource createMediaSource(MediaItem mediaItem);
    }
}
