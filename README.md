chanchan
========

Chanchan is a lightweight static blog generator written in clojure.  It's intended to be a replacement for something like [Jekyll](http://jekyllrb.com).

The initial goal is to be able to generate a very simple blog such as [this](http://gmacd.github.io).

It currently makes use of [Bootstrap](http://twitter.github.io/bootstrap/).

Chanchan is licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).


Usage
=====

Execute `lein run`.  This will convert all markdown posts in `assets/posts` to equivalent html files in `site/posts`.

Execute `python -m SimpleHTTPServer 8888` in the same directory to run a simple http server.

Browse to `http://localhost:8888/site/posts/2013-07-07-first-post.html` to see the converted post.
