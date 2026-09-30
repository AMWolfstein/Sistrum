# Development setup on a new machine

Steps for a fresh Linux machine, in order. Versions are the ones this repo is built and tested
with; newer ones may work but are untested here.

## 1. Clone and local git excludes

The repo has no `.gitignore`; build outputs and machine-local files are excluded through
`.git/info/exclude`. After cloning, append:

```sh
git clone https://github.com/AMWolfstein/Sistrum.git && cd Sistrum
cat >> .git/info/exclude <<'EOF'
androidApp/src/main/jniLibs/
local.properties
.gradle/
.kotlin/
build/
androidApp/build/
sharedLogic/build/
.idea/
*.iml
.cxx/
androidApp/.cxx/
androidApp/src/main/res/font/material_symbols_rounded.ttf
.graphify/
*.graphify-bak
.claude/*
!.claude/skills/
!.claude/settings.json
!.claude/hooks/
!.claude/agents/
EOF
```

`graphify-out/` has its own tracked `.gitignore`.

## 2. JDK, Android SDK and `local.properties`

- JDK 21 (`java -version`).
- Android SDK with command-line tools, then:
  ```sh
  sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" \
             "ndk;30.0.15729638" "cmake;3.22.1"
  ```
- A Python venv for the icon-font build step (fonttools + brotli):
  ```sh
  python3 -m venv ~/.local/share/sistrum/fontenv
  ~/.local/share/sistrum/fontenv/bin/pip install fonttools==4.66.1 brotli==1.2.0
  ```
- `local.properties` in the repo root (untracked):
  ```properties
  sdk.dir=/home/<you>/Android/Sdk
  python3=/home/<you>/.local/share/sistrum/fontenv/bin/python3
  ```

## 3. FFmpeg libraries

The FFmpeg shared libraries are not in git. Build them once (downloads FFmpeg 8.1.2 and uses
NDK 30.0.15729638 from `sdk.dir`):

```sh
bash scripts/build-ffmpeg-android.sh arm64-v8a
```

Output goes to `androidApp/src/main/jniLibs/` (excluded). Then check the build:
`./gradlew :androidApp:assembleDevDebug`.

## 4. graphify (knowledge graph)

Install the verified 0.9.72 wheel in its own pipx environment, no extras:

```sh
d=$(mktemp -d)
pip download "graphifyy==0.9.72" --no-deps -d "$d"
echo "d193dcc43b07c9534826277fc92c8e54dc22aba102d58aaf424494a0906216c5  $d/graphifyy-0.9.72-py3-none-any.whl" | sha256sum -c -
pipx install "$d/graphifyy-0.9.72-py3-none-any.whl"
graphify --help >/dev/null && echo ok
```

Do NOT run `graphify install --project`: the skill (`.claude/skills/graphify/`) and its hooks
(`.claude/settings.json`) are already tracked, and the command would append a section to the
tracked `CLAUDE.md`. Don't run `graphify hook install` either (no git hooks). The graph
itself (`graphify-out/graph.json`) is tracked; refresh it with
`graphify extract androidApp/src/main --code-only --out .`.

## 5. Spec Kit

The project is already initialized (`.specify/` and the `speckit-*` skills are tracked), so the
skills work without the CLI. Install the CLI only to upgrade or re-run `specify` commands:

```sh
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git
specify --version   # this repo was initialized with 1.0.14.dev0
```

## 6. OpenCode and OpenCode Go

```sh
npm config set prefix ~/.npm-global           # if the default global prefix isn't writable
npm install -g opencode-ai@1.18.33
ln -s ~/.npm-global/bin/opencode ~/.local/bin/opencode   # ~/.local/bin must be on PATH
opencode --version
opencode auth login                            # choose "OpenCode Go"
opencode auth list                             # should list OpenCode Go
opencode models opencode-go | grep -E 'deepseek-v4.1-flash|glm-5.3-flash|deepseek-v4-pro'
```

In the OpenCode Go workspace's Privacy settings, select **Global** regions. Without it, the Go
models fail with "This Go model requires Global regions".

## 7. delegate-skills (opencode-delegate)

Install the `opencode-delegate` skill globally for Claude Code (it lands in
`~/.claude/skills/opencode-delegate/`, where `.claude/skills/delegate-task/scripts/dispatch.sh`
expects `scripts/relay.mjs`):

```sh
npx skills add amElnagdy/delegate-skills --skill opencode-delegate --agent claude-code -g -y
ls ~/.claude/skills/opencode-delegate/scripts/relay.mjs
```

If you install it elsewhere, set `OPENCODE_RELAY=/path/to/relay.mjs` for `dispatch.sh`.

## 8. Claude Code

- Open the repo in Claude Code and approve the project MCP server `context7` (`.mcp.json`)
  when asked. It runs through `npx`, so Node must be installed.
- Tracked project config: `.claude/settings.json` (graphify and git hooks),
  `.claude/hooks/`, `.claude/agents/`, `.claude/skills/`. The hooks need `python3` and
  `graphify` on PATH.
- Workflow rules live in `CLAUDE.md`; the implementer's rules in `AGENTS.md`.
