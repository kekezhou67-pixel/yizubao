# -*- coding: utf-8 -*-
"""中文 CLIP 图文跨模态服务（阶段7B，讲义05 §1.2.2）

CLIP（Contrastive Language-Image Pre-Training）：
把图像和文本映射到同一个 512 维向量空间 → 四种检索方向：
  文→文（BGE）｜文→图、图→文、图→图（CLIP）

★ 模型加载方式（实测坑）：
  OFA-Sys/chinese-clip 是 transformers 格式模型，
  sentence-transformers 3.2 加载会报 'ChineseCLIPConfig' has no attribute 'hidden_size'，
  必须用 transformers 原生 ChineseCLIPModel + ChineseCLIPProcessor。

★ 核心原则（讲义05 面试题）：
1. 文本向量（BGE）和图像向量（CLIP）是两个语义空间，
   必须分库存放、分别检索（本项目：text.index / img.index 两套 FAISS）
2. 图片本身没有文字，靠 caption 才能被【文字查询】命中
3. 换模型必须重建索引（维度/空间都变了）
"""
import io
import logging
import threading

import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image

log = logging.getLogger(__name__)

_model = None
_processor = None
_lock = threading.Lock()


def get_model():
    """懒加载单例：ChineseCLIPModel（首次从 hf-mirror 缓存加载，约10秒）"""
    global _model, _processor
    if _model is None:
        with _lock:
            if _model is None:
                from transformers import ChineseCLIPModel, ChineseCLIPProcessor
                from app import config
                log.info("加载中文 CLIP: %s ...", config.CLIP_MODEL)
                _model = ChineseCLIPModel.from_pretrained(config.CLIP_MODEL)
                _processor = ChineseCLIPProcessor.from_pretrained(config.CLIP_MODEL)
                _model.eval()
                log.info("中文 CLIP 加载完成")
    return _model, _processor


def encode_image(file_bytes: bytes) -> list:
    """图片字节 → 512 维归一化向量

    ★ transformers 4.57 坑：get_image_features/get_text_features 内部依赖的
      pooler_output 返回 None（TypeError: linear input must be Tensor, not NoneType）。
      绕过：手动取 last_hidden_state 的 CLS（第0位）→ projection 层，
      这正是 Chinese-CLIP 官方实现的取法（text/vision 都是 CLS 直投，无 pooler dense）。
    """
    model, processor = get_model()
    img = Image.open(io.BytesIO(file_bytes)).convert("RGB")
    inputs = processor(images=img, return_tensors="pt")
    with torch.no_grad():
        out = model.vision_model(pixel_values=inputs["pixel_values"])
        pooled = out.pooler_output
        if pooled is None:
            pooled = out.last_hidden_state[:, 0, :]
        vec = model.visual_projection(pooled)
    vec = F.normalize(vec, p=2, dim=-1)[0]
    return vec.numpy().astype("float32").tolist()


def encode_text(text: str) -> list:
    """中文文本 → 512 维归一化向量（与图像同空间，可直接算相似度）"""
    model, processor = get_model()
    inputs = processor(text=[text], padding=True, truncation=True,
                       max_length=52, return_tensors="pt")
    with torch.no_grad():
        out = model.text_model(**inputs)
        pooled = out.pooler_output
        if pooled is None:
            pooled = out.last_hidden_state[:, 0, :]   # CLS（Chinese-CLIP 官方取法）
        vec = model.text_projection(pooled)
    vec = F.normalize(vec, p=2, dim=-1)[0]
    return vec.numpy().astype("float32").tolist()


def warmup():
    encode_text("预热")
    log.info("clip 预热完成")
