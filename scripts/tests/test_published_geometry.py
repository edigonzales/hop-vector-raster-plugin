"""Published Maven/installed-runtime consistency checks, without network access."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile


spec = importlib.util.spec_from_file_location(
    "published_geometry", Path(__file__).resolve().parents[1] / "verify-published-geometry.py"
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PublishedGeometryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / "bundle"
        self.bundle.mkdir()
        self.repo = self.root / "m2"
        self.jar = self.repo / "ch/so/agi/hop-geometry-type/0.2.0-SNAPSHOT/hop-geometry-type-0.2.0-SNAPSHOT.jar"
        self.jar.parent.mkdir(parents=True)
        self.jar.write_bytes(b"published-jar")
        self.jar.with_suffix(".pom").write_text("<project/>")
        self.zip = self.bundle / "hop-geometry-type-plugin-0.2.0-SNAPSHOT.zip"

    def archive(self, contents=(b"published-jar",)):
        with zipfile.ZipFile(self.zip, "w") as archive:
            for index, content in enumerate(contents):
                archive.writestr(f"plugins/misc/hop-geometry-type/hop-geometry-type-{index}.jar", content)
            archive.writestr("plugins/misc/hop-geometry-type/lib/jts-core.jar", b"runtime")

    def verify(self):
        return module.verify(self.bundle, self.repo, "0.2.0-SNAPSHOT")

    def test_identical_bytes_pass(self):
        self.archive()
        self.assertEqual(len(self.verify()), 64)

    def test_different_publications_fail(self):
        self.archive((b"older-jar",))
        with self.assertRaisesRegex(ValueError, "mismatch"):
            self.verify()

    def test_missing_and_multiple_zips_fail(self):
        with self.assertRaisesRegex(ValueError, "found 0"):
            self.verify()
        self.archive()
        (self.bundle / "hop-geometry-type-plugin-extra.zip").write_bytes(self.zip.read_bytes())
        with self.assertRaisesRegex(ValueError, "found 2"):
            self.verify()

    def test_missing_and_multiple_plugin_jars_fail(self):
        for contents in ((), (b"a", b"b")):
            self.archive(contents)
            with self.assertRaisesRegex(ValueError, "exactly one Geometry JAR"):
                self.verify()

    def test_missing_resolved_artifacts_fail(self):
        self.archive()
        self.jar.with_suffix(".pom").unlink()
        with self.assertRaisesRegex(ValueError, "Missing resolved Maven Geometry POM"):
            self.verify()
        self.jar.unlink()
        with self.assertRaisesRegex(ValueError, "Missing resolved Maven Geometry JAR"):
            self.verify()

    def test_corrupt_entry_crc_fails(self):
        self.archive()
        contents = bytearray(self.zip.read_bytes())
        contents[contents.index(b"published-jar")] ^= 1
        self.zip.write_bytes(contents)
        with self.assertRaisesRegex(ValueError, "Corrupt Geometry ZIP"):
            self.verify()

    def test_corrupt_zip_fails(self):
        self.zip.write_bytes(b"broken zip")
        with self.assertRaises(zipfile.BadZipFile):
            self.verify()


if __name__ == "__main__":
    unittest.main()
