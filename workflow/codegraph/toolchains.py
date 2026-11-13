"""Read toolchain versions from the repository's own pin files, not copies."""
from pathlib import Path
import re


def rust_channel(root):
    """Channel pinned by rust_backend/rust-toolchain.toml, or None."""
    try: text=(Path(root)/'rust_backend/rust-toolchain.toml').read_text(encoding='utf-8')
    except OSError: return None
    found=re.search(r'^\s*channel\s*=\s*"([^"]+)"',text,re.M)
    return found.group(1) if found else None


def jdk_version(root):
    """Major JDK pinned by android/app/build.gradle.kts (JavaVersion.VERSION_25), or None."""
    try: text=(Path(root)/'android/app/build.gradle.kts').read_text(encoding='utf-8')
    except OSError: return None
    found=re.search(r'JavaVersion\.VERSION_(\d+)',text)
    return int(found.group(1)) if found else None
