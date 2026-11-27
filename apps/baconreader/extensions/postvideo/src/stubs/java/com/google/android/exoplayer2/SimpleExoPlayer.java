// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.google.android.exoplayer2;

public class SimpleExoPlayer implements Player {
    public native void setPlayWhenReady(boolean playWhenReady);

    public native int getPlaybackState();

    public native void seekTo(int windowIndex, long positionMs);
}
