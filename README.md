# Spotify History Logger
Ever wanted to make your own versions of Spotify's yearly summary
playlists? To do that you need access to your playback history.

This is a web app that does just that, by polling [Spotify's
API](https://developer.spotify.com/documentation/web-api/reference/player/get-recently-played/)
at regular intervals. To keep your embarrassing music taste a secret
between you and Spotify, everything is encrypted using [libsodium
sealed
boxes](https://libsodium.gitbook.io/doc/public-key_cryptography/sealed_boxes),
using a public key you generate when you register. The private key is
never sent to the server; instead all decryption of the data (when you
want to analyse it later) is done in your browser (or you can [do it
yourself](resources/public/decode.py)).

## Analysing your data
When you decided to record your Spotify history, you probably also
decided that you wanted to finally learn SQL in order to turn that raw
data into profound insights. Fortunately, the app has a built in SQL
IDE that runs [SQLite](https://www.sqlite.org/) right in your browser.
This achieves the twin aims of keeping your data private and stopping
you from running `DROP TABLE` on everyone else's data.

If you would like to experience this for yourself without registering,
you can [try it on some fake data](https://spothist.dlesl.com/app/sql).

## How it works
Everything is written in Clojure. The backend is quite simple,
handling authentication with Spotify, and regularly polling for new
data. Spotify's responses are compressed (they're quite verbose),
encrypted and stored, and that's it.

The frontend uses
[libsodium.js](https://github.com/jedisct1/libsodium.js/) to decrypt
this data, and inserts it into a database using
[SQL.js](https://github.com/kripken/sql.js/).

## Development
To build it you will need Java 11, leiningen and yarn. Then you can
just run `./build.sh`.

To run it from the REPL, run `build.sh` once first to setup the
dependencies, then:

```
$ lein repl
user=> (start)     ; start backend server
user=> (start-fw)  ; start figwheel for the frontend
```

The project layout is based on [luminus](https://luminusweb.com/).
