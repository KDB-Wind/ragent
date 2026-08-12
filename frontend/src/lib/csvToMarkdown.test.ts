import { describe, expect, it } from "vitest";

import { csvToMarkdown } from "./csvToMarkdown";

describe("csvToMarkdown", () => {
  it("escapes an existing backslash before escaping a table delimiter", () => {
    expect(csvToMarkdown("header\nleft\\|right")).toBe(
      "| header |\n| --- |\n| left\\\\\\|right |"
    );
  });

  it("keeps embedded CSV newlines inside one markdown cell", () => {
    expect(csvToMarkdown('header\n"line 1\nline 2"')).toBe(
      "| header |\n| --- |\n| line 1<br>line 2 |"
    );
  });
});
