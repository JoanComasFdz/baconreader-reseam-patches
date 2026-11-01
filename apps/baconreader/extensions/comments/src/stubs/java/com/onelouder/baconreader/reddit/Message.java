// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.reddit;

import java.util.List;

public class Message extends ThingData {
    public String parent_id;
    public List<Thing> replies;

    public <T> List<T> getReplies(Class<T> type) {
        return null;
    }
}
