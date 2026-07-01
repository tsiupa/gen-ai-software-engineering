# HOWTORUN — Custom Lorem Ipsum MCP Server

A minimal [FastMCP](https://gofastmcp.com) server that exposes the contents of
`lorem-ipsum.md` both as a **Resource** (a URI Claude can read from) and as a
**Tool** named `read` (an action Claude can call). Both return exactly the first
`word_count` words of the file (default: `30`).

## Concepts

- **Resource** — a URI Claude can *read from* (files, APIs, dynamic content).
  Here: `lorem://words` (first 30 words) and the template
  `lorem://words/{word_count}` (first N words).
- **Tool** — an action Claude can *call* to perform an operation (read a file,
  run a command). Here: `read(word_count=30)`.

## 1. Prerequisites

- Python **3.10+** (FastMCP requires it).
- [`uv`](https://docs.astral.sh/uv/) — recommended. Install with:
  ```bash
  brew install uv        # macOS
  # or: curl -LsSf https://astral.sh/uv/install.sh | sh
  ```

## 2. Install dependencies

`fastmcp` is declared in both `pyproject.toml` and `requirements.txt`.

**With uv (recommended)** — no manual install needed; `uv run` provisions an
isolated environment (and even downloads Python 3.12 if missing) from
`pyproject.toml` on first run.

**With pip (alternative):**
```bash
cd custom-mcp-server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## 3. Run the server

```bash
# From the custom-mcp-server/ folder:
uv run python server.py
# or with pip venv active:
python server.py
```

The server starts on the default **stdio** transport and prints a
`Starting MCP server 'lorem-ipsum' with transport 'stdio'` banner. This is the
same command your MCP client launches automatically — you normally do not run it
by hand.

## 4. Connect the MCP configuration

The server is registered in the repo's `../.mcp.json` under `custom-lorem`:

```json
"custom-lorem": {
  "command": "uv",
  "args": [
    "run",
    "--directory",
    "/Users/oleksandr.tsiupa/Projects/STUDY/gen-ai-software-engineering/homework-5/custom-mcp-server",
    "python",
    "server.py"
  ]
}
```

> Update the absolute path in `--directory` if you clone the repo elsewhere.

Claude Code auto-discovers `.mcp.json` at the project root. Verify it is loaded:

```bash
claude mcp list          # should show custom-lorem as connected
```

## 5. Use / test the `read` tool

**In Claude Code**, just ask:

> Use the custom-lorem `read` tool to return 10 words.

**From the CLI / a smoke test** (uses FastMCP's in-memory client):

```bash
uv run python - <<'PY'
import asyncio
from fastmcp import Client
import server

async def main():
    async with Client(server.mcp) as c:
        print("tools:", [t.name for t in await c.list_tools()])
        r = await c.call_tool("read", {"word_count": 10})
        print("read(10):", r.data)
        res = await c.read_resource("lorem://words/5")
        print("resource lorem://words/5:", res[0].text)

asyncio.run(main())
PY
```

Expected: the tool returns exactly 10 words and the resource returns exactly 5.
