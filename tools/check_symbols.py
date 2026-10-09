"""Catches a symbol used without its import.

This exists because I shipped a compile error of exactly this kind (`viewModelScope` used in
a ViewModel with no import) and only found it in CI. `check_imports.py` covers cross-package
*model* references; this covers the far larger set of framework and Compose symbols.

Deliberately conservative: it strips comments and raw strings, and treats a declaration in
the same package as satisfying the reference, so it only reports things that are really
missing.
"""
import os
import re
import sys

# symbol -> the import that provides it
NEEDS = {
    "viewModelScope": "androidx.lifecycle.viewModelScope",
    "hiltViewModel": "androidx.hilt.navigation.compose.hiltViewModel",
    "collectAsStateWithLifecycle": "androidx.lifecycle.compose.collectAsStateWithLifecycle",
    "stateIn": "kotlinx.coroutines.flow.stateIn",
    "SharingStarted": "kotlinx.coroutines.flow.SharingStarted",
    "asStateFlow": "kotlinx.coroutines.flow.asStateFlow",
    "MutableStateFlow": "kotlinx.coroutines.flow.MutableStateFlow",
    "StateFlow": "kotlinx.coroutines.flow.StateFlow",
    "UUID": "java.util.UUID",
    "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "rememberScrollState": "androidx.compose.foundation.rememberScrollState",
    "Arrangement": "androidx.compose.foundation.layout.Arrangement",
    "stringResource": "androidx.compose.ui.res.stringResource",
    "GlassCard": "com.example.agent.ui.components.GlassCard",
    "LabelValueRow": "com.example.agent.ui.components.LabelValueRow",
    "SectionHeader": "com.example.agent.ui.components.SectionHeader",
    "PrimaryButton": "com.example.agent.ui.components.PrimaryButton",
    "SecondaryButton": "com.example.agent.ui.components.SecondaryButton",
    "SpideySpacing": "com.example.agent.ui.theme.SpideySpacing",
    "spideyColors": "com.example.agent.ui.theme.spideyColors",
    "ApplicationContext": "dagger.hilt.android.qualifiers.ApplicationContext",
    "HiltViewModel": "dagger.hilt.android.lifecycle.HiltViewModel",
    "preferencesDataStore": "androidx.datastore.preferences.preferencesDataStore",
    "stringPreferencesKey": "androidx.datastore.preferences.core.stringPreferencesKey",
    "JsonPrimitive": "kotlinx.serialization.json.JsonPrimitive",
    "JsonObject": "kotlinx.serialization.json.JsonObject",
    "JsonArray": "kotlinx.serialization.json.JsonArray",
    "JsonElement": "kotlinx.serialization.json.JsonElement",
    "JsonNull": "kotlinx.serialization.json.JsonNull",
}


def strip_comments_and_strings(src: str) -> str:
    out = []
    i, n = 0, len(src)
    while i < n:
        if src[i:i + 3] == '"""':
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
            continue
        if src[i:i + 2] == "//":
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if src[i:i + 2] == "/*":
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        c = src[i]
        if c == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if c == "`":
            j = src.find("`", i + 1)
            i = n if j < 0 else j + 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def main(root: str) -> int:
    problems = []

    # every symbol declared anywhere in the tree, so same-package refs are not flagged
    declared = set()
    files = []
    for dirpath, _, filenames in os.walk(root):
        for f in filenames:
            if f.endswith(".kt"):
                path = os.path.join(dirpath, f)
                files.append(path)
                body = strip_comments_and_strings(open(path).read())
                declared |= set(
                    re.findall(r"\b(?:fun|val|var|class|object|enum class|interface)\s+(\w+)", body)
                )

    for path in files:
        raw = open(path).read()
        body = strip_comments_and_strings(raw)
        package = re.search(r"^package\s+([\w.]+)", raw, re.M)
        package = package.group(1) if package else ""
        for symbol, imp in NEEDS.items():
            if not re.search(rf"\b{symbol}\b", body):
                continue
            if f"import {imp}" in raw:
                continue
            # fully qualified in place, declared in this file, or declared in this package
            if (imp + "(") in body or (imp + ".") in body:
                continue
            if symbol in declared and imp.startswith(package + "."):
                continue
            if symbol in declared and package == imp.rsplit(".", 1)[0]:
                continue
            if re.search(rf"(fun|val|var|class|object|interface)\s+{symbol}\b", body):
                continue
            problems.append(f"{path}: {symbol} used without `import {imp}`")

    for p in sorted(set(problems)):
        print(p)
    print("missing-import issues:", len(set(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "app/src"))
