#!/usr/bin/env python3
"""Saves real YouTube web responses (anonymous) so the Kotlin parsers can be written against real data.
Usage: probe.py <out-dir>
"""
import json, os, re, sys, urllib.parse, urllib.request

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
COOKIE = "SOCS=CAI; PREF=hl=en&gl=US"


def http(url, data=None, headers=None):
    h = {"User-Agent": UA, "Accept-Language": "en-US,en;q=0.9", "Cookie": COOKIE}
    h.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=h)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def save(name, obj):
    path = os.path.join(OUT, name + ".json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    print("saved", name, os.path.getsize(path))


home = http("https://www.youtube.com/").decode("utf-8", "replace")
key = re.search(r'"INNERTUBE_API_KEY":"([^"]+)"', home).group(1)
ver = re.search(r'"INNERTUBE_CONTEXT_CLIENT_VERSION":"([^"]+)"', home).group(1)
vd = re.search(r'"VISITOR_DATA":"([^"]+)"', home).group(1)
print("client version", ver)
save("_meta", {"key": key, "version": ver, "visitorData": vd})

CTX = {"client": {"clientName": "WEB", "clientVersion": ver, "hl": "en", "gl": "US",
                  "visitorData": vd, "userAgent": UA, "platform": "DESKTOP"}}


def call(ep, body, name):
    body = dict(body)
    body["context"] = CTX
    url = "https://www.youtube.com/youtubei/v1/%s?key=%s&prettyPrint=false" % (ep, key)
    hdr = {"Content-Type": "application/json", "Origin": "https://www.youtube.com",
           "X-YouTube-Client-Name": "1", "X-YouTube-Client-Version": ver,
           "X-Goog-Visitor-Id": vd}
    try:
        raw = http(url, json.dumps(body).encode(), hdr)
        obj = json.loads(raw)
    except Exception as e:  # keep going, the error itself is useful
        print("FAILED", name, e)
        save(name + "_ERROR", {"error": str(e)})
        return None
    save(name, obj)
    return obj


def tokens(obj, path=""):
    """Every continuation token with the path it sits at."""
    found = []
    if isinstance(obj, dict):
        cc = obj.get("continuationCommand")
        if isinstance(cc, dict) and "token" in cc:
            found.append((path, cc["token"]))
        for k, v in obj.items():
            found += tokens(v, path + "/" + k)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            found += tokens(v, path + "[%d]" % i)
    return found


def comment_section_token(obj):
    """Token inside the comment-item-section (or the comments engagement panel)."""
    def walk(o):
        if isinstance(o, dict):
            if o.get("sectionIdentifier") == "comment-item-section" or \
               o.get("targetId") == "engagement-panel-comments-section":
                t = tokens(o)
                if t:
                    return t[0][1]
            for v in o.values():
                r = walk(v)
                if r:
                    return r
        elif isinstance(o, list):
            for v in o:
                r = walk(v)
                if r:
                    return r
        return None
    return walk(obj)


# --- home ---
h = call("browse", {"browseId": "FEwhat_to_watch"}, "home")
if h:
    t = [x for x in tokens(h)]
    if t:
        call("browse", {"continuation": t[-1][1]}, "home_continuation")
call("browse", {"browseId": "FEtrending"}, "trending")
call("browse", {"browseId": "FEexplore"}, "explore")
call("browse", {"browseId": "FEsubscriptions"}, "subscriptions_anon")

# --- search ---
s = call("search", {"query": "lofi hip hop"}, "search")
if s:
    t = tokens(s)
    if t:
        call("search", {"continuation": t[-1][1]}, "search_continuation")
call("search", {"query": "lofi hip hop", "params": "EgIQAQ%3D%3D"}, "search_videos_only")
call("search", {"query": "mrbeast", "params": "EgIQAg%3D%3D"}, "search_channels")
call("search", {"query": "python tutorial", "params": "EgIQAw%3D%3D"}, "search_playlists")
try:
    sug = http("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=" +
               urllib.parse.quote("lofi")).decode("utf-8", "replace")
    save("suggestions", {"raw": sug})
except Exception as e:
    print("FAILED suggestions", e)

# --- watch page ---
n = call("next", {"videoId": "dQw4w9WgXcQ"}, "next")
if n:
    save("next_tokens", tokens(n))
    ct = comment_section_token(n)
    if ct:
        c = call("next", {"continuation": ct}, "comments")
        if c:
            save("comments_tokens", tokens(c))
            ts = tokens(c)
            # a reply thread / next page, whichever shows up last
            if ts:
                call("next", {"continuation": ts[-1][1]}, "comments_page2")
    rel = [x for x in tokens(n) if "secondaryResults" in x[0]]
    if rel:
        call("next", {"continuation": rel[-1][1]}, "related_continuation")
call("next", {"videoId": "jfKfPfyJRdk"}, "next_live")
call("player", {"videoId": "dQw4w9WgXcQ"}, "player_web")

# --- channel ---
CH = "UCX6OQ3DkcsbYNE6H8uQQuVA"
ch = call("browse", {"browseId": CH}, "channel_home")
v = call("browse", {"browseId": CH, "params": "EgZ2aWRlb3PyBgQKAjoA"}, "channel_videos")
if v:
    t = tokens(v)
    if t:
        call("browse", {"continuation": t[-1][1]}, "channel_videos_continuation")
call("browse", {"browseId": CH, "params": "EglwbGF5bGlzdHPyBgQKAkIA"}, "channel_playlists")
call("browse", {"browseId": CH, "params": "EgVhYm91dPIGBAoCEgA%3D"}, "channel_about")
call("browse", {"browseId": CH, "params": "EgZzaG9ydHPyBgUKA5oBAA%3D%3D"}, "channel_shorts")

# --- playlist ---
call("browse", {"browseId": "VLPLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"}, "playlist")
call("browse", {"browseId": "VLPLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"}, "playlist_2")

# --- video page html (ytInitialData fallback) ---
try:
    page = http("https://www.youtube.com/watch?v=dQw4w9WgXcQ").decode("utf-8", "replace")
    save("watch_html_stats", {"length": len(page), "has_initial_data": "ytInitialData" in page})
except Exception as e:
    print("FAILED watch html", e)
