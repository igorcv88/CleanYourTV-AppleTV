"""The Windows Setup can reuse one ADB identity across freshly extracted ZIP directories."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

from test_adb_key_permissions import ROOT, STUB_DEVICE, STUB_KEYGEN, STUB_SIGNER


class PersistentSetupKeyTest(unittest.TestCase):
    def test_same_key_survives_a_new_tools_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            stub = base / "stub" / "adb_shell"
            (stub / "auth").mkdir(parents=True)
            (stub / "__init__.py").write_text("")
            (stub / "adb_device.py").write_text(STUB_DEVICE)
            (stub / "auth" / "__init__.py").write_text("")
            (stub / "auth" / "keygen.py").write_text(STUB_KEYGEN)
            (stub / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER)

            persistent = base / "settings" / "adbkey"
            for version in ("beta3", "beta4"):
                tools = base / version / "tools"
                tools.mkdir(parents=True)
                shutil.copy2(ROOT / "tools" / "tv.py", tools / "tv.py")
                home = base / "home"
                home.mkdir(exist_ok=True)
                env = dict(os.environ, TV_HOST="127.0.0.1", HOME=str(home),
                           USERPROFILE=str(home), CLEANYOURTV_ADB_KEYDIR=str(persistent),
                           PYTHONPATH=os.pathsep.join([str(base / "stub"), str(tools)]))
                result = subprocess.run(
                    [sys.executable, "-c", "import tv; tv.get_signer(); print(tv.KEYDIR)"],
                    cwd=tools, env=env, capture_output=True, text=True, timeout=60,
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn(str(persistent), result.stdout)
                self.assertFalse((tools / "adbkey").exists())
                key = persistent / "adbkey"
                self.assertTrue(key.exists())
                if version == "beta3":
                    key.write_bytes(b"persisted key from first install\n")
                else:
                    self.assertEqual(key.read_bytes(), b"persisted key from first install\n")


if __name__ == "__main__":
    unittest.main()
