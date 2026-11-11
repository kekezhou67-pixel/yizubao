# -*- coding: utf-8 -*-
"""BGE 向量化（讲义04 §3/§5）

要点：
- 模型模块级单例：加载要几十秒，服务启动时加载一次，请求内复用
- normalize_embeddings=True：归一化后 内积(IP) == 余弦相似度
- ★ query 与 document 必须用同一个模型（讲义04 核心约束）——
  Java 侧的问题向量也调本服务 /embed 生成，保证同源
"""
import logging
import threading

log = logging.getLogger(__name__)

_model = None
_lock = threading.Lock()


def get_model():
    """懒加载 + 双检锁单例"""
    global _model
    if _model is None:
        with _lock:
            if _model is None:
                from sentence_transformers import SentenceTransformer
                from app import config
                log.info("加载 BGE 模型: %s ...", config.BGE_MODEL)
                _model = SentenceTransformer(config.BGE_MODEL, device="cpu")
                log.info("BGE 模型加载完成，输出维度: %s", _model.get_sentence_embedding_dimension())
    return _model


def embed_texts(texts: list) -> list:
    """批量向量化，返回归一化后的 float32 向量列表"""
    model = get_model()
    vectors = model.encode(texts, normalize_embeddings=True)
    return [v.astype("float32").tolist() for v in vectors]


def embed_query(query: str) -> list:
    """单条 query 向量化（与文档同模型！）"""
    return embed_texts([query])[0]


def warmup():
    """服务启动时预热：加载模型并跑一次推理（触发内部初始化）"""
    embed_texts(["预热"])
    log.info("embedder 预热完成")
