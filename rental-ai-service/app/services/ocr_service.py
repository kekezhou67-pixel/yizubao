# -*- coding: utf-8 -*-
"""PaddleOCR 服务（阶段7A，讲义05 §1.2.1）

选型：PaddleOCR —— 百度开源、中文友好、免费、可离线，
自带【检测 + 识别 + 方向矫正】三阶段流水线：
  检测：找出每行文字的位置框（哪里有字）
  识别：把框内图像转成文字（框里是什么）
  方向矫正：拍歪/倒置的图片先转正再识别

坑（讲义05）：paddleocr 与 paddlepaddle 是两个独立包，都锁 2.x。
首次调用会联网下载 检测/识别/方向分类 3 个模型，之后走本地缓存。
"""
import io
import logging
import threading

log = logging.getLogger(__name__)

_ocr = None
_lock = threading.Lock()


def get_ocr():
    """懒加载单例（模型加载慢，且首次要下载）"""
    global _ocr
    if _ocr is None:
        with _lock:
            if _ocr is None:
                from paddleocr import PaddleOCR
                log.info("加载 PaddleOCR（首次会下载3个模型）...")
                _ocr = PaddleOCR(
                    use_angle_cls=True,   # 方向分类：拍歪/倒置自动矫正
                    lang="ch",            # 中文模型
                    show_log=False,       # 关刷屏日志
                )
                log.info("PaddleOCR 加载完成")
    return _ocr


def ocr_image_bytes(file_bytes: bytes) -> dict:
    """图片字节 → OCR 文本
    结果结构: [[页][行][坐标, (文字, 置信度)]]，取文字拼接
    """
    import numpy as np
    from PIL import Image

    img = Image.open(io.BytesIO(file_bytes)).convert("RGB")
    result = get_ocr().ocr(np.array(img), cls=True)

    lines = []
    for block in result:
        if not block:
            continue
        for line in block:
            try:
                text = line[1][0]        # (文字, 置信度) 取文字
                conf = float(line[1][1])
                if text.strip():
                    lines.append({"text": text.strip(), "conf": round(conf, 3)})
            except (IndexError, TypeError, ValueError):
                continue

    return {
        "text": "\n".join(l["text"] for l in lines),
        "lineCount": len(lines),
        "lines": lines,
    }


def warmup():
    """可选预热：服务启动不阻塞（模型大，放首次请求时加载也行）"""
    get_ocr()
