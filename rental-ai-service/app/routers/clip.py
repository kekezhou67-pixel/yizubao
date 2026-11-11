# -*- coding: utf-8 -*-
"""CLIP 图文检索接口（阶段7B）

- POST /clip/index          图片入库（multipart: file + image_url + caption）
- POST /clip/search-image   图搜图（multipart: file）
- POST /clip/search-text    文搜图（JSON: {text, top_k}）

★ img 索引用独立的 FaissStore（img.index），与文本库（text.index）分开 ——
  BGE 与 CLIP 语义空间不同，混存则相似度无意义（讲义05）。
"""
import logging
from typing import Optional

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from pydantic import BaseModel

from app.services import clip_service
from app.services.vector_store import get_img_store

log = logging.getLogger(__name__)
router = APIRouter(prefix="/clip", tags=["clip"])

_seq_lock = __import__("threading").Lock()
_seq = [0]


def _next_vector_id() -> str:
    with _seq_lock:
        _seq[0] += 1
        return f"clip-{_seq[0]}"


class TextSearchReq(BaseModel):
    text: str
    top_k: int = 20


@router.post("/index")
async def index_image(file: UploadFile = File(...),
                      image_url: str = Form(...),
                      caption: str = Form("")):
    """房源图片入库：CLIP 向量化 → 写 img 索引（caption 一并存入，文搜图靠它）"""
    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="文件为空")
    try:
        vec = clip_service.encode_image(file_bytes)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"图像编码失败: {e}")

    store = get_img_store()
    vector_id = _next_vector_id()
    store.add([vec], [{
        "vector_id": vector_id,
        "image_url": image_url,
        "caption": caption,
    }])
    return {"vector_id": vector_id, "total": store.count()}


@router.post("/search-image")
async def search_by_image(file: UploadFile = File(...), top_k: int = Form(20)):
    """图搜图：上传图片 → 相似房源图"""
    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="文件为空")
    try:
        vec = clip_service.encode_image(file_bytes)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"图像编码失败: {e}")
    results = get_img_store().search(vec, top_k=top_k)
    return {"count": len(results), "results": results}


@router.post("/search-text")
def search_by_text(req: TextSearchReq):
    """文搜图：文字描述 → 匹配 caption/图像 的房源图（跨模态）"""
    if not req.text.strip():
        raise HTTPException(status_code=400, detail="text 为空")
    vec = clip_service.encode_text(req.text)
    results = get_img_store().search(vec, top_k=req.top_k)
    return {"count": len(results), "results": results}
