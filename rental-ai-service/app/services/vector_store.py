# -*- coding: utf-8 -*-
"""FAISS 向量存储（讲义04 §5 + 工程方案决策2：统一 FAISS，不引入 Milvus）

设计：
- IndexFlatIP(512)：暴力内积检索，向量已归一化 → IP 等价余弦，分数越大越相似
  （几千条量级 Flat 完全够用且零精度损失；数据量大了再换 HNSW/IVF）
- FAISS 本身没有业务 id：用 meta JSON 维护 行号 → {vector_id, doc_id, chunk_id} 映射
- 持久化：faiss.write_index + text_meta.json（服务重启自动加载）
- 线程锁：FAISS index 的 add/search 非线程安全，统一加锁
"""
import json
import logging
import threading
from pathlib import Path

import faiss
import numpy as np

from app import config

log = logging.getLogger(__name__)


class FaissStore:
    """一套向量库（文本库/图像库各实例化一个，语义空间不同不能混 —— 讲义05 重点）"""

    def __init__(self, index_file: Path, meta_file: Path, dim: int = config.VECTOR_DIM):
        self.index_file = index_file
        self.meta_file = meta_file
        self.dim = dim
        self._lock = threading.Lock()
        self._meta: list = []      # 行号 → {"vector_id":..., 其他业务字段}
        self._index = None
        self._load()

    def _load(self):
        # ★ Windows 中文路径坑：faiss.read_index/write_index 的 C++ 层 fopen
        #   打不开含中文的路径（Illegal byte sequence）。
        #   解决：serialize_index/deserialize_index 走字节流 + Python 文件 API。
        # ★ deserialize_index 需要可写的 numpy uint8 数组（不是 bytes），故 frombuffer + copy
        if self.index_file.exists() and self.meta_file.exists():
            with open(self.index_file, "rb") as f:
                arr = np.frombuffer(f.read(), dtype=np.uint8).copy()
            self._index = faiss.deserialize_index(arr)
            with open(self.meta_file, "r", encoding="utf-8") as f:
                self._meta = json.load(f)
            log.info("FAISS 索引已加载: %s（%d 条）", self.index_file.name, self._index.ntotal)
        else:
            self._index = faiss.IndexFlatIP(self.dim)
            self._meta = []
            log.info("新建 FAISS 索引: %s (dim=%d)", self.index_file.name, self.dim)

    def _persist(self):
        """写盘（调用方已持锁）"""
        # serialize_index 返回 numpy uint8 数组，支持 buffer 协议可直接 write
        with open(self.index_file, "wb") as f:
            f.write(faiss.serialize_index(self._index))
        tmp = self.meta_file.with_suffix(".tmp")
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self._meta, f, ensure_ascii=False)
        tmp.replace(self.meta_file)   # 原子替换，防写一半损坏

    def add(self, vectors: list, metas: list) -> int:
        """批量入库。vectors: [[float]*dim]，metas: [{"vector_id":..., ...}] 一一对应"""
        if len(vectors) != len(metas):
            raise ValueError("vectors 与 metas 长度不一致")
        arr = np.asarray(vectors, dtype="float32")
        if arr.ndim != 2 or arr.shape[1] != self.dim:
            # 讲义04 的坑：dims 必须与模型输出严格一致
            raise ValueError(f"向量维度错误: 期望 {self.dim}，实际 {arr.shape}")
        with self._lock:
            self._index.add(arr)
            self._meta.extend(metas)
            self._persist()
        return len(metas)

    def search(self, query_vector: list, top_k: int = 20) -> list:
        """检索。返回 [{vector_id, score, row, ...meta}]，IP 分数越大越相似"""
        q = np.asarray([query_vector], dtype="float32")
        with self._lock:
            k = min(top_k, self._index.ntotal)
            if k <= 0:
                return []
            scores, rows = self._index.search(q, k)
        results = []
        for score, row in zip(scores[0], rows[0]):
            if row < 0:      # FAISS 不足 k 条时补 -1
                continue
            item = dict(self._meta[row])
            item["score"] = float(score)
            item["row"] = int(row)
            results.append(item)
        return results

    def count(self) -> int:
        with self._lock:
            return self._index.ntotal

    def delete_by_doc(self, doc_id) -> int:
        """按 doc_id 删除（IndexFlatIP 不支持按 id 删 → 重建索引）
        知识库文档量小，重建代价可接受"""
        with self._lock:
            keep_idx = [i for i, m in enumerate(self._meta) if m.get("doc_id") != doc_id]
            removed = len(self._meta) - len(keep_idx)
            if removed == 0:
                return 0
            if keep_idx:
                old_vectors = self._index.reconstruct_n(0, self._index.ntotal)
                new_arr = np.asarray([old_vectors[i] for i in keep_idx], dtype="float32")
                self._index.reset()
                self._index.add(new_arr)
                self._meta = [self._meta[i] for i in keep_idx]
            else:
                self._index.reset()
                self._meta = []
            self._persist()
            return removed


# ---- 模块级单例 ----
_text_store = None
_img_store = None
_store_lock = threading.Lock()


def get_text_store() -> FaissStore:
    """文本向量库（BGE 512维：法规/攻略/房源文本）"""
    global _text_store
    if _text_store is None:
        with _store_lock:
            if _text_store is None:
                _text_store = FaissStore(config.TEXT_INDEX_FILE, config.TEXT_META_FILE)
    return _text_store


def get_img_store() -> FaissStore:
    """图像向量库（CLIP 512维：房源图片）—— 阶段7B 启用
    ★ 与文本库分开：BGE 和 CLIP 是两个语义空间，混在一个索引里相似度无意义（讲义05 面试题）"""
    global _img_store
    if _img_store is None:
        with _store_lock:
            if _img_store is None:
                _img_store = FaissStore(config.IMG_INDEX_FILE, config.IMG_META_FILE)
    return _img_store
