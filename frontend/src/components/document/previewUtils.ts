const IMAGE_EXTS = ["png", "jpg", "jpeg", "svg", "gif", "webp", "bmp"];

export const isSpreadsheetType = (ext?: string | null) => {
  const e = (ext || "").toLowerCase();
  return e === "xlsx" || e === "xls";
};

export const isImageType = (ext?: string | null) => IMAGE_EXTS.includes((ext || "").toLowerCase());

export const isDocxType = (ext?: string | null) => (ext || "").toLowerCase() === "docx";

export const isPreviewableType = (ext?: string | null) => {
  const type = (ext || "").toLowerCase();
  return ["pdf", "csv", "markdown", "txt"].includes(type)
    || isDocxType(type)
    || isSpreadsheetType(type)
    || isImageType(type);
};

// 剥离 markdown 头部的 front-matter，单独展示
export const parseFrontMatter = (content: string): { head: string | null; body: string } => {
  if (content.startsWith("---\n")) {
    const end = content.indexOf("\n---\n", 4);
    if (end > 0) {
      return { head: content.substring(4, end), body: content.substring(end + 5) };
    }
  }
  return { head: null, body: content };
};
