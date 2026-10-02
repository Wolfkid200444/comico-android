import tempfile
import unittest
from pathlib import Path
from read_version import release_metadata


class ReleaseVersionTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.current = Path(self.directory.name) / 'current.properties'
        self.previous = Path(self.directory.name) / 'previous.properties'
        self.previous.write_text('VERSION_NAME=0.1.0\nVERSION_CODE=1\n')

    def write_current(self, name, code):
        self.current.write_text(f'VERSION_NAME={name}\nVERSION_CODE={code}\n')

    def test_first_release(self):
        self.write_current('0.1.0', 1)
        self.assertEqual('v0.1.0', release_metadata(self.current)['tag'])

    def test_unchanged_version_does_not_release(self):
        self.write_current('0.1.0', 1)
        self.assertEqual('false', release_metadata(self.current, self.previous)['changed'])

    def test_bump_both_values(self):
        self.write_current('0.2.0', 2)
        self.assertEqual('true', release_metadata(self.current, self.previous)['changed'])

    def test_reusing_name_is_rejected(self):
        self.write_current('0.1.0', 2)
        with self.assertRaises(ValueError):
            release_metadata(self.current, self.previous)

    def test_reusing_code_is_rejected(self):
        self.write_current('0.2.0', 1)
        with self.assertRaises(ValueError):
            release_metadata(self.current, self.previous)

    def test_unsafe_name_is_rejected(self):
        self.write_current('0.2.0;echo unsafe', 2)
        with self.assertRaises(ValueError):
            release_metadata(self.current)


if __name__ == '__main__':
    unittest.main()
