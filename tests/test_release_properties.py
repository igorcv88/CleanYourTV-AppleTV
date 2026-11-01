"""Signing secrets must survive Gradle's ISO-8859-1 Java Properties reader."""

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "tools/release_properties.py"
spec = importlib.util.spec_from_file_location("release_properties", SCRIPT)
release_properties = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_properties)


class ReleasePropertiesTest(unittest.TestCase):
    def test_special_characters_are_escaped_as_java_properties(self):
        self.assertEqual(r"pass\ word\:\=\#\!\\end", release_properties.escape("pass word:=#!\\end"))
        self.assertEqual(r"\u000A\u000D\u0009", release_properties.escape("\n\r\t"))

    def test_non_ascii_uses_java_utf16_unicode_escapes(self):
        self.assertEqual(r"produ\u00E7\u00E3o", release_properties.escape("produção"))
        self.assertEqual(r"\uD83D\uDD11", release_properties.escape("🔑"))

    def test_secret_file_is_ascii_even_when_credentials_are_not(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "tv-release.properties"
            release_properties.write_signing_properties(output, {
                "RUNNER_TEMP": directory,
                "KEYSTORE_PASSWORD": "senha:com espaço",
                "KEY_ALIAS": "produção🔑",
                "KEY_PASSWORD": "#chave!",
            })
            contents = output.read_text(encoding="ascii")
            self.assertIn(r"storePassword=senha\:com\ espa\u00E7o", contents)
            self.assertIn(r"keyAlias=produ\u00E7\u00E3o\uD83D\uDD11", contents)
            self.assertIn(r"keyPassword=\#chave\!", contents)


if __name__ == "__main__":
    unittest.main()
