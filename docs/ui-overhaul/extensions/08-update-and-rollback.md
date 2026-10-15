# Extension lifecycle, updates and rollback

**VERIFIED IN SOURCE:** M04/M05 in [source map](../01-reference-source-map.md): pinned mobile `ExtensionManager.install/set_enabled/initialize/cleanup`, manifest gates and verified package staging exist upstream. Do not infer Sistrum inherits them. Every transition/control below is **PROPOSED**.

## State model

Track installation state, enable intention and operation state separately; this avoids treating `UPDATE_AVAILABLE` as “currently not active.” User-facing lifecycle includes required sequence:

`DISCOVERED → INSTALLED → ENABLED → UPDATE_AVAILABLE → UPDATING → VERIFIED → ACTIVE`.

Initial install also validates before INSTALLED, and first enable can reach ACTIVE without a remote update. DISABLED preserves package/settings; INCOMPATIBLE explains failed host/permissions/required-feature gates; FAILED retains prior version and cause; ROLLED_BACK identifies active prior digest; REMOVED clears code/KV/secrets and cancels generation. A discovered unsigned/unapproved package can be listed without execution. Installed means verified storage, not permission to execute. An update badge never evaluates code.

## Update protocol

1. Fetch bundled-default registry with 24 h conditional checks (ETag/Last-Modified when supplied); cache last good data; manual refresh bounded. Checks default ON, automatic code downloads/updates OFF. Never auto-install/execute.
2. Strict schema/hash/version/URL validation; verify registry/publisher authenticity where supported. Current unsigned snapshot lacks that signal; require separate trusted approval policy for executable packages and show status accurately.
3. Compare canonical installed origin/id + version/hash. Same-version changed bytes is a security change, not a silent update. Record min-app versus required host profile separately.
4. User initiates Update after reviewing version, source, known compatibility, rights and permission delta. No approval means prior active version continues unchanged.
5. Stage download with quotas, HTTPS/address/redirect checks; verify package hash and trusted approval signature/digest, then manifest/version/identity/member/required-feature checks. Interrupted download never changes active pointer.
6. Permission increases (domains/wildcards/KV/credentials/host methods) require fresh consent and compatibility review. Do not silently turn an unsupported file permission into a stub and call package compatible.
7. Compatibility validation only after explicit consent, in an isolated candidate runtime with denied real credentials/library and controlled broker. No validation evaluation at discovery. Successful validation is not a grant to execute arbitrary methods.
8. Preserve previous immutable known-good package plus matching settings version/snapshot. Commit journal `candidate_verified → activating`; drain/cancel old generation, retire its runtime and swap active pointer atomically in private app storage. Settings migration must be declarative or trusted app code; no unapproved installer hooks.
9. Start candidate only if previously enabled and user-approved. Health failure restores pointer/settings snapshot, logs cause and displays FAILED/ROLLED_BACK. Disabled packages stay disabled after update/rollback. Clean staging after durable pointer verification.
10. Rollback is explicit to one previously trusted version with compatible host API and known settings schema; no downgrade from registry alone. If previous version revoked/unsafe, disable and explain rather than run it.

Crash recovery replays journal before activation; old pointer remains authority until durable commit. If neither active nor previous passes verification, state is FAILED/DISABLED with static/local fallback. No remote package can replace APK code, shared libraries or app data. Uninstall cancels in-flight calls and invalidates broker sessions before deleting versions; late replies cannot repopulate caches.

## Breaking-change detection

CI compatibility corpus pins package hashes/manifests/recorded broker fixtures. Registry refresh identifies changes, but new hashes are never considered tested. Detect removed/renamed methods, requiredRuntimeFeatures/minAppVersion changes, output schema/timing changes and domains/permissions. Report incompatible/update-blocked reason in UI. Manually promote only after tests/security/license review. User can inspect current/prior version and errors and clear provider caches independently.

## UNKNOWN / NEEDS VALIDATION / BLOCKED

No upstream registry signing authority was verified. Trust/catalog ownership, key rotation/revocation, JVM/runtime choice and package contributor rights need decision. General activation remains BLOCKED until those gates. Tests must cover process death at every transition, failed validation, full disk, disabled state persistence, same-version mutation, rollback settings compatibility and permission escalation. None ran during Phase 0.
