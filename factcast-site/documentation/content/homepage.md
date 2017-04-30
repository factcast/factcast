+++
draft = false
title = "Home page"
description = ""

tags = ["tag1","tag2"]

[menu.main]
parent = "page"
identifier = "home"
weight = 1

+++

To tell Hugo-theme-docdock to consider a page as homepage's content, just create a content file named `_index.md` in content folder.

{{%panel theme="danger" header="**Homepage consideration**"%}}Do not set [menu.main] in the frontmatter of your _index.md file{{%/panel%}}
