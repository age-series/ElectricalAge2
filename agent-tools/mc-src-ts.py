#!/usr/bin/env python3
"""
mc-src-ts.py - Minecraft/Forge source reader using tree-sitter.

Reads Parchment-remapped source from the ForgeGradle build cache.
Run `./gradlew build` or `./gradlew setup` before first use.

Setup:
    pip install tree-sitter tree-sitter-java

    Without tree-sitter, `glob` and `method` fall back to regex-based
    extraction (less accurate, no javadoc capture).

Commands:
    read <class-name> [--lines M-N]
        Dump source or a line range of a class.
        Class names: FQN, short name (auto-resolves), or Outer.Inner.

    method <class-name> <method-name> [--lines M-N]
        Extract a method body with preceding javadoc.

    grep <pattern> [--max N] [--context M] [--class C] [-F]
        Search source files. -F for literal, --class scopes search.

    list [<package>]
        List classes under a package prefix.

    find <name>
        Find classes by substring (case-insensitive).

    glob <class-name> [flags...]
        List class members (methods, fields, constructors, inner types).

        Category filters (default: all):
            --methods       Methods only
            --fields        Fields only
            --ctors         Constructors only
            --inner-types   Inner types only

        Visibility filters (default: public + protected):
            --public        Include public
            --private       Include private
            --protected     Include protected
            --package       Include package-private

        Other filters:
            --static        Only static members
            --no-static     Only instance members
            --filter <str>  Substring match on member name

        Class names support short names (if unique) and Outer.Inner syntax.
"""

import glob as globmod
import os
import re
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

# Tree-sitter (graceful fallback if not installed)
_TS_AVAILABLE = False
try:
    import tree_sitter_java as tsjava
    from tree_sitter import Parser, Language
    _TS_PARSER = Parser(Language(tsjava.language()))
    _TS_AVAILABLE = True
except ImportError:
    pass


PROJECT_MARKERS = ["build.gradle", "settings.gradle", "gradlew"]


def find_project_root(start: Path) -> Optional[Path]:
    for parent in [start] + list(start.parents)[:32]:
        if any((parent / m).exists() for m in PROJECT_MARKERS):
            return parent
    return None


@dataclass
class SourceJar:
    jar_path: Path

    @classmethod
    def discover(cls) -> "SourceJar":
        cwd = Path.cwd().resolve()
        root = find_project_root(cwd)
        if root is None:
            print("Error: could not find project root (no build.gradle found)", file=sys.stderr)
            sys.exit(1)
        pattern = str(root / "build/fg_cache/net/minecraftforge/forge/*_mapped_parchment_*/forge-*-sources.jar")
        matches = sorted(globmod.glob(pattern))
        if not matches:
            print("Error: no Parchment-remapped sources jar found in build cache.", file=sys.stderr)
            print("  Looked for: build/fg_cache/net/minecraftforge/forge/*_mapped_parchment_*/forge-*-sources.jar", file=sys.stderr)
            print("Run './gradlew build' or './gradlew setup' first.", file=sys.stderr)
            sys.exit(1)
        return cls(jar_path=Path(matches[0]))

    def open(self) -> zipfile.ZipFile:
        return zipfile.ZipFile(self.jar_path, "r")

    def path_for_class(self, fqn: str) -> str:
        """Convert 'net.minecraft.Foo' -> 'net/minecraft/Foo.java'.
        For inner classes 'net.minecraft.Foo.Bar' -> 'net/minecraft/Foo.java'."""
        outer_fqn, _ = _class_chain(fqn) if "." in fqn else (fqn, [fqn])
        return outer_fqn.replace(".", "/") + ".java"

    def read_entry(self, entry_path: str) -> Optional[str]:
        try:
            with self.open() as zf:
                return zf.read(entry_path).decode("utf-8")
        except KeyError:
            return None

    def resolve_name(self, name: str) -> str:
        """Resolve a short class name to FQN. Only top-level classes (no $)."""
        if "." in name:
            return name  # already qualified
        matches = []
        with self.open() as zf:
            for entry in zf.namelist():
                if not entry.endswith(".java"):
                    continue
                if "$" in entry:
                    continue
                simple = entry.rsplit("/", 1)[-1].removesuffix(".java")
                if simple == name:
                    fqn = entry.removesuffix(".java").replace("/", ".")
                    matches.append(fqn)
        if len(matches) == 0:
            return name  # let caller handle the error
        if len(matches) == 1:
            return matches[0]
        # Ambiguous: print candidates and exit
        print(f"Ambiguous class name '{name}'. Candidates:", file=sys.stderr)
        for m in sorted(matches):
            print(f"  {m}", file=sys.stderr)
        sys.exit(1)

    def grep(self, pattern: str, max_results: int = 20, context_lines: int = 0,
             class_filter: Optional[str] = None, fixed_strings: bool = False) -> list[dict]:
        results = []
        # Accept both \| and | as alternation (agents habit from grep basic mode)
        if not fixed_strings:
            pattern = pattern.replace(r"\|", "|")
        compiled = re.compile(re.escape(pattern)) if fixed_strings else re.compile(pattern)
        with self.open() as zf:
            for info in zf.infolist():
                if not info.filename.endswith(".java"):
                    continue
                if class_filter and class_filter.replace(".", "/") not in info.filename:
                    continue
                content = zf.read(info.filename).decode("utf-8")
                lines = content.splitlines()
                for i, line in enumerate(lines):
                    if compiled.search(line):
                        start = max(0, i - context_lines)
                        end = min(len(lines), i + context_lines + 1)
                        context = lines[start:end]
                        results.append({
                            "file": info.filename,
                            "line": i + 1,
                            "context_start": start + 1,
                            "lines": context,
                            "match_idx": i - start,
                        })
                        if len(results) >= max_results:
                            return results
        return results

    def method_source_ts(self, fqn: str, method_name: str) -> Optional[tuple[int, str]]:
        """Extract a method (with javadoc) from a class using tree-sitter."""
        if not _TS_AVAILABLE:
            return None
        entry = self.path_for_class(fqn)
        content_str = self.read_entry(entry)
        if content_str is None:
            return None
        content_bytes = content_str.encode("utf-8")
        tree = _TS_PARSER.parse(content_bytes)
        root = tree.root_node

        simple_name = fqn.split(".")[-1]
        is_constructor = (method_name == simple_name)

        def collect(node, matches):
            """Walk the tree, collect all matching method/constructor nodes."""
            if is_constructor and node.type == "constructor_declaration":
                matches.append(node)
                return
            if not is_constructor and node.type == "method_declaration":
                name_node = node.child_by_field_name("name")
                if name_node:
                    name = content_bytes[name_node.start_byte:name_node.end_byte].decode()
                    if name == method_name:
                        matches.append(node)
                return
            if node.type in ("class_declaration", "interface_declaration",
                             "enum_declaration", "record_declaration"):
                body = node.child_by_field_name("body")
                if body:
                    for child in body.children:
                        collect(child, matches)

        matches = []
        for child in root.children:
            collect(child, matches)

        if len(matches) == 0:
            return None

        if len(matches) > 1:
            # Print overloaded candidates and fail (don't fall back to regex)
            print(f"Multiple matches for '{method_name}' in {simple_name}:", file=sys.stderr)
            for m in matches:
                sl = content_str[:m.start_byte].count("\n") + 1
                if m.type == "constructor_declaration":
                    label = simple_name
                else:
                    name_node = m.child_by_field_name("name")
                    label = content_bytes[name_node.start_byte:name_node.end_byte].decode() if name_node else "?"
                params_node = m.child_by_field_name("parameters")
                if params_node:
                    param_parts = []
                    for p in params_node.children:
                        if p.type == "formal_parameter":
                            t = p.child_by_field_name("type")
                            tn = _node_text(t, content_bytes) if t else "?"
                            param_parts.append(tn)
                    sig = ", ".join(param_parts)
                else:
                    sig = ""
                print(f"  {sl:5d}: {label}({sig})", file=sys.stderr)
            sys.exit(1)

        target = matches[0]

        # Find preceding javadoc (walk backward through siblings)
        parent = target.parent
        doc_start = target.start_byte
        if parent:
            siblings = list(parent.children)
            try:
                idx = siblings.index(target)
                for sibling in siblings[idx - 1::-1]:
                    stype = sibling.type
                    if stype == "block_comment":
                        doc_start = sibling.start_byte
                        break
                    if stype in ("marker_annotation", "annotation", "line_comment"):
                        doc_start = sibling.start_byte
                        continue
                    break
            except ValueError:
                pass

        def line_of(byte_offset):
            return content_str[:byte_offset].count("\n") + 1

        start_line = line_of(doc_start)
        source = content_str[doc_start:target.end_byte]
        return start_line, source


# =====================================================================
#  Command: read
# =====================================================================

def cmd_read(args: list[str]) -> None:
    import argparse
    p = argparse.ArgumentParser("mc-src.py read")
    p.add_argument("class_name", help="Fully qualified class name")
    p.add_argument("--lines", "-l", help="Line range like 10-20 or 50-")
    opts = p.parse_args(args)

    jar = SourceJar.discover()
    entry = jar.path_for_class(opts.class_name)
    content = jar.read_entry(entry)
    if content is None:
        print(f"Class '{opts.class_name}' not found in sources jar.")
        print(f"  Expected entry: {entry}")
        pkg_prefix = ".".join(opts.class_name.split(".")[:-1]).replace(".", "/")
        with jar.open() as zf:
            matches = [n for n in zf.namelist() if n.startswith(pkg_prefix) and n.endswith(".java")]
        if matches:
            print(f"  Files under {pkg_prefix}:")
            for m in sorted(matches)[:20]:
                show = m.removesuffix(".java").replace("/", ".")
                print(f"    {show}")
        sys.exit(1)

    if opts.lines:
        parts = opts.lines.split("-")
        start = int(parts[0]) - 1
        end = int(parts[1]) if parts[1] else None
        lines = content.splitlines()
        chunk = lines[start:end]
        for i, line in enumerate(chunk, start=start + 1):
            print(f"{i:5d}:{line}")
    else:
        lines = content.splitlines()
        total = len(lines)
        cap = 100
        if total <= cap:
            for i, line in enumerate(lines, start=1):
                print(f"{i:5d}:{line}")
        else:
            for i, line in enumerate(lines[:cap], start=1):
                print(f"{i:5d}:{line}")
            print(f"\n... output truncated: showing first {cap} of {total} lines ...")
            print(f"(use --lines {cap + 1}- to see the rest, or --lines N-M for a range)")


# =====================================================================
#  Command: grep
# =====================================================================

def cmd_grep(args: list[str]) -> None:
    import argparse
    p = argparse.ArgumentParser("mc-src.py grep")
    p.add_argument("pattern", help="Regex pattern to search")
    p.add_argument("--max", "-m", type=int, default=20, help="Max results")
    p.add_argument("--context", "-C", type=int, default=0, help="Context lines around match")
    p.add_argument("--class", "-c", dest="class_filter", help="Substring match on class/package path")
    p.add_argument("-F", "--fixed-strings", action="store_true", help="Treat pattern as literal text")
    opts = p.parse_args(args)

    jar = SourceJar.discover()
    results = jar.grep(opts.pattern, max_results=opts.max, context_lines=opts.context,
                       class_filter=opts.class_filter, fixed_strings=opts.fixed_strings)

    if not results:
        print(f"No matches for '{opts.pattern}'")
        return

    if opts.context > 0:
        for r in results:
            cls = r["file"].removesuffix(".java").replace("/", ".")
            print(f"\n--- {cls}:{r['context_start']}-{r['context_start'] + len(r['lines']) - 1} ---")
            for i, line in enumerate(r["lines"]):
                line_num = r["context_start"] + i
                marker = ">" if i == r["match_idx"] else " "
                print(f" {marker} {line_num:4d}:{line}")
    else:
        for r in results:
            cls = r["file"].removesuffix(".java").replace("/", ".")
            for i, line in enumerate(r["lines"]):
                print(f"{cls}:{r['line']}:{line}")
            # r["lines"] is a single-element list when context == 0

    plural = "s" if len(results) > 1 else ""
    print(f"\n{len(results)} result{plural}")


# =====================================================================
#  Command: list
# =====================================================================

def cmd_list(args: list[str]) -> None:
    import argparse
    p = argparse.ArgumentParser("mc-src.py list")
    p.add_argument("package", nargs="?", default="", help="Package prefix (optional)")
    opts = p.parse_args(args)
    prefix = opts.package.replace(".", "/")
    if prefix and not prefix.endswith("/"):
        prefix += "/"
    jar = SourceJar.discover()
    seen = set()
    matches = []
    with jar.open() as zf:
        for name in sorted(zf.namelist()):
            if not name.endswith(".java"):
                continue
            if prefix and not name.startswith(prefix):
                continue
            cls = name.removesuffix(".java").replace("/", ".")
            if cls not in seen:
                matches.append(cls)
                seen.add(cls)
    if not matches:
        print(f"No classes in package '{opts.package}'")
        return
    cap = 50
    for cls in matches[:cap]:
        print(cls)
    if len(matches) > cap:
        print(f"\n... and {len(matches) - cap} more — narrow your query with a longer package prefix ...")


# =====================================================================
#  Command: find
# =====================================================================

def cmd_find(args: list[str]) -> None:
    import argparse
    p = argparse.ArgumentParser("mc-src.py find")
    p.add_argument("name", help="Substring to search in class names")
    opts = p.parse_args(args)
    pat = re.compile(re.escape(opts.name), re.IGNORECASE)
    jar = SourceJar.discover()
    matches = []
    with jar.open() as zf:
        for name in sorted(zf.namelist()):
            if not name.endswith(".java"):
                continue
            if pat.search(name):
                matches.append(name.removesuffix(".java").replace("/", "."))
    if not matches:
        print(f"No classes matching '{opts.name}'")
        return
    for cls in matches:
        print(cls)


# =====================================================================
#  Command: method (tree-sitter + regex fallback)
# =====================================================================

def _method_source_regex(lines: list[str], method_name: str) -> Optional[tuple[int, str]]:
    """Regex-based method extraction (fallback when tree-sitter unavailable)."""
    method_pat = re.compile(rf"\b{re.escape(method_name)}\s*\(")
    for i, line in enumerate(lines):
        m = method_pat.search(line)
        if not m:
            continue
        stripped_line = line.strip()
        if stripped_line.startswith("*") or stripped_line.startswith("/*") or stripped_line.startswith("//"):
            continue
        prefix = line[:m.start()].strip()
        is_decl = any(kw in prefix for kw in (
            "public", "private", "protected", "static", "final",
            "abstract", "synchronized", "void", "int", "long",
            "double", "float", "boolean", "char", "byte", "short",
            "String", "<", ">", "@",
        ))
        if not is_decl:
            if i > 0:
                prev = lines[i - 1].strip()
                if any(kw in prev for kw in ("public", "private", "protected", "static", "final", "abstract", "<", ">", "@")):
                    is_decl = True
        if not is_decl:
            continue
        doc_start = i
        while doc_start > 0:
            doc_start -= 1
            stripped = lines[doc_start].strip()
            if stripped.startswith("//") or stripped.startswith("@") or stripped == "":
                continue
            if stripped.startswith("*") or stripped == "/**" or stripped == "*/":
                while doc_start > 0 and not lines[doc_start].strip() == "/**":
                    doc_start -= 1
                break
            doc_start += 1
            break
        brace_depth = 0
        end = i
        for j in range(i, len(lines)):
            end = j
            brace_depth += lines[j].count("{") - lines[j].count("}")
            if brace_depth == 0 and j > i:
                break
        return doc_start + 1, "\n".join(lines[doc_start:end + 1])
    return None


def cmd_method(args: list[str]) -> None:
    import argparse
    p = argparse.ArgumentParser("mc-src.py method")
    p.add_argument("class_name", help="Fully qualified class name")
    p.add_argument("method_name", help="Method name to extract")
    p.add_argument("--lines", "-l", help="Line range within the method like 10-20")
    opts = p.parse_args(args)

    jar = SourceJar.discover()
    result = jar.method_source_ts(opts.class_name, opts.method_name)
    if result is None:
        entry = jar.path_for_class(opts.class_name)
        content = jar.read_entry(entry)
        if content is None:
            print(f"Method '{opts.method_name}' not found in {opts.class_name}")
            sys.exit(1)
        result = _method_source_regex(content.splitlines(), opts.method_name)

    if result is None:
        print(f"Method '{opts.method_name}' not found in {opts.class_name}")
        sys.exit(1)

    line_offset, source = result
    if opts.lines:
        parts = opts.lines.split("-")
        start = int(parts[0]) - 1
        end = int(parts[1]) if parts[1] else None
        body = source.splitlines()
        chunk = body[start:end]
        for i, line in enumerate(chunk, start=line_offset + start):
            print(f"{i:5d}:{line}")
    else:
        for i, line in enumerate(source.splitlines(), start=line_offset):
            print(f"{i:5d}:{line}")


# =====================================================================
#  Glob helpers (tree-sitter)
# =====================================================================

_VIS_KEYWORDS = frozenset({"public", "private", "protected"})
_MOD_KEYWORDS = frozenset({
    "static", "abstract", "final", "synchronized", "native",
    "transient", "volatile", "strictfp", "default",
})


def _node_text(node, content_bytes: bytes) -> str:
    return content_bytes[node.start_byte:node.end_byte].decode()


def _modifiers_from_node(node, content_bytes: bytes) -> tuple[Optional[str], list[str]]:
    """Extract (visibility, [other_mods]) from a declaration node.
    Modifiers are type-based children (not named fields) in tree-sitter-java."""
    mod_node = None
    for child in node.children:
        if child.type == "modifiers":
            mod_node = child
            break
        if child.type in ("marker_annotation", "annotation"):
            continue
    if mod_node is None:
        return "(package)", []
    vis = None
    mods = []
    for mchild in mod_node.children:
        text = _node_text(mchild, content_bytes)
        if text in _VIS_KEYWORDS:
            vis = text
        elif text in _MOD_KEYWORDS:
            mods.append(text)
    return vis or "(package)", mods


def _param_text(node, content_bytes: bytes) -> str:
    """Format a formal_parameter as 'Type name'."""
    type_node = node.child_by_field_name("type")
    name_node = node.child_by_field_name("name")
    t = _node_text(type_node, content_bytes) if type_node else "?"
    n = _node_text(name_node, content_bytes) if name_node else "?"
    return f"{t} {n}"


def _byte_to_line_range(content_str: str, start_byte: int, end_byte: int) -> tuple[int, int]:
    before = content_str[:start_byte]
    between = content_str[start_byte:end_byte]
    start_line = before.count("\n") + 1
    end_line = start_line + between.count("\n")
    return start_line, end_line


def _class_chain(fqn: str) -> tuple[str, list[str]]:
    """Split FQN into (outer_fqn, [class_chain]).
    'net.minecraft.Foo.Bar' -> ('net.minecraft.Foo', ['Foo', 'Bar'])
    'net.minecraft.Foo' -> ('net.minecraft.Foo', ['Foo'])
    """
    parts = fqn.split(".")
    for i, p in enumerate(parts):
        if p and p[0].isupper():
            return (".".join(parts[:i + 1]), parts[i:])
    return (fqn, [parts[-1]])


def _find_nested_type(root_node, class_chain: list[str], content_bytes: bytes):
    """Walk the tree to find a nested type by name chain.
    root_node: program or class_body node.
    class_chain: ['Outer', 'Inner'] or just ['Outer'].
    Returns the AST node of the innermost type, or None.
    """
    def find_type_in_children(children, name):
        for child in children:
            if child.type in ("class_declaration", "interface_declaration",
                              "enum_declaration", "annotation_type_declaration", "record_declaration"):
                name_node = child.child_by_field_name("name")
                if name_node:
                    cname = content_bytes[name_node.start_byte:name_node.end_byte].decode()
                    if cname == name:
                        return child
        return None

    # Search root children for first name in chain
    node = find_type_in_children(root_node.children, class_chain[0])
    if node is None:
        return None

    # Walk down remaining chain
    for name in class_chain[1:]:
        body = node.child_by_field_name("body")
        if body is None:
            return None
        node = find_type_in_children(body.children, name)
        if node is None:
            return None

    return node

def _glob_treesitter(content_str: str, opts) -> Optional[list[dict]]:
    """Tree-sitter-based member extraction. Returns rows or None on failure."""
    if not _TS_AVAILABLE:
        return None

    content_bytes = content_str.encode("utf-8")

    try:
        tree = _TS_PARSER.parse(content_bytes)
    except Exception:
        return None

    root = tree.root_node
    # Find target type declaration (handles inner class chains like Outer.Inner)
    _, chain = _class_chain(opts.class_name)
    target_class = _find_nested_type(root, chain, content_bytes)
    if target_class is None:
        return None

    body = target_class.child_by_field_name("body")
    if body is None:
        return None

    # Visibility filter
    has_vis_flag = opts.public or opts.private or opts.protected or opts.package
    ok_vis = {"public", "protected"} if not has_vis_flag else set()
    if not has_vis_flag:
        ok_vis = {"public", "protected"}
    else:
        if opts.public: ok_vis.add("public")
        if opts.private: ok_vis.add("private")
        if opts.protected: ok_vis.add("protected")
        if opts.package: ok_vis.add("(package)")

    show_methods = not opts.fields and not opts.ctors and not opts.inner_types or opts.methods
    show_fields = not opts.methods and not opts.ctors and not opts.inner_types or opts.fields
    show_ctors = not opts.methods and not opts.fields and not opts.inner_types or opts.ctors
    show_inner = not opts.methods and not opts.fields and not opts.ctors or opts.inner_types

    def matches_static(mods):
        is_s = "static" in mods
        if opts.static and opts.no_static:
            return True
        if opts.static:
            return is_s
        if opts.no_static:
            return not is_s
        return True

    def matches_name(name):
        if opts.filter:
            return opts.filter.lower() in name.lower()
        return True

    rows = []

    for child in body.children:
        ct = child.type

        if ct == "{":
            continue
        if ct == "}":
            continue
        if ct in ("block_comment", "line_comment"):
            continue

        if ct not in ("field_declaration", "method_declaration",
                      "constructor_declaration", "class_declaration",
                      "interface_declaration", "enum_declaration",
                      "annotation_type_declaration", "record_declaration"):
            continue

        # Extract modifiers (type-based, not named field)
        vis, mods = _modifiers_from_node(child, content_bytes)
        if vis not in ok_vis:
            continue
        if not matches_static(mods):
            continue

        # Field declarations store names inside variable_declarator, not directly
        if ct == "field_declaration":
            # Collect ALL variable names; skip only if NONE match the filter
            var_names = []
            for gc in child.children:
                if gc.type == "variable_declarator":
                    vname_node = gc.child_by_field_name("name")
                    if vname_node:
                        var_names.append(_node_text(vname_node, content_bytes))
            if not var_names:
                continue
            if opts.filter and not any(matches_name(n) for n in var_names):
                continue
            name = var_names[0]  # for ordering, not display
        else:
            name_node = child.child_by_field_name("name")
            if name_node is None:
                continue
            name = _node_text(name_node, content_bytes)
            if not matches_name(name):
                continue

        start_line, end_line = _byte_to_line_range(content_str, child.start_byte, child.end_byte)

        if ct == "field_declaration":
            if not show_fields:
                continue
            type_node = child.child_by_field_name("type")
            t = _node_text(type_node, content_bytes) if type_node else "?"
            # Find all variable_declarators
            for gc in child.children:
                if gc.type == "variable_declarator":
                    vname_node = gc.child_by_field_name("name")
                    if vname_node:
                        vname = _node_text(vname_node, content_bytes)
                        has_init = gc.child_by_field_name("value") is not None
                        suffix = " = ..." if has_init else ""
                        rows.append({
                            "line": start_line,
                            "range": (start_line, end_line),
                            "text": f"{vis} {' '.join(mods)} {t} {vname}{suffix}",
                        })

        elif ct == "method_declaration":
            if not show_methods:
                continue
            type_node = child.child_by_field_name("type")
            t = _node_text(type_node, content_bytes) if type_node else "void"
            params_node = child.child_by_field_name("parameters")
            params = ""
            if params_node:
                param_list = [
                    _param_text(p, content_bytes)
                    for p in params_node.children
                    if p.type == "formal_parameter"
                ]
                params = ", ".join(param_list)
            rows.append({
                "line": start_line,
                "range": (start_line, end_line),
                "text": f"{vis} {' '.join(mods)} {t} {name}({params})",
            })

        elif ct == "constructor_declaration":
            if not show_ctors:
                continue
            params_node = child.child_by_field_name("parameters")
            params = ""
            if params_node:
                param_list = [
                    _param_text(p, content_bytes)
                    for p in params_node.children
                    if p.type == "formal_parameter"
                ]
                params = ", ".join(param_list)
            rows.append({
                "line": start_line,
                "range": (start_line, end_line),
                "text": f"{vis} {' '.join(mods)} {name}({params})",
            })

        elif ct in ("class_declaration", "interface_declaration",
                    "enum_declaration", "annotation_type_declaration", "record_declaration"):
            if not show_inner:
                continue
            kind_map = {
                "class_declaration": "class",
                "interface_declaration": "interface",
                "enum_declaration": "enum",
                "annotation_type_declaration": "@interface",
                "record_declaration": "record",
            }
            rows.append({
                "line": start_line,
                "range": (start_line, end_line),
                "text": f"{vis} {' '.join(mods)} {kind_map[ct]} {name}",
            })

    return rows


# =====================================================================
#  Glob helpers (regex fallback)
# =====================================================================

def _member_range(lines: list[str], start_0: int) -> tuple[int, int]:
    brace_depth = 0
    end = start_0
    for j in range(start_0, len(lines)):
        end = j
        brace_depth += lines[j].count("{") - lines[j].count("}")
        if brace_depth == 0 and j > start_0:
            break
    return (start_0, end)


def _glob_regex(lines: list[str], simple_name: str, opts) -> list[dict]:
    rows = []

    has_vis_flag = opts.public or opts.private or opts.protected or opts.package
    ok_vis = {"public", "protected"} if not has_vis_flag else set()
    if not has_vis_flag:
        ok_vis = {"public", "protected"}
    else:
        if opts.public: ok_vis.add("public")
        if opts.private: ok_vis.add("private")
        if opts.protected: ok_vis.add("protected")
        if opts.package: ok_vis.add("(package)")

    show_methods = not opts.fields and not opts.ctors and not opts.inner_types or opts.methods
    show_fields = not opts.methods and not opts.ctors and not opts.inner_types or opts.fields
    show_ctors = not opts.methods and not opts.fields and not opts.inner_types or opts.ctors
    show_inner = not opts.methods and not opts.fields and not opts.ctors or opts.inner_types

    def matches_static_mods(modlist):
        is_s = "static" in modlist
        if opts.static and opts.no_static:
            return True
        if opts.static:
            return is_s
        if opts.no_static:
            return not is_s
        return True

    def matches_name(name):
        if opts.filter:
            return opts.filter.lower() in name.lower()
        return True

    def is_annotation(line):
        return bool(re.match(r"^\s*@", line))

    cls_line = None
    for i, ln in enumerate(lines):
        if any(kw in stripped for kw in ("class ", "interface ", "enum ", "record ")):
            if simple_name in stripped and "{" in stripped:
                cls_line = i
                break
    if cls_line is None:
        return rows

    brace_depth = 1
    i = cls_line
    while i < len(lines):
        brace_depth += lines[i].count("{") - lines[i].count("}")
        if brace_depth <= 0:
            break
        i += 1
        ln = lines[i] if i < len(lines) else ""
        stripped = ln.strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("*") or is_annotation(stripped):
            continue
        if stripped.startswith("/**"):
            continue

        vis = None
        mods = []
        tokens = stripped.split()
        rest_start = 0
        for ti, tok in enumerate(tokens):
            if tok in _VIS_KEYWORDS:
                vis = tok
            elif tok in _MOD_KEYWORDS:
                mods.append(tok)
            else:
                rest_start = ti
                break
        else:
            continue
        if vis is None or vis not in ok_vis:
            continue
        if not matches_static_mods(mods):
            continue
        rest = tokens[rest_start:]
        if not rest:
            continue

        # Inner types
        if show_inner and rest[0] in ("class", "interface", "enum", "@interface"):
            kind = rest[0]
            if len(rest) > 1 and not rest[1].startswith("("):
                name = rest[1]
                if matches_name(name):
                    s0 = i
                    e0 = _member_range(lines, s0)[1]
                    rows.append({
                        "line": s0 + 1,
                        "range": (s0 + 1, e0 + 1),
                        "text": f"{vis} {' '.join(mods)} {kind} {name}",
                    })
            continue

        # Constructors
        first_word = rest[0].split("(")[0]
        if show_ctors and first_word == simple_name and "(" in stripped:
            if matches_name(simple_name):
                s0 = i
                e0 = _member_range(lines, s0)[1]
                param_count = sum(1 for p in tokens if "," in p)
                rows.append({
                    "line": s0 + 1,
                    "range": (s0 + 1, e0 + 1),
                    "text": f"{vis} {' '.join(mods)} {simple_name}({param_count} params)",
                })
            continue

        # Methods
        if "(" in stripped and stripped.strip().endswith("{") and rest[0].split("(")[0] != simple_name:
            if not show_methods:
                continue
            has_paren = stripped.index("(")
            before_paren = stripped[:has_paren].strip()
            bp_clean = [t for t in before_paren.split() if t not in _VIS_KEYWORDS and t not in _MOD_KEYWORDS]
            if len(bp_clean) >= 1:
                method_name = bp_clean[-1]
                ret_type = " ".join(bp_clean[:-1])
                if matches_name(method_name):
                    s0 = i
                    e0 = _member_range(lines, s0)[1]
                    rows.append({
                        "line": s0 + 1,
                        "range": (s0 + 1, e0 + 1),
                        "text": f"{vis} {' '.join(mods)} {ret_type} {method_name}(...)",
                    })
            continue

        # Fields
        if show_fields and ("=" in stripped or stripped.endswith(";") or stripped.endswith(",")):
            end_marker = ";" if ";" in stripped else ("=" if "=" in stripped else ",")
            idx = stripped.index(end_marker) if end_marker in stripped else len(stripped)
            field_part = stripped[:idx].strip()
            ft = field_part.split()
            content_tokens = [t for t in ft if t not in _VIS_KEYWORDS and t not in _MOD_KEYWORDS]
            if len(content_tokens) >= 2:
                field_name = content_tokens[-1]
                field_type = " ".join(content_tokens[:-1])
                if matches_name(field_name):
                    rows.append({
                        "line": i + 1,
                        "range": (i + 1, i + 1),
                        "text": f"{vis} {' '.join(mods)} {field_type} {field_name}",
                    })

    return rows


def cmd_glob(args: list[str]) -> None:
    import argparse
    p = argparse.ArgumentParser("mc-src-ts.py glob")
    p.add_argument("class_name", help="Fully qualified or short class name")
    p.add_argument("--methods", action="store_true")
    p.add_argument("--fields", action="store_true")
    p.add_argument("--ctors", "--constructors", action="store_true")
    p.add_argument("--inner-types", action="store_true")
    p.add_argument("--public", action="store_true")
    p.add_argument("--private", action="store_true")
    p.add_argument("--protected", action="store_true")
    p.add_argument("--package", action="store_true")
    p.add_argument("--static", action="store_true")
    p.add_argument("--no-static", action="store_true")
    p.add_argument("--filter", "-f", type=str, default=None, help="Substring filter on member name")
    opts = p.parse_args(args)

    if not _TS_AVAILABLE:
        print("Warning: tree-sitter not installed. Install with: pip install tree-sitter tree-sitter-java",
              file=sys.stderr)
        print("Falling back to regex-based extraction.", file=sys.stderr)

    jar = SourceJar.discover()
    class_name = jar.resolve_name(opts.class_name)
    opts.class_name = class_name  # downstream uses this for display and target lookup
    entry = jar.path_for_class(class_name)
    content = jar.read_entry(entry)
    if content is None:
        print(f"Class '{class_name}' not found in sources jar.", file=sys.stderr)
        sys.exit(1)

    lines = content.splitlines()
    rows = _glob_treesitter(content, opts)
    use_regex = rows is None
    if rows is None:
        rows = _glob_regex(lines, class_name.split(".")[-1], opts)

    def safe_text(s: str) -> str:
        return s.encode("utf-8", errors="replace").decode("utf-8")

    rows.sort(key=lambda r: (r["line"], r["text"]))

    print(f"[{class_name}]" + ("  (regex fallback)" if use_regex else ""))
    count = 0
    for r in rows:
        text = safe_text(r["text"])
        range_str = f"L{r['range'][0]}-{r['range'][1]}" if r["range"][0] != r["range"][1] else f"L{r['range'][0]}"
        print(f"  {text:<58s} {range_str}")
        count += 1
    print(f"\n{count} member{'' if count == 1 else 's'}")


# =====================================================================
#  Main
# =====================================================================

def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__.strip())
        sys.exit(1)
    command = sys.argv[1]
    rest = sys.argv[2:]
    match command:
        case "read":
            cmd_read(rest)
        case "grep":
            cmd_grep(rest)
        case "list":
            cmd_list(rest)
        case "find":
            cmd_find(rest)
        case "method":
            cmd_method(rest)
        case "glob":
            cmd_glob(rest)
        case _:
            print(f"Unknown command: {command}")
            print(__doc__.strip())
            sys.exit(1)


if __name__ == "__main__":
    main()
