# -*- coding: utf-8 -*-
"""CrossEncoder 精排（讲义04 §6：两阶段检索的第二阶段）

bi-encoder（BGE）粗排召回 top-20（快，向量可预计算）
→ cross-encoder（bge-reranker-base）精排取 top-5（慢但准：query+doc 拼对打分）

调度由 Java 发起（工程方案决策4：Python 只做 AI 计算，不管业务）。
"""
import logging
import threading

log = logging.getLogger(__name__)

_model = None
_lock = threading.Lock()


def get_model():
    global _model
    if _model is None:
        with _lock:
            if _model is None:
                from sentence_transformers import CrossEncoder
                from app import config
                log.info("加载 Rerank 模型: %s ...", config.RERANK_MODEL)
                _model = CrossEncoder(config.RERANK_MODEL, device="cpu")
                log.info("Rerank 模型加载完成")
    return _model


def rerank(query: str, candidates: list, top_k: int = 5) -> list:
    """candidates: [{"vector_id":..., "text":...}]
    返回按分数降序的 [{vector_id, text, score}] 前 top_k 条"""
    if not candidates:
        return []
    model = get_model()
    pairs = [[query, c.get("text", "")] for c in candidates]
    scores = model.predict(pairs)
    scored = sorted(
        zip(candidates, scores),
        key=lambda x: float(x[1]),
        reverse=True,
    )[:top_k]
    return [
        {**c, "score": float(s)}
        for c, s in scored
    ]


def warmup():
    get_model().predict([["预热", "预热"]])
    log.info("reranker 预热完成")
