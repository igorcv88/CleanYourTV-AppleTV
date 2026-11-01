"""Write Gradle's Java Properties signing file without leaking secrets to logs.

Java Properties.load(InputStream) decodes ISO-8859-1, even when a file was written
as UTF-8. Escape all non-ASCII code units, including UTF-16 surrogate pairs.
"""

import os
import sys
from pathlib import Path


def escape(value: str) -> str:
    result = []
    units = value.encode("utf-16-be", "surrogatepass")
    for i in range(0, len(units), 2):
        code = (units[i] << 8) | units[i + 1]
        if code > 0x7E or code < 0x20:
            result.append(f"\\u{code:04X}")
        elif chr(code) in "\\ =:#!":
            result.append("\\" + chr(code))
        else:
            result.append(chr(code))
    return "".join(result)


def write_signing_properties(path: Path, environment: dict) -> None:
    values = {
        "storeFile": str(Path(environment["RUNNER_TEMP"]) / "tv-release.jks"),
        "storePassword": environment["KEYSTORE_PASSWORD"],
        "keyAlias": environment["KEY_ALIAS"],
        "keyPassword": environment["KEY_PASSWORD"],
    }
    path.write_text("".join(f"{key}={escape(value)}\n" for key, value in values.items()),
                    encoding="ascii")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: release_properties.py OUTPUT_PATH")
    write_signing_properties(Path(sys.argv[1]), os.environ)
