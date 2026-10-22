#!/usr/bin/env python3
"""Checks the native libraries in a built APK or AAB for 16 KB page-size compatibility.

    python3 tools/check_native_alignment.py app/build/outputs/apk/release/spidy.apk [more files]

For every lib/arm64-v8a/*.so (base/lib/... in an AAB) it checks:

  1. ELF: every PT_LOAD segment has p_align >= 16384 (the -Wl,-z,max-page-size=16384 flag in
     app/src/main/cpp/CMakeLists.txt). Without it the library cannot load on 16 KB devices.
  2. APK only: the entry is stored uncompressed and its data starts on a 16 KB boundary, so it
     can be mapped directly from the APK (useLegacyPackaging = false). AGP 8.5.1+ does this.

Our own library (libspidey_engine.so) failing is an error. Any other library failing (for
example libc++_shared.so from an NDK older than r28) is a warning, because it is outside this
repository's control; pass --strict to make those errors too.

Exit status: 0 fine, 1 a violation, 2 usage or unreadable input.
"""

import struct
import sys
import zipfile

PAGE = 16384
OWN_LIBRARY = "libspidey_engine.so"
PT_LOAD = 1


def elf_load_alignments(data):
    """Returns the p_align of every PT_LOAD segment of a 64-bit little-endian ELF."""
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    if data[4] != 2 or data[5] != 1:
        raise ValueError("only 64-bit little-endian ELF (arm64-v8a) is supported")
    e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
    e_phentsize, e_phnum = struct.unpack_from("<HH", data, 0x36)
    aligns = []
    for index in range(e_phnum):
        offset = e_phoff + index * e_phentsize
        p_type = struct.unpack_from("<I", data, offset)[0]
        if p_type == PT_LOAD:
            aligns.append(struct.unpack_from("<Q", data, offset + 0x30)[0])
    return aligns


def stored_data_offset(archive_path, info):
    """Offset of an entry's data inside the zip: local header (30 bytes) + name + extra."""
    with open(archive_path, "rb") as handle:
        handle.seek(info.header_offset)
        header = handle.read(30)
    name_length, extra_length = struct.unpack_from("<HH", header, 26)
    return info.header_offset + 30 + name_length + extra_length


def check_archive(path, strict):
    errors, warnings = [], []
    is_apk = path.lower().endswith(".apk")
    with zipfile.ZipFile(path) as archive:
        libs = [i for i in archive.infolist()
                if "lib/arm64-v8a/" in i.filename and i.filename.endswith(".so")]
        if not libs:
            warnings.append(f"{path}: no arm64-v8a native libraries found (engine not built?)")
        for info in libs:
            problems = []
            try:
                aligns = elf_load_alignments(archive.read(info))
            except (ValueError, struct.error) as error:
                problems.append(f"unreadable ELF: {error}")
                aligns = []
            for align in aligns:
                if align < PAGE:
                    problems.append(f"PT_LOAD p_align is {align}, needs >= {PAGE}")
                    break
            if is_apk:
                if info.compress_type != zipfile.ZIP_STORED:
                    problems.append("entry is compressed (must be stored)")
                elif stored_data_offset(path, info) % PAGE != 0:
                    problems.append(f"entry data is not {PAGE}-byte aligned in the APK")
            label = f"{path}: {info.filename}"
            own = info.filename.endswith("/" + OWN_LIBRARY)
            if not problems:
                print(f"ok      {label}")
            elif own or strict:
                errors += [f"{label}: {p}" for p in problems]
            else:
                warnings += [f"{label}: {p}" for p in problems]
    return errors, warnings


def main(argv):
    strict = "--strict" in argv
    paths = [a for a in argv if not a.startswith("--")]
    if not paths:
        print(__doc__, file=sys.stderr)
        return 2
    errors, warnings = [], []
    for path in paths:
        try:
            e, w = check_archive(path, strict)
        except (OSError, zipfile.BadZipFile) as error:
            print(f"cannot read {path}: {error}", file=sys.stderr)
            return 2
        errors += e
        warnings += w
    for line in warnings:
        print(f"warning {line}")
    for line in errors:
        print(f"ERROR   {line}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
