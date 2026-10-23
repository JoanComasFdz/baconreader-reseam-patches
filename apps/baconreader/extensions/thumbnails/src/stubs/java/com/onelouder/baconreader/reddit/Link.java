// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.reddit;

import com.onelouder.baconreader.reddit_gallery.RedditGalleryData;
import com.onelouder.baconreader.reddit_gallery.RedditMediaData;
import java.util.List;
import java.util.Map;

public class Link {
    public List<Link> crosspostParents;
    public Boolean isGallery;
    public Boolean is_self;
    public Media media;
    public Boolean over_18;
    public RedditPreview preview;
    public RedditGalleryData redditGalleryData;
    public Map<String, RedditMediaData> redditMediaMetadata;
    public String thumbnail;
    public String url;
    public String urlOverriddenByDest;
}
