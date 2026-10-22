// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.imgur_gallery;

import android.os.AsyncTask;
import java.util.List;

/** Lists the images of an Imgur album or gallery with the app's Imgur client. */
public class ImagesRetriever extends AsyncTask<Void, Void, List<ImgurImage>> {
    public ImagesRetriever(String imgurHash, int requestType, GalleryCallback callback) {}

    @Override
    protected native List<ImgurImage> doInBackground(Void... voids);
}
