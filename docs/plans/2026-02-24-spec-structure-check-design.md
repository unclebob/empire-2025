# Spec Structure Checker — Design

## Purpose

A Clojure tool that Claude runs automatically after editing spec files to detect parenthesis and Speclj structural errors. Primary goal: catch `(it)` nested inside `(it)` and similar structural mistakes that silently swallow tests.

## Approach

Text-based state machine scanner. No Clojure reader — it compresses code and doesn't preserve line numbers, and balanced parens don't imply correct Speclj structure anyway.

## Invocation

```bash
clj -M:spec-structure-check spec/empire/combat_spec.clj
clj -M:spec-structure-check spec/   # batch mode, scans all .clj files
```

## Output

**Success:** `OK`

**Errors:** One line per error, minimal tokens.
```
ERROR line 52: (it) inside (it) at line 42
ERROR line 87: unclosed (describe) from line 60
```

**Optional `--tree` flag:** Prints indented Speclj form tree with line numbers.

## Speclj Structural Rules

Speclj files have a simple 2-3 layer structure:

| Level | Form | Allowed children |
|---|---|---|
| 1 (top) | `describe` | `it`, `context`, `before`, `before-all`, `after`, `with-stubs`, `with`, `around` |
| 2 | `context` | `it`, `before`, `before-all`, `after`, `with-stubs`, `with`, `around` |
| 2-3 | `it` | none — any structural form inside is an error |

**Key constraints:**
- `describe` blocks are top-level only — never nested
- `context` blocks appear only inside `describe` — never nested
- `it` blocks appear inside `describe` or `context` — never nested
- No structural Speclj form may appear inside `(it)`

## Algorithm

Single-pass character scanner maintaining:
- `depth` — paren depth
- `line` — current line number
- `stack` — vector of `{:form keyword, :line N, :depth N}` for open Speclj forms
- `errors` — collected errors
- `mode` — `:normal`, `:string`, `:comment`, `:char-literal`, `:regex`

Character handling:
- `\n` — bump line, exit `:comment`
- `\\` — skip next char (character literal)
- `"` — toggle `:string` (respecting `\"` escapes)
- `;` — enter `:comment`
- `#"` — enter `:regex` (skip until unescaped `"`)
- `(` in `:normal` — increment depth, look ahead for Speclj keyword
- `)` in `:normal` — decrement depth, pop stack if depth matches tracked form

When `(` precedes a Speclj keyword:
- If parent on stack is `:it`, emit error
- Push form onto stack

At EOF:
- Non-empty stack = unclosed forms
- Depth != 0 = unbalanced parens

## Implementation

- **Source:** `src/empire/paren_check/core.cljc`
- **Alias:** `:spec-structure-check` in `deps.edn`
- **Spec:** `spec/empire/paren_check/core_spec.clj`
