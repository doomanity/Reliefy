#!/usr/bin/env python3
"""Asks YouTube's player endpoint for "made for kids" videos as different clients, and checks
whether the stream URLs it hands back actually download. Usage: kids.py <out-dir>"""
import json, os, re, sys, urllib.request, urllib.error

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")

# (label, clientName, clientVersion, client-name-number, user agent, extra client fields)
CLIENTS = [
    ("web", "WEB", "2.20260708.00.00", 1, UA, {}),
    ("mweb", "MWEB", "2.20260708.05.00", 2,
     "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)", {}),
    ("web_embedded", "WEB_EMBEDDED_PLAYER", "2.20260708.00.00", 56, UA, {}),
    ("web_kids", "WEB_KIDS", "2.20260708.00.00", 76, UA, {}),
    ("web_remix", "WEB_REMIX", "1.20260707.12.00", 67, UA, {}),
    ("tv", "TVHTML5", "7.20260707.07.00", 7,
     "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)", {}),
    ("tv_simply", "TVHTML5_SIMPLY", "1.0", 75, UA, {}),
    ("tv_embedded", "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0", 85, UA, {}),
    ("android", "ANDROID", "21.26.364", 3, "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
     {"androidSdkVersion": 30, "osName": "Android", "osVersion": "11"}),
    ("android_vr", "ANDROID_VR", "1.65.10", 28,
     "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
     {"deviceMake": "Oculus", "deviceModel": "Quest 3", "androidSdkVersion": 32, "osName": "Android", "osVersion": "12L"}),
    ("ios", "IOS", "21.26.4", 5, "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
     {"deviceMake": "Apple", "deviceModel": "iPhone16,2", "osName": "iPhone", "osVersion": "18.3.2.22D82"}),
    ("visionos", "VISIONOS", "1.02", 101,
     "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
     {"deviceMake": "Apple", "deviceModel": "RealityDevice17,1", "osName": "visionOS", "osVersion": "26.5.23O471"}),
]

# Baby Shark Dance (Pinkfong, made for kids), a POPS Kids search fallback, and a normal control video.
VIDEOS = ["XqZsoesa55w", "dQw4w9WgXcQ"]


def http(url, data=None, headers=None, method=None):
    req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.status, r.read()


home = http("https://www.youtube.com/", headers={"User-Agent": UA, "Cookie": "SOCS=CAI"})[1].decode("utf-8", "replace")
vd = re.search(r'"VISITOR_DATA":"([^"]+)"', home).group(1)

# Find a POPS Kids video through the search page.
try:
    page = http("https://www.youtube.com/results?search_query=pops+kids+doraemon",
                headers={"User-Agent": UA, "Cookie": "SOCS=CAI"})[1].decode("utf-8", "replace")
    ids = re.findall(r'"videoId":"([\w-]{11})"', page)
    for v in ids[:2]:
        if v not in VIDEOS:
            VIDEOS.insert(1, v)
except Exception as e:
    print("search failed", e)


def try_url(url, ua):
    if not url:
        return "no-url"
    try:
        st, body = http(url + "&range=0-2047", headers={"User-Agent": ua})
        return "%d len=%d" % (st, len(body))
    except urllib.error.HTTPError as e:
        return "HTTP %d" % e.code
    except Exception as e:
        return "ERR %s" % e


report = {"visitorData": vd, "videos": VIDEOS, "results": []}
for vid in VIDEOS:
    for label, name, ver, num, ua, extra in CLIENTS:
        client = {"clientName": name, "clientVersion": ver, "hl": "en", "gl": "US", "visitorData": vd, "userAgent": ua}
        client.update(extra)
        body = {"context": {"client": client}, "videoId": vid, "contentCheckOk": True, "racyCheckOk": True}
        hdr = {"Content-Type": "application/json", "User-Agent": ua, "X-YouTube-Client-Name": str(num),
               "X-YouTube-Client-Version": ver, "X-Goog-Visitor-Id": vd, "Origin": "https://www.youtube.com"}
        row = {"video": vid, "client": label}
        try:
            _, raw = http("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", json.dumps(body).encode(), hdr)
            obj = json.loads(raw)
            ps = obj.get("playabilityStatus", {})
            sd = obj.get("streamingData", {}) or {}
            fmts = (sd.get("formats") or []) + (sd.get("adaptiveFormats") or [])
            direct = [f for f in fmts if f.get("url")]
            row.update({
                "status": ps.get("status"), "reason": ps.get("reason"),
                "madeForKids": (obj.get("microformat", {}).get("playerMicroformatRenderer", {}) or {}).get("isFamilySafe"),
                "formats": len(fmts), "direct": len(direct),
                "ciphered": len([f for f in fmts if f.get("signatureCipher")]),
                "sabr": bool(sd.get("serverAbrStreamingUrl")), "hls": bool(sd.get("hlsManifestUrl")),
                "itags": sorted({f.get("itag") for f in fmts}),
            })
            if direct:
                audio = next((f for f in direct if f.get("mimeType", "").startswith("audio")), direct[0])
                row["download"] = try_url(audio["url"], ua)
            if sd.get("hlsManifestUrl"):
                try:
                    st, m = http(sd["hlsManifestUrl"], headers={"User-Agent": ua})
                    seg = [l for l in m.decode("utf-8", "replace").splitlines() if l.startswith("http")]
                    row["hlsMaster"] = st
                    if seg:
                        st2, m2 = http(seg[0], headers={"User-Agent": ua})
                        segs = [l for l in m2.decode("utf-8", "replace").splitlines() if l.startswith("http")]
                        row["hlsVariant"] = st2
                        if segs:
                            try:
                                st3, b3 = http(segs[min(len(segs) - 1, 40)], headers={"User-Agent": ua})
                                row["hlsSegment"] = "%d len=%d" % (st3, len(b3))
                            except urllib.error.HTTPError as e:
                                row["hlsSegment"] = "HTTP %d" % e.code
                except Exception as e:
                    row["hlsErr"] = str(e)
        except urllib.error.HTTPError as e:
            row["status"] = "HTTP %d" % e.code
        except Exception as e:
            row["status"] = "ERR %s" % e
        print(json.dumps(row))
        report["results"].append(row)

with open(os.path.join(OUT, "kids.json"), "w") as f:
    json.dump(report, f, indent=1)
