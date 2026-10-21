"""Fetch only the pinned Bazel workspace files needed to build Tensor dispatch."""

import hashlib
import json
from pathlib import Path
import shutil
import socket
import time
import urllib.error
import urllib.request

SOURCE_ROOT = Path('/src/litert-lm')
RECIPE = Path('/recipe')
MAX_SOURCE_BYTES = 4 * 1024 * 1024


def read_source(url: str) -> bytes:
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                return response.read(MAX_SOURCE_BYTES + 1)
        except urllib.error.URLError as error:
            if not isinstance(error.reason, socket.gaierror) or attempt == 2:
                raise
            print(f'Retrying DNS failure for {url}: {error.reason}', flush=True)
            time.sleep(attempt + 1)
    raise RuntimeError('Source download exhausted its bounded attempts')


def fetch_file(commit: str, relative: str, expected: str) -> None:
    target = (SOURCE_ROOT / relative).resolve()
    if not target.is_relative_to(SOURCE_ROOT):
        raise ValueError(f'Invalid source path: {relative}')
    url = f'https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/{commit}/{relative}'
    content = read_source(url)
    if len(content) > MAX_SOURCE_BYTES:
        raise ValueError(f'Source exceeds size limit: {relative}')
    if hashlib.sha256(content).hexdigest() != expected:
        raise ValueError(f'Source checksum mismatch: {relative}')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(content)


def apply_public_hooks_patch(litert_commit: str) -> None:
    workspace = SOURCE_ROOT / 'WORKSPACE'
    source = workspace.read_text()
    if f'LITERT_REF = "{litert_commit}"' not in source:
        raise ValueError('LiteRT pin differs from the verified manifest')
    marker = '    name = "litert",\n'
    if source.count(marker) != 1:
        raise ValueError('Expected exactly one LiteRT repository declaration')
    replacement = marker + '    patches = ["//:google-tensor-public-hooks.patch"],\n    patch_args = ["-p1"],\n'
    workspace.write_text(source.replace(marker, replacement))
    shutil.copyfile(RECIPE / 'google-tensor-public-hooks.patch', SOURCE_ROOT / 'google-tensor-public-hooks.patch')


def drop_workspace_lines(lines: list[str]) -> None:
    # A release can carry a dependency re-pin its own WORKSPACE cannot load
    # (0.18.1 bumps TensorFlow past the rules_cc it declares). Each dropped line
    # must exist exactly once, so a new release never inherits this silently.
    workspace = SOURCE_ROOT / 'WORKSPACE'
    source = workspace.read_text()
    for line in lines:
        if source.count(line + '\n') != 1:
            raise ValueError(f'Expected exactly one WORKSPACE line: {line}')
        source = source.replace(line + '\n', '')
    workspace.write_text(source)


def main() -> None:
    manifest = json.loads((RECIPE / 'sources.json').read_text())
    files = manifest['files']
    if not 1 <= len(files) <= 128:
        raise ValueError('Invalid number of source files')
    SOURCE_ROOT.mkdir(parents=True, exist_ok=True)
    for relative, expected in files.items():
        fetch_file(manifest['litertlm_commit'], relative, expected)
    drop_workspace_lines(manifest['dropped_workspace_lines'])
    apply_public_hooks_patch(manifest['litert_commit'])
    print(f"Verified LiteRT-LM {manifest['litertlm_version']}, LiteRT {manifest['litert_commit']}")


if __name__ == '__main__':
    main()
