// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.MediaItem;

public interface MediaSourceFactory {
    MediaSource createMediaSource(MediaItem mediaItem);
}
