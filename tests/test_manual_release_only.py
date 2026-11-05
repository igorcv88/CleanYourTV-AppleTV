"""PR validation is automatic; signed publication remains owner-triggered.

Pull requests test tools and compile the launcher without production keys.
The separate manual workflow validates, signs and publishes the release directly
to GitHub Releases; neither workflow uploads Actions artifacts.
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / ".github/workflows"
RELEASE = DIRECTORY / "release.yml"
PR_CI = DIRECTORY / "pr-ci.yml"


class ManualReleaseOnlyTest(unittest.TestCase):
    def setUp(self):
        self.text = RELEASE.read_text(encoding="utf-8")

    def test_only_pr_validation_and_manual_release_workflows(self):
        found = sorted(p.name for pattern in ("*.yml", "*.yaml") for p in DIRECTORY.glob(pattern))
        self.assertEqual(sorted([PR_CI.name, RELEASE.name]), found)

    def test_pr_checks_validate_before_build_and_never_publish(self):
        ci = PR_CI.read_text(encoding="utf-8")
        self.assertIn("  pull_request:", ci)
        self.assertIn("permissions:\n  contents: read", ci)
        self.assertIn("python -m unittest discover -s tests -v", ci)
        self.assertIn("python tools/gen_tables.py --check", ci)
        self.assertIn("needs: tool-tests", ci)
        for step in ("testReleaseUnitTest", "lintRelease", "assembleRelease"):
            self.assertIn(step, ci)
        self.assertLess(ci.index("testReleaseUnitTest"), ci.index("lintRelease"))
        self.assertLess(ci.index("lintRelease"), ci.index("assembleRelease"))
        for forbidden in ("secrets.", "gh release", "actions/upload-artifact"):
            self.assertNotIn(forbidden, ci)

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
