"""Custom MCP server built with FastMCP.

Exposes the contents of ``lorem-ipsum.md`` as both a Resource (a URI Claude can
read from) and a Tool (an action Claude can call). Both return exactly the first
``word_count`` words of the file (default: 30).
"""

from pathlib import Path

from fastmcp import FastMCP

mcp = FastMCP("lorem-ipsum")

LOREM_FILE = Path(__file__).parent / "lorem-ipsum.md"
DEFAULT_WORD_COUNT = 30


def _first_words(word_count: int) -> str:
    """Return the first ``word_count`` whitespace-separated words of the file."""
    words = LOREM_FILE.read_text(encoding="utf-8").split()
    count = max(0, word_count)
    return " ".join(words[:count])


@mcp.resource("lorem://words")
def lorem_default() -> str:
    """Resource: the first 30 words of lorem-ipsum.md."""
    return _first_words(DEFAULT_WORD_COUNT)


@mcp.resource("lorem://words/{word_count}")
def lorem_words(word_count: int) -> str:
    """Resource template: the first ``word_count`` words of lorem-ipsum.md."""
    return _first_words(word_count)


@mcp.tool
def read(word_count: int = DEFAULT_WORD_COUNT) -> str:
    """Read the first ``word_count`` words (default 30) from lorem-ipsum.md."""
    return _first_words(word_count)


if __name__ == "__main__":
    mcp.run()
