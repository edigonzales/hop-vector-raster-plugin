import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

DOCS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('biblios_build', DOCS / 'build.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()


class DocumentationBuildTest(unittest.TestCase):
    def test_workflow_scope_and_deployment_guards(self):
        workflow = (DOCS.parents[1] / '.github/workflows/biblios-docs.yml').read_text()
        triggers = workflow.split('on:\n', 1)[1].split('\npermissions:', 1)[0]
        self.assertEqual(triggers.count("- 'docs/biblios/**'"), 2)
        self.assertEqual(triggers.count('paths:'), 2)
        self.assertNotIn('.github/', triggers)
        self.assertIn('branches: [main]', triggers)
        self.assertIn('workflow_dispatch:', triggers)
        import re
        path_blocks = re.findall(r'    paths:\n((?:      .*\n)+)', triggers)
        self.assertEqual(path_blocks, ["      - 'docs/biblios/**'\n"] * 2)
        # Only a handbook path can satisfy either automatic paths filter.
        import fnmatch
        for path, expected in [('docs/biblios/user/01-grundlagen.adoc', True),
                               ('docs/biblios/build.py', True), ('README.md', False),
                               ('examples/raster-clip/clip-cog.hpl', False),
                               ('.github/workflows/biblios-docs.yml', False),
                               ('pom.xml', False), ('scripts/check-doc-examples.py', False)]:
            self.assertEqual(fnmatch.fnmatchcase(path, 'docs/biblios/**'), expected)
        guard = "if: github.event_name != 'pull_request' && github.ref == 'refs/heads/main'"
        self.assertEqual(workflow.count(guard), 2)
        self.assertIn('--revision "$GITHUB_SHA"', workflow)
        build_job = workflow.split('  build:', 1)[1].split('  deploy:', 1)[0]
        self.assertNotIn('pages: write', build_job)
        self.assertNotIn('id-token: write', build_job)

    def test_snapshot_uses_revision_not_main_and_local_edits(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            repo = base / 'repo'; repo.mkdir()
            git(repo, 'init', '-q', '-b', 'main')
            git(repo, 'config', 'user.name', 'Test')
            git(repo, 'config', 'user.email', 'test@localhost')
            (repo / 'docs').mkdir()
            shutil.copytree(DOCS, repo / 'docs/biblios', ignore=shutil.ignore_patterns(
                'build', '.downloads', '.cache', '.biblios-cache', '__pycache__', 'biblios.local.yml'))
            shutil.copytree(DOCS.parents[1] / 'examples', repo / 'examples')
            chapter = repo / 'docs/biblios/user/01-grundlagen.adoc'
            chapter.write_text(chapter.read_text() + '\nMAIN_ONLY_MARKER\n')
            git(repo, 'add', '.')
            git(repo, '-c', 'commit.gpgsign=false', 'commit', '-qm', 'main')
            git(repo, 'checkout', '-qb', 'pr')
            chapter.write_text(chapter.read_text().replace('MAIN_ONLY_MARKER', 'PR_ONLY_MARKER'))
            git(repo, 'add', '.')
            git(repo, '-c', 'commit.gpgsign=false', 'commit', '-qm', 'PR')
            sha = git(repo, 'rev-parse', 'HEAD')
            git(repo, 'checkout', '-q', '--detach', sha)
            chapter.write_text(chapter.read_text() + '\nUNCOMMITTED_MARKER\n')
            previous = helper.ROOT
            try:
                helper.ROOT = repo
                exact = base / 'exact'; exact.mkdir()
                helper.snapshot(exact, sha)
                text = (exact / 'docs/biblios/user/01-grundlagen.adoc').read_text()
                self.assertIn('PR_ONLY_MARKER', text)
                self.assertNotIn('MAIN_ONLY_MARKER', text)
                self.assertNotIn('UNCOMMITTED_MARKER', text)
                local = base / 'local'; local.mkdir()
                helper.snapshot(local)
                self.assertIn('UNCOMMITTED_MARKER', (local / 'docs/biblios/user/01-grundlagen.adoc').read_text())
                self.assertEqual(len(list((local / 'docs/biblios/user/downloads').rglob('*.hpl'))), 7)
                self.assertEqual(git(repo, 'rev-parse', 'HEAD'), sha)
                self.assertEqual(git(repo, 'diff', '--cached'), '')
                if os.environ.get('BIBLIOS_TEST_JAR'):
                    subprocess.run(['python3', str(repo / 'docs/biblios/build.py'), '--revision', sha,
                                    '--jar', os.environ['BIBLIOS_TEST_JAR']], check=True)
                    html = (repo / 'docs/biblios/build/docs-site/benutzerhandbuch/main/index.html').read_text()
                    self.assertIn('PR_ONLY_MARKER', html)
                    self.assertNotIn('MAIN_ONLY_MARKER', html)
                    self.assertNotIn('UNCOMMITTED_MARKER', html)
            finally:
                helper.ROOT = previous


if __name__ == '__main__':
    unittest.main()
