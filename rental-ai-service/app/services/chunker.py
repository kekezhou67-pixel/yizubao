# -*- coding: utf-8 -*-
"""父子分片（讲义04 §3 重点）

- 父块 2000字/重叠200：喂给 LLM，保上下文完整
- 子块 500字/重叠50：  做向量检索，保命中精度
- 命中 child → 返回所属 parent 给 LLM

★ 工程方案 §4.3 的坑：不要用 text.find(child) 定位父块（重复文本会错），
  切分时直接记录 parent_index 关联。
"""
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app import config

# 中文分隔符优先级：段落 > 换行 > 句号 > 逗号（RecursiveCharacterTextSplitter 按序尝试）
_SEPARATORS = ["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""]


def _splitter(chunk_size: int, overlap: int) -> RecursiveCharacterTextSplitter:
    return RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=overlap,
        separators=_SEPARATORS,
        length_function=len,
    )


def build_chunks(text: str,
                 parent_size: int = None,
                 parent_overlap: int = None,
                 child_size: int = None,
                 child_overlap: int = None) -> list:
    """两级切分，返回 [{parent_index, parent_text, child_index, child_text}]

    child_index 是全局序号（入库后对应 kb_chunk 的行），
    parent_index 标记所属父块（检索命中后据此取父块喂 LLM）。
    """
    parent_size = parent_size or config.PARENT_CHUNK_SIZE
    parent_overlap = parent_overlap or config.PARENT_CHUNK_OVERLAP
    child_size = child_size or config.CHILD_CHUNK_SIZE
    child_overlap = child_overlap or config.CHILD_CHUNK_OVERLAP

    parent_splitter = _splitter(parent_size, parent_overlap)
    child_splitter = _splitter(child_size, child_overlap)

    parents = parent_splitter.split_text(text)
    chunks = []
    child_no = 0
    for p_idx, p_text in enumerate(parents):
        for c_text in child_splitter.split_text(p_text):
            if not c_text.strip():
                continue
            chunks.append({
                "parent_index": p_idx,
                "parent_text": p_text,
                "child_index": child_no,
                "child_text": c_text,
            })
            child_no += 1
    return chunks
