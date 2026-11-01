// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.reddit;

import java.util.List;

public class MoreChildrenResponse {
    public MoreChildrenResponseJson json;

    public static class MoreChildrenResponseJson {
        public MoreChildrenResponseData data;
        public List errors;
    }

    public static class MoreChildrenResponseData {
        public List<Thing> things;
    }
}
