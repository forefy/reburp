#!/usr/bin/env python3
"""Check that each Codex skill stub matches the Claude Code skill it points to.

Skill content lives once, under .claude/skills/<name>. A Codex agent finds skills through
.codex/skills/<name>/SKILL.md, whose frontmatter it uses to decide when to load the skill and
whose body sends it to the Claude copy. The frontmatter is the one part written twice, so this
fails when a stub's frontmatter or pointer drifts from its target.

Usage: python3 tools/check_skill_stubs.py   (exit 1 on any mismatch)
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent


def frontmatter(path):
    text = path.read_text(encoding="utf-8")
    if not text.startswith("---\n"):
        return None, text
    head, _, body = text[4:].partition("\n---\n")
    return head.strip(), body


def main():
    problems = []
    stubs = sorted((ROOT / ".codex" / "skills").glob("*/SKILL.md"))
    for stub in stubs:
        name = stub.parent.name
        target = ROOT / ".claude" / "skills" / name / "SKILL.md"
        rel = target.relative_to(ROOT).as_posix()
        if not target.exists():
            problems.append(f"{stub.relative_to(ROOT)}: no {rel} to point to")
            continue
        stub_head, stub_body = frontmatter(stub)
        target_head, _ = frontmatter(target)
        if stub_head != target_head:
            problems.append(f"{stub.relative_to(ROOT)}: frontmatter differs from {rel}; copy it verbatim")
        if rel not in stub_body:
            problems.append(f"{stub.relative_to(ROOT)}: body does not point to {rel}")
    for problem in problems:
        print(problem)
    print(f"{len(stubs)} Codex skill stub(s) checked, {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
