// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.google.android.exoplayer2;

import android.content.Context;
import com.google.android.exoplayer2.source.MediaSource;

public class SimpleExoPlayer implements Player {
    public native void setMediaSource(MediaSource mediaSource);

    public native void prepare();

    public native void setPlayWhenReady(boolean playWhenReady);

    public native void release();

    public static final class Builder {
        public Builder(Context context) {}

        public native SimpleExoPlayer build();
    }
}
