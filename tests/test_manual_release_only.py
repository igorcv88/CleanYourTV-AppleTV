"""The fork spends GitHub Actions minutes only when its owner publishes a release.

There must be one workflow, triggered manually. It validates source and builds/signs
the APK, then attaches the APK, its SHA-256 and the complete setup ZIP with its
SHA-256 directly to GitHub Releases; no Actions artifacts are uploaded.
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / ".github/workflows"
RELEASE = DIRECTORY / "release.yml"


class ManualReleaseOnlyTest(unittest.TestCase):
    def setUp(self):
        self.text = RELEASE.read_text(encoding="utf-8")

    def test_exactly_one_workflow(self):
        found = sorted(p.name for pattern in ("*.yml", "*.yaml") for p in DIRECTORY.glob(pattern))
        self.assertEqual([RELEASE.name], found)

    def test_only_manual_dispatch_never_pr_push_or_schedule(self):
        self.assertRegex(self.text, r"(?m)^on:\s*$")
        head = self.text.split("\njobs:", 1)[0]
        triggers = set(re.findall(
            r"(?m)^  (push|pull_request|workflow_dispatch|schedule|workflow_call|repository_dispatch):",
            head,
        ))
        self.assertEqual({"workflow_dispatch"}, triggers)
        self.assertIn("github.ref == 'refs/heads/main'", self.text)

    def test_validation_happens_before_publication(self):
        required = (
            "tools/venv/bin/python -m unittest discover -s tests -v",
            "./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease",
            'test "$size" -lt 16777216',
            "apksigner verify --verbose --print-certs",
        )
        release_at = self.text.index('gh "${args[@]}"')
        for check in required:
            with self.subTest(check=check):
                self.assertIn(check, self.text)
                self.assertLess(self.text.index(check), release_at)

    def test_signed_release_uses_all_four_secrets(self):
        for name in ("KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
            with self.subTest(secret=name):
                self.assertIn("secrets." + name, self.text)
        self.assertIn("tv-release.jks", self.text)
        self.assertIn("releaseKeystore", self.text)

    def test_four_assets_go_directly_to_github_releases(self):
        for asset in ('"release/$APK"', '"release/$APK.sha256"',
                      '"release/$ZIP"', '"release/$ZIP.sha256"'):
            with self.subTest(asset=asset):
                self.assertIn(asset, self.text)
        self.assertIn('args=(release create "$TAG"', self.text)
        self.assertNotIn("actions/upload-artifact", self.text.lower())
        self.assertNotIn("artifact upload", self.text.lower())

    def test_setup_zip_excludes_linux_runner_venv(self):
        self.assertIn("rsync -a --exclude='/venv/' tools/", self.text)
        self.assertNotIn('cp -R tools examples', self.text)

    def test_signing_properties_written_with_java_compatible_unicode(self):
        self.assertIn('python3 tools/release_properties.py "$RUNNER_TEMP/tv-release.properties"', self.text)


if __name__ == "__main__":
    unittest.main()
