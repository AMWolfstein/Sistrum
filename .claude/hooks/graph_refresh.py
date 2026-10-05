#!/usr/bin/env python3
"""Graphify graph scope, refresh and no-op filter (CLAUDE.md "Graph updates").

  graph_refresh.py changed   Print the changed in-scope source files (vs HEAD, untracked included).
                             Exit 0 if there are any, 1 if none.
  graph_refresh.py refresh   Refresh the graph. Prints "graph: changed (...)" or "graph: unchanged (...)".
  graph_refresh.py auto      `refresh` only if `changed` finds something or the graph files differ from HEAD;
                             otherwise print "graph: not needed".

Scope (keep in sync with the repo-root `.graphifyignore`, which tells graphify the same thing):
source files of every source set under androidApp/src and sharedLogic/src (minus res, jniLibs,
resources, assets, composeResources, third_party, build and generated directories). Docs, specs, scripts, tools,
resources and build outputs never trigger a refresh.

Clean build: `graphify extract` "heals" from an existing graph.json in its output directory and never
prunes external stub nodes (no source file), so refreshing in place accumulates stale nodes and the result
depends on history. The refresh therefore builds in a private directory (graphify-out/build/, git-ignored;
its AST cache stays warm) after deleting that directory's previous graph, then copies graph.json and
.graphify_analysis.json into graphify-out/. Same sources -> same graph.

No-op filter: graphify writes the current git HEAD into graph.json's top-level `built_at_commit`, so a
refresh after any commit rewrites the file even when no code changed. If the refreshed graph equals HEAD's
apart from that field (and the analysis file is equal too), both files are reset to HEAD (index and work
tree), so nothing is staged.
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

SCOPE = ("androidApp/src/", "sharedLogic/src/")  # every source set (main, test, androidTest, qa, dev, ...)
EXCLUDED_PARTS = ("/res/", "/jniLibs/", "/build/", "/generated/", "/resources/", "/assets/", "/.cxx/",
                  "/composeResources/", "/third_party/")
CODE_EXTENSIONS = (".kt", ".kts", ".java", ".c", ".cc", ".cpp", ".h", ".hpp")
TRIGGERS = (".graphifyignore",)  # a scope change is a graph change
GRAPH_FILES = ("graphify-out/graph.json", "graphify-out/.graphify_analysis.json")
BUILD_DIR = "graphify-out/build"  # private output dir: <BUILD_DIR>/graphify-out/{graph.json,cache,...}
REFRESH = ["graphify", "extract", ".", "--code-only", "--out", BUILD_DIR]
NOISE_FIELDS = ("built_at_commit",)


def git(root, *args, check=True):
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=check).stdout


def repo_root(cwd="."):
    return git(cwd, "rev-parse", "--show-toplevel").strip()


def in_scope(path):
    if path in TRIGGERS:
        return True
    return (path.startswith(SCOPE) and path.endswith(CODE_EXTENSIONS)
            and not any(part in "/" + path for part in EXCLUDED_PARTS))


def changed_files(root):
    paths = set(git(root, "diff", "--name-only", "--no-renames", "HEAD").splitlines())
    paths |= set(git(root, "ls-files", "--others", "--exclude-standard").splitlines())
    return sorted(p for p in paths if in_scope(p))


def needs_refresh(root):
    """In-scope source changed, or the graph files differ from HEAD (e.g. left over from a reverted change)."""
    return bool(changed_files(root)) or bool(git(root, "diff", "--name-only", "HEAD", "--", *GRAPH_FILES).strip())


def head_json(root, path):
    shown = subprocess.run(["git", "show", f"HEAD:{path}"], cwd=root, capture_output=True, text=True)
    return json.loads(shown.stdout) if shown.returncode == 0 else None


def without_noise(data):
    return {k: v for k, v in data.items() if k not in NOISE_FIELDS} if isinstance(data, dict) else data


def refresh(root, timeout=110):
    """Run the refresh; reset the graph files to HEAD when only noise changed. Returns (changed, message)."""
    built = Path(root) / BUILD_DIR / "graphify-out"
    for name in GRAPH_FILES:
        (built / Path(name).name).unlink(missing_ok=True)  # no healing from the previous graph
    run = subprocess.run(REFRESH, cwd=root, capture_output=True, text=True, timeout=timeout)
    if run.returncode != 0:
        raise RuntimeError("`" + " ".join(REFRESH) + "` failed:\n" + run.stderr[-800:])
    for name in GRAPH_FILES:
        shutil.copyfile(built / Path(name).name, Path(root) / name)
    current = [json.loads((Path(root) / f).read_text()) for f in GRAPH_FILES]
    nodes, links = len(current[0].get("nodes", [])), len(current[0].get("links", []))
    previous = [head_json(root, f) for f in GRAPH_FILES]
    if all(p is not None for p in previous) and \
            all(without_noise(c) == without_noise(p) for c, p in zip(current, previous)):
        git(root, "checkout", "HEAD", "--", *GRAPH_FILES)
        return False, f"graph: unchanged ({nodes} nodes, {links} edges; only {', '.join(NOISE_FIELDS)} differed, reset to HEAD)"
    return True, f"graph: changed ({nodes} nodes, {links} edges)"


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    root = repo_root()
    if command == "changed":
        files = changed_files(root)
        print("\n".join(files))
        sys.exit(0 if files else 1)
    if command == "auto" and not needs_refresh(root):
        print("graph: not needed (no source change in scope)")
        return
    if command in ("refresh", "auto"):
        try:
            print(refresh(root)[1])
        except (RuntimeError, subprocess.TimeoutExpired) as error:
            print(f"graph: refresh FAILED: {error}", file=sys.stderr)
            sys.exit(1)
        return
    print(__doc__, file=sys.stderr)
    sys.exit(2)


if __name__ == "__main__":
    main()
