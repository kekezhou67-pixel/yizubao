# -*- coding: utf-8 -*-
"""文档解析（讲义04 §3：PDF/Word/Excel/纯文本）

Excel 转 markdown 表格 —— 讲义04 强调：表格的价值在行列关系，
转成 markdown 后 LLM 和分片器都能理解结构。
所有文件操作显式 encoding="utf-8"（讲义04 的坑）。
"""
import io
from pathlib import Path


def parse_pdf(file_bytes: bytes) -> str:
    """PDF → 文本（pypdf）"""
    from pypdf import PdfReader
    reader = PdfReader(io.BytesIO(file_bytes))
    pages = []
    for page in reader.pages:
        text = page.extract_text() or ""
        pages.append(text)
    return "\n\n".join(pages)


def parse_docx(file_bytes: bytes) -> str:
    """Word → 文本（python-docx，含表格）"""
    import docx
    document = docx.Document(io.BytesIO(file_bytes))
    parts = [p.text for p in document.paragraphs if p.text.strip()]
    # 表格转 markdown
    for table in document.tables:
        rows = []
        for row in table.rows:
            cells = [c.text.strip().replace("\n", " ") for c in row.cells]
            rows.append("| " + " | ".join(cells) + " |")
        if rows:
            parts.append("\n".join(rows))
    return "\n\n".join(parts)


def parse_excel(file_bytes: bytes) -> str:
    """Excel → markdown 表格（pandas，每 sheet 一段）"""
    import pandas as pd
    sheets = pd.read_excel(io.BytesIO(file_bytes), sheet_name=None)
    parts = []
    for name, df in sheets.items():
        parts.append(f"## 工作表：{name}\n\n" + df.to_markdown(index=False))
    return "\n\n".join(parts)


def parse_txt(file_bytes: bytes) -> str:
    """纯文本/markdown"""
    return file_bytes.decode("utf-8", errors="replace")


def parse_file(filename: str, file_bytes: bytes) -> dict:
    """按扩展名分发解析。返回 {text, meta}"""
    suffix = Path(filename).suffix.lower()
    if suffix == ".pdf":
        text, kind = parse_pdf(file_bytes), "pdf"
    elif suffix in (".docx", ".doc"):
        text, kind = parse_docx(file_bytes), "docx"
    elif suffix in (".xlsx", ".xls"):
        text, kind = parse_excel(file_bytes), "excel"
    elif suffix in (".txt", ".md"):
        text, kind = parse_txt(file_bytes), "text"
    else:
        raise ValueError(f"不支持的文件类型: {suffix}（支持 pdf/docx/xlsx/txt/md）")

    return {
        "text": text,
        "meta": {
            "filename": filename,
            "type": kind,
            "char_count": len(text),
        },
    }
