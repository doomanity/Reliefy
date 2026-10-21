# SABR protocol buffers

These `.proto` files come from LibreTube (`app/src/main/proto`). The Java lite classes generated
from them are checked in under `src/main/java/video_streaming` and `src/main/java/misc`, because
the protobuf Gradle plugin cannot be applied to an Android library while AGP's new DSL is on.

Regenerate with protoc 35.1 (it must not be newer than the `protobuf-javalite` runtime, 4.35.1):

```bash
cd core/media/videoPlayer/proto
protoc -I . --java_out=lite:../src/main/java $(find . -name "*.proto")
```
