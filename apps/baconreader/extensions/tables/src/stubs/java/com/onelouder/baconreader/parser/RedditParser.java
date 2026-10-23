// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.parser;

import java.util.List;

public class RedditParser {
    public class Node {}

    public class Element extends Node {
        public List<Node> children;
        public String name;

        public native String getAttribute(String name);

        public native List<Element> selectChildren(String name);

        public native Element selectSingleNode(String name);
    }

    public class Text extends Node {
        public native String getText();
    }
}
