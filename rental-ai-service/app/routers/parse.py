# -*- coding: utf-8 -*-
"""文档解析接口：POST /parse（multipart 上传）"""
from fastapi import APIRouter, File, HTTPException, UploadFile

from app.services import parser

router = APIRouter(tags=["parse"])


@router.post("/parse")
async def parse_document(file: UploadFile = File(...)):
    """PDF/Word/Excel/TXT/MD → 纯文本"""
    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="文件为空")
    try:
        result = parser.parse_file(file.filename or "unknown.txt", file_bytes)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"解析失败: {e}")
    return result
