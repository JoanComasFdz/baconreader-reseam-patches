// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.reddit;

import android.os.Parcel;
import android.os.Parcelable;

public class Link implements Parcelable {
    public Media media;
    public String title;
    public String url;

    public boolean isRedditVideo() {
        return false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {}
}
