"""Check publishable source files without printing matched secret values (stdlib only)."""
from pathlib import Path, PurePosixPath
import argparse
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
GENERATED = {'.git', 'node_modules', '.cache', '.tools', 'build', 'dist', '.gradle',
             '.kotlin', 'test-results', 'playwright-report', 'artifacts', '__pycache__'}
PRIVATE_DIRS = {'private', 'runtime', 'backups', 'screenshots'}
PRIVATE_EXT = {'.apk', '.aab', '.jks', '.keystore', '.p12', '.pfx', '.pem', '.key',
               '.db', '.sqlite', '.sqlite3', '.enc', '.har', '.hprof', '.log', '.zip', '.7z'}
TEXT_EXT = {'.kt', '.kts', '.ts', '.tsx', '.js', '.mjs', '.json', '.md', '.txt', '.xml',
            '.html', '.svg', '.css', '.properties', '.bat', '.cmd', '.ps1', '.py', '.yml', '.yaml'}
PATTERNS = {
    'private-key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'github-token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b'),
    'api-key': re.compile(r'\bsk-(?:proj-)?[A-Za-z0-9_-]{32,}\b'),
    'jwt': re.compile(r'\beyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\b'),
    'windows-user-path': re.compile(r'[A-Za-z]:[/\\](?:Users|Documents and Settings)[/\\][^\s"<>]+', re.I),
    'machine-drive-path': re.compile(r'(?<![A-Za-z])[A-Za-z]:[/\\][A-Za-z\u4e00-\u9fff][^\s"<>]*'),
    'unix-user-path': re.compile(r'/(?:home|Users)/[A-Za-z0-9_.-]+/'),
    'mobile-number': re.compile(r'(?<![\d.])1[3-9]\d{9}(?![\d.])'),
    'id-card-number': re.compile(r'(?<!\d)[1-9]\d{16}[\dXx](?!\d)'),
    'email': re.compile(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b'),
    'url-credentials': re.compile(r'https?://[^/\s:@]+:[^/\s@]+@'),
    'session-cookie-literal': re.compile(r'\b(?:JSESSIONID|CASTGC)=(?!test-|demo-)[A-Za-z0-9_-]{8,}'),
}
# These two source literals are a Kotlin receiver and an intentional URL-spoofing fixture.
EMAIL_SOURCE_LITERALS = {
    'android/app/src/main/java/dev/campususage/app/SchoolLoginActivity.kt': {'this@SchoolLoginActivity.error'},
    'android/app/src/test/java/dev/campususage/app/MainCampusPortalTests.kt': {'attacker@sdhq.hust.edu.cn'},
}
EMAIL_SOURCE_LITERALS['scripts/audit_release.py'] = set().union(*EMAIL_SOURCE_LITERALS.values())
CREDENTIAL_LITERAL = re.compile(
    r'''["'](?:password|passwd|username|access_token|refresh_token|token|studentId|userId|roomId|roomNo)["']\s*[:,]\s*["']([^"'\r\n]{4,})["']''', re.I)

def check(relative, data):
    """Return categories only, never matched personal information."""
    path = PurePosixPath(relative)
    findings = []
    if path.is_absolute() or '..' in path.parts:
        return ['unsafe-archive-path']
    if set(path.parts) & (GENERATED | PRIVATE_DIRS):
        findings.append('excluded-directory')
    if path.suffix.lower() in PRIVATE_EXT or path.name == 'local.properties' or path.name.startswith('.env'):
        findings.append('private-or-generated-file')
    if re.search(r'(?:cookies|credentials|secrets|session-import|import-result|verification-request|verification-result)', path.name, re.I):
        findings.append('private-filename')
    if path.suffix == '.jar' and relative != 'android/gradle/wrapper/gradle-wrapper.jar':
        findings.append('unexpected-binary')
    if path.suffix.lower() in TEXT_EXT or path.name in {'.gitignore', 'gradlew'}:
        try:
            text = data.decode('utf-8-sig')
        except UnicodeDecodeError:
            return findings + ['unreadable-source']
        # The required upstream licence may contain its original author's contact information.
        for category, pattern in PATTERNS.items():
            if category == 'email' and relative == 'android/app/src/main/assets/third-party-notices.txt':
                continue
            matches = pattern.finditer(text)
            allowed = EMAIL_SOURCE_LITERALS.get(relative, set()) if category == 'email' else set()
            if any(match.group(0) not in allowed for match in matches):
                findings.append(category)
        for match in CREDENTIAL_LITERAL.finditer(text):
            value = match.group(1)
            fictional_room = relative == 'android/app/src/test/java/dev/campususage/app/SchoolClientTest.kt' and value in {'room-a', 'room-b'}
            if not fictional_room and not value.startswith(('test-', 'demo-', 'example-', '$')):
                findings.append('credential-literal'); break
    if path.suffix.lower() == '.png':
        import struct
        pos = 8
        while pos + 12 <= len(data):
            size = struct.unpack('>I', data[pos:pos+4])[0]
            if data[pos+4:pos+8] in (b'eXIf', b'tEXt', b'iTXt', b'zTXt'):
                findings.append('image-metadata'); break
            pos += size + 12
    if path.suffix.lower() in {'.jpg', '.jpeg'}:
        pos = 2
        while pos + 4 <= len(data) and data[pos] == 255:
            marker = data[pos+1]
            if marker in (0xDA, 0xD9):
                break
            if marker in (0xE1, 0xED, 0xFE):
                findings.append('image-metadata'); break
            pos += 2 + int.from_bytes(data[pos+2:pos+4], 'big')
    return findings

def source_files():
    try:
        result = subprocess.run(['git', '-C', str(ROOT), 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
                                capture_output=True, check=True)
        names = result.stdout.decode('utf-8').split('\0')
        # Git may discover a parent repository. Only use this list for our own repository.
        if (ROOT/'.git').exists():
            return sorted({name for name in names if name and (ROOT/name).is_file()})
    except (OSError, subprocess.CalledProcessError):
        pass
    return sorted(p.relative_to(ROOT).as_posix() for p in ROOT.rglob('*') if p.is_file()
                  and not (set(p.relative_to(ROOT).parts) & GENERATED)
                  and p.name != 'local.properties')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, help='Audit every member of an existing source ZIP')
    args = parser.parse_args()
    failures, total = [], 0
    if args.archive:
        with zipfile.ZipFile(args.archive) as archive:
            for entry in archive.infolist():
                if entry.is_dir():
                    continue
                name = entry.filename
                if name.startswith('campus-usage/'):
                    name = name[len('campus-usage/'):]
                total += 1
                for category in check(name, archive.read(entry)):
                    failures.append((name, category))
    else:
        for name in source_files():
            total += 1
            for category in check(name, (ROOT/name).read_bytes()):
                failures.append((name, category))
    for name, category in failures:
        print(f'FAIL {name}: {category}')
    print(f'{total} source files checked; {len(failures)} findings')
    return bool(failures)

if __name__ == '__main__':
    sys.exit(main())
