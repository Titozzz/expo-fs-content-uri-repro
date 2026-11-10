# expo-file-system content:// repro (Android)

Two expo-file-system 58.0.13 (also reproduced on 57.0.7) issues on Android, with `content://` files picked through `File.pickFileAsync()`.

The app ships its own documents provider ("Repro files" in the system picker) with four 256 KiB files:

| File | COLUMN_SIZE | Served as |
| --- | --- | --- |
| correct-size.bin | 262144 (correct) | real file |
| wrong-size.bin | 196608 (too small) | real file |
| unknown-size.bin | null | real file |
| pipe.bin | 262144 (correct) | pipe |

## Run

```sh
npm install
python3 server.py        # in another terminal, receives the uploads
npx expo run:android     # emulator or device
```

On a physical device, set the upload URL to your machine's LAN address.

In the app, tap a button: the picker opens in "Repro files" (otherwise open it from the picker menu), then pick a file. Results show in the app and in the Metro logs (`[repro]`).

## Expected vs actual

Actual results below: Pixel_API_36 emulator (Android 16), debug build, expo-file-system 58.0.13 (also reproduced on 57.0.7).

**Pick + upload** (`File.upload`, `BINARY_CONTENT`)
- expected: every file uploads, the server receives 262144 bytes
- actual:

```
UPLOAD correct-size.bin: HTTP 200 received 262144 bytes (Content-Length: 262144)
UPLOAD wrong-size.bin: FAILED Call to function 'FileSystemUploadTask.start' has been rejected.
→ Caused by: Unable to upload a file: expected 196608 bytes but received 204800
UPLOAD unknown-size.bin: FAILED Call to function 'FileSystemUploadTask.start' has been rejected.
→ Caused by: Unable to upload a file: expected 0 bytes but received 8192
UPLOAD pipe.bin: HTTP 200 received 262144 bytes (Content-Length: 262144)
```

For `wrong-size.bin`, the server received 196608 bytes (a truncated file) and answered 200 before the client threw. The `unknown-size.bin` upload never reached the server.

**Pick + copy** (`File.copy` into `Paths.cache`)
- expected: copy size 262144 for every file
- actual:

```
COPY correct-size.bin: reported size=262144 stream length=262144 copy size=262144
COPY wrong-size.bin: reported size=196608 stream length=262144 copy size=262144
COPY unknown-size.bin: reported size=0 stream length=262144 copy size=262144
COPY pipe.bin: reported size=262144 stream length=262144 copy size=0
```

`copy()` resolves without error and the copy of `pipe.bin` is empty: it opens the pipe and closes it without reading (the provider's writer then gets `EPIPE`).
