#!/usr/bin/env python3
"""Claude Code PreToolUse hooks for Bash commands in this repo.

  git_hooks.py guard  Block git commands the workflow forbids (see CLAUDE.md "Workflow").
  git_hooks.py graph  Before `git commit` with changed app code, refresh the Graphify graph
                      and require the refreshed graph files to be staged in the same commit.

Input: the hook JSON on stdin (tool_input.command, cwd). A block exits with code 2 and a
message on stderr, which Claude Code shows to the model. Anything unparsable is allowed.
"""
import json
import os
import re
import shlex
import shutil
import subprocess
import sys

GRAPH_FILES = ["graphify-out/graph.json", "graphify-out/.graphify_analysis.json"]
GRAPH_SOURCE = "androidApp/src/main"
GRAPH_CODE_EXTENSIONS = (".kt", ".kts", ".java", ".c", ".cc", ".cpp", ".h", ".hpp")
GRAPH_REFRESH = ["graphify", "extract", GRAPH_SOURCE, "--code-only", "--out", "."]
OPERATORS = {";", "&&", "||", "|", "&", "\n"}
HEREDOC = re.compile(r"<<-?\s*(['\"]?)(\w+)\1[^\n]*\n.*?\n\s*\2\s*(?:\n|$)", re.S)


def segments(command):
    """Yield (program, args) for every simple command in a shell command line."""
    command = HEREDOC.sub("\n", command)  # heredoc bodies are data, not commands
    lexer = shlex.shlex(command, posix=True, punctuation_chars=";&|\n")
    lexer.whitespace = " \t\r"
    lexer.whitespace_split = True
    words = []
    for token in list(lexer) + [";"]:
        if token in OPERATORS or set(token) <= set(";&|\n"):
            while words and (re.fullmatch(r"\w+=.*", words[0]) or words[0] in ("command", "env", "time", "noglob")):
                words = words[1:]
            if words:
                yield os.path.basename(words[0]), words[1:]
            words = []
        else:
            words.append(token)


def git_segments(command):
    """Yield the argument list after `git` for every git invocation in the command."""
    for program, args in segments(command):
        if program != "git":
            continue
        while args and args[0] in ("-C", "-c", "--git-dir", "--work-tree"):
            args = args[2:]
        if args:
            yield args


def current_branch(cwd):
    result = subprocess.run(["git", "branch", "--show-current"], cwd=cwd, capture_output=True, text=True)
    return result.stdout.strip()


def block(message):
    print(message, file=sys.stderr)
    sys.exit(2)


def guard(command, cwd):
    commands = list(git_segments(command))
    switches_to_main = any(a[0] in ("switch", "checkout") and "main" in a[1:] for a in commands)
    for args in commands:
        sub, rest = args[0], args[1:]
        if sub == "add" and any(a in ("-A", "--all", ".", "-u", "--update", ":/") for a in rest):
            block("Blocked: stage files by explicit path only (no `git add -A`, `.`, `-u`).")
        short_flags = [a[1:] for a in rest if re.fullmatch(r"-[a-zA-Z]+", a)]
        if sub == "commit" and ("--all" in rest or any("a" in f for f in short_flags)):
            block("Blocked: `git commit -a` stages implicitly; stage files by explicit path, then commit.")
        if sub == "stash":
            block("Blocked: `git stash` is not allowed in this repo.")
        if sub == "clean":
            block("Blocked: `git clean` is not allowed in this repo (it deletes untracked build inputs).")
        if sub == "reset" and "--hard" in rest:
            block("Blocked: `git reset --hard` is not allowed; use `git reset --keep <commit>` or `git revert`.")
        if sub == "push":
            if any(a in ("-f", "--force", "--mirror", "--delete", "-d") or a.startswith("--force") or a.startswith("+") for a in rest):
                block("Blocked: no force, mirror or delete pushes.")
            refs = [a for a in rest if not a.startswith("-")][1:]  # drop the remote name
            if any(re.fullmatch(r"(?:.*:)?(?:refs/heads/)?main", r) for r in refs):
                block("Blocked: never push to `main`; push the feature branch.")
            if not refs and (current_branch(cwd) == "main" or switches_to_main):
                block("Blocked: the current branch is `main`; never push to `main`.")
        if sub == "merge" and (current_branch(cwd) == "main" or switches_to_main):
            block("Blocked: never merge into `main`; merging is the owner's decision.")
    if any(program == "gh" and args[:2] == ["pr", "merge"] for program, args in segments(command)):
        block("Blocked: never merge pull requests; merging is the owner's decision.")


def graph(command, cwd):
    if not any(args[0] == "commit" for args in git_segments(command)):
        return
    root = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=cwd, capture_output=True, text=True).stdout.strip()
    if not root:
        return
    status = subprocess.run(["git", "status", "--porcelain", "--untracked-files=all", "--", GRAPH_SOURCE],
                            cwd=root, capture_output=True, text=True).stdout
    changed = [line[3:] for line in status.splitlines() if line[3:].endswith(GRAPH_CODE_EXTENSIONS)]
    if not changed:
        return
    if shutil.which("graphify") is None:
        print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext":
              "graphify is not installed, so the graph could not be refreshed for this commit."}}))
        return
    refresh = subprocess.run(GRAPH_REFRESH, cwd=root, capture_output=True, text=True, timeout=110)
    if refresh.returncode != 0:
        block("Blocked: graph refresh failed (`" + " ".join(GRAPH_REFRESH) + "`):\n" + refresh.stderr[-800:])
    added = {a for args in git_segments(command) if args[0] == "add" for a in args[1:]}
    stale = [f for f in GRAPH_FILES
             if subprocess.run(["git", "diff", "--quiet", "--", f], cwd=root).returncode != 0 and f not in added]
    if stale:
        block("Blocked: app code changed, so the Graphify graph was refreshed. Stage it by path in this "
              "commit and commit again:\n  git add " + " ".join(stale))


def main():
    try:
        payload = json.load(sys.stdin)
        command = payload.get("tool_input", {}).get("command", "")
        cwd = payload.get("cwd") or os.getcwd()
    except (ValueError, AttributeError):
        return
    try:
        {"guard": guard, "graph": graph}[sys.argv[1]](command, cwd)
    except ValueError:  # unbalanced quotes etc.: not a command we can judge
        return


if __name__ == "__main__":
    main()
