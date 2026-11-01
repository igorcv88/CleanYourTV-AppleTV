"""The native TV picker must ship *all* official themes and the optional Scenic Glass layout."""

import json
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
THEMES = ROOT / "themes"


class ThemeGalleryTest(unittest.TestCase):
    def test_native_gallery_has_one_entry_per_bundled_theme(self):
        source = (SRC / "ThemeCatalog.java").read_text(encoding="utf-8")
        block = re.search(r"private static final String\[\] IDS\s*=\s*\{(.*?)\};", source, re.S)
        self.assertIsNotNone(block, "ThemeCatalog.IDS is missing")
        ids = re.findall(r'"([a-z0-9-]+)"', block.group(1))
        folders = {p.name for p in THEMES.iterdir() if (p.is_dir() and (p / "config.json").exists())}
        self.assertEqual(17, len(ids))
        self.assertEqual(17, len(set(ids)), "theme picker must not repeat an ID")
        self.assertEqual(folders, set(ids), "themes/ and the TV picker must agree")
        for theme in ids:
            with self.subTest(theme=theme):
                self.assertIsInstance(json.loads((THEMES / theme / "config.json").read_text()), dict)
                self.assertTrue((THEMES / theme / "screenshot.jpg").is_file())

    def test_switch_is_reversible_and_off_main_thread(self):
        activity = (SRC / "ThemeGalleryActivity.java").read_text(encoding="utf-8")
        catalog = (SRC / "ThemeCatalog.java").read_text(encoding="utf-8")
        self.assertIn("worker.execute(() ->", activity)
        self.assertIn("ThemeCatalog.apply(getApplicationContext(), id)", activity)
        for path in ("theme-picker-backup", "previousCollection", "previousWallpaper", "previousConfig"):
            self.assertIn(path, catalog)
        self.assertIn("wallpapers/", catalog, "multi-image themes must retain their wallpapers")

    def test_scenic_is_optional_and_original_home_stays_default(self):
        config = (SRC / "Config.java").read_text(encoding="utf-8")
        scene = json.loads((THEMES / "scenic-glass/config.json").read_text())
        self.assertTrue(scene["scenic_home"])
        self.assertIn('bool(json, "scenic_home", false)', config)
        self.assertIn("scenicHome;", config)
        for path in THEMES.glob("*/config.json"):
            if path.parent.name == "scenic-glass":
                continue
            self.assertFalse(json.loads(path.read_text()).get("scenic_home", False), path.name)


if __name__ == "__main__":
    unittest.main()
