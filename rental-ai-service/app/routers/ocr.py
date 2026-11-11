# -*- coding: utf-8 -*-
"""OCR 接口（阶段7A）：POST /ocr（multipart 图片上传）"""
from fastapi import APIRouter, File, HTTPException, UploadFile

from app.services import ocr_service

router = APIRouter(tags=["ocr"])

ALLOWED_EXT = (".jpg", ".jpeg", ".png", ".bmp", ".webp")


@router.post("/ocr")
async def ocr(file: UploadFile = File(...)):
    """图片文字识别：合同照片/扫描件 → 纯文本（供 Java 串联 RAG 审查）"""
    filename = (file.filename or "").lower()
    if not filename.endswith(ALLOWED_EXT):
        raise HTTPException(status_code=400,
                            detail=f"仅支持图片格式: {ALLOWED_EXT}")
    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="文件为空")
    try:
        return ocr_service.ocr_image_bytes(file_bytes)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"OCR 识别失败: {e}")
