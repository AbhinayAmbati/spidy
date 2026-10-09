#!/usr/bin/env python3
"""Catch cross-package symbols used without an import.

Kotlin resolves a symbol by simple name, so forgetting an import for one of our own types
is an easy mistake that only shows up at compile time - and there is no Kotlin compiler in
the dev sandbox, so it would otherwise surface only in CI.

Kotlin resolves a symbol by simple name, so forgetting an import for one of our own
types is an easy mistake that only shows up at compile time — and the sandbox has no
Kotlin compiler, so it would otherwise surface only in CI.

This checks a curated list of our internal, cross-package symbols. Names that also exist
in the Android/Compose libraries are deliberately excluded: those are imported from
elsewhere and would only produce false positives.

Exit code 1 if anything is missing, so it can gate a build.
"""
from __future__ import annotations

import os
import re
import sys

# symbol -> package that declares it
SYMBOLS: dict[str, str] = {}


def _add(pkg: str, names: str) -> None:
    for n in names.split():
        SYMBOLS[n] = pkg


_add("com.example.agent.core.ai",
     "AgentJson AgentActionCodec AgentToolSpec AIProvider AIProviderException AgentTool "
     "ValidationResult parseObjectOrEmpty")
_add("com.example.agent.domain.model",
     "AgentAction ActionTarget ToolNames AgentMessage AgentResponse MessageRole ToolResult "
     "TaskStep StepStatus RecordedStep TaskState TaskProgress ScreenSnapshot UIElement "
     "Bounds ActionRisk AwaitingKind MemoryEntry ElementKey inVisualOrder Routine RoutineStep")
_add("com.example.agent.automation.tools", "ToolSchemas ToolRegistry")
_add("com.example.agent.automation.accessibility", "TargetMatcher AccessibilityBridge UserTouchGate SettleGate")
_add("com.example.agent.automation", "AndroidAutomationManager")
_add("com.example.agent.core.agent", "ActionValidator PromptBuilder AgentRuntime ActionSignature UrlPolicy ContextCompactor")
_add("com.example.agent.data.repository", "RoutineRepository")
_add("com.example.agent.core.control", "AgentHalt")
_add("com.example.agent.core.permissions", "PermissionUtils")

# Library *extension* symbols that are invisible without an explicit import. Only pure
# extensions belong here: `encodeToString` / `decodeFromJsonElement` also exist as member
# functions taking an explicit serializer, which need no import, so flagging those would
# produce false positives.
LIB_EXTENSIONS: dict[str, str] = {
    "elementNames": "kotlinx.serialization.descriptors.elementNames",
}

DECL_RE = re.compile(
    r"^(?:internal |private |public )?(?:abstract |open |sealed |data |enum )?"
    r"(?:class|object|interface|fun|val|var)\s+([A-Za-z_]\w*)",
    re.M,
)


def strip_noncode(src: str) -> str:
    src = re.sub(r"//.*", "", src)
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    src = re.sub(r'"(?:[^"\\]|\\.)*"', '""', src)
    return src


def main(root: str) -> int:
    problems: list[tuple[str, str, str]] = []

    for dirpath, _, filenames in os.walk(root):
        for fn in filenames:
            if not fn.endswith(".kt"):
                continue
            path = os.path.join(dirpath, fn)
            src = open(path, encoding="utf-8").read()

            m = re.search(r"^package\s+([\w.]+)", src, re.M)
            if not m:
                continue
            pkg = m.group(1)

            imports = re.findall(r"^import\s+([\w.]+)", src, re.M)
            imported_simple = {i.rsplit(".", 1)[-1] for i in imports}

            body = strip_noncode("\n".join(
                line for line in src.splitlines()
                if not line.startswith("import") and not line.startswith("package")
            ))
            declared_here = set(DECL_RE.findall(src))

            for name, needed in LIB_EXTENSIONS.items():
                if needed in imports:
                    continue
                if not re.search(rf"\b{re.escape(name)}\b", body):
                    continue
                problems.append((path, name, f"library extension; add: import {needed}"))

            for name, home in SYMBOLS.items():
                if home == pkg:
                    continue
                if name in imported_simple:
                    continue
                if name in declared_here:
                    continue
                if not re.search(rf"\b{re.escape(name)}\b", body):
                    continue
                # Drop fully-qualified uses first, then look for a *bare* one. A file can
                # legitimately use a symbol fully qualified in one place and bare in
                # another, so this has to be per-occurrence, not per-file.
                bare = re.sub(rf"\b{re.escape(home)}\.{re.escape(name)}\b", "", body)
                if not re.search(rf"\b{re.escape(name)}\b", bare):
                    continue
                problems.append((path, name, home))

    for path, name, home in sorted(set(problems)):
        print(f"error: {path}: '{name}' is used but not imported (declared in {home})")
    if problems:
        print(f"\n{len(set(problems))} missing import(s)")
        return 1
    print("check_imports: all cross-package symbols are imported")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "app/src"))
