# -*- coding: utf-8 -*-
"""分片/向量化/入库/检索/精排接口

字段命名统一下划线（Java PythonAiClient 按此约定调用 —— 工程方案 §4.5）
"""
from typing import List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app import config
from app.services import chunker, embedder, reranker
from app.services.vector_store import get_text_store

router = APIRouter(tags=["rag"])


# ==================== 请求/响应模型 ====================

class SplitReq(BaseModel):
    text: str
    parent_size: Optional[int] = None
    child_size: Optional[int] = None


class EmbedReq(BaseModel):
    texts: List[str] = Field(..., min_length=1)


class IndexChunk(BaseModel):
    vector_id: str            # Java 生成的全局唯一 id（存 kb_chunk.vector_id）
    child_text: str           # 用于向量化的子块
    doc_id: Optional[int] = None
    chunk_index: Optional[int] = None


class IndexReq(BaseModel):
    chunks: List[IndexChunk] = Field(..., min_length=1)


class SearchReq(BaseModel):
    query: str
    top_k: int = 20


class RerankCandidate(BaseModel):
    vector_id: str
    text: str


class RerankReq(BaseModel):
    query: str
    candidates: List[RerankCandidate]
    top_k: int = 5


class DeleteDocReq(BaseModel):
    doc_id: int


# ==================== 接口 ====================

@router.post("/split")
def split_text(req: SplitReq):
    """父子分片（不向量化，Java 拿分片结果存 kb_chunk）"""
    if not req.text.strip():
        raise HTTPException(status_code=400, detail="text 为空")
    chunks = chunker.build_chunks(
        req.text,
        parent_size=req.parent_size,
        child_size=req.child_size,
    )
    return {"chunk_count": len(chunks), "chunks": chunks}


@router.post("/embed")
def embed(req: EmbedReq):
    """向量化（Java 的 query 向量也从这里走 —— 保证 query 与文档同模型）"""
    vectors = embedder.embed_texts(req.texts)
    return {"count": len(vectors), "dim": config.VECTOR_DIM, "vectors": vectors}


@router.post("/index")
def index_chunks(req: IndexReq):
    """子块向量化 + 写入 FAISS 文本库"""
    try:
        vectors = embedder.embed_texts([c.child_text for c in req.chunks])
        metas = [c.model_dump(exclude={"child_text"}) for c in req.chunks]
        added = get_text_store().add(vectors, metas)
        return {"indexed": added, "total": get_text_store().count()}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/search")
def search(req: SearchReq):
    """FAISS 向量检索（返回 vector_id + score，Java 拿去和 ES 结果做 RRF）"""
    qvec = embedder.embed_query(req.query)
    results = get_text_store().search(qvec, top_k=req.top_k)
    return {"count": len(results), "results": results}


@router.post("/rerank")
def rerank(req: RerankReq):
    """CrossEncoder 精排（RRF 融合后的 top-10 → 精排 top-5）"""
    results = reranker.rerank(
        req.query,
        [c.model_dump() for c in req.candidates],
        top_k=req.top_k,
    )
    return {"count": len(results), "results": results}


@router.post("/index/delete-doc")
def delete_doc(req: DeleteDocReq):
    """按 doc_id 删除向量（知识库文档删除时级联清理）"""
    removed = get_text_store().delete_by_doc(req.doc_id)
    return {"removed": removed, "total": get_text_store().count()}
