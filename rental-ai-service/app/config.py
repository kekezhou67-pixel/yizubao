# -*- coding: utf-8 -*-
"""全局配置（阶段4）

模型下载走 hf-mirror 镜像（工程方案风险表：国内下载慢/失败的应对）。
所有路径基于本文件位置计算，不依赖启动目录。
"""
import os
from pathlib import Path

# ★ 必须在 import sentence_transformers 之前设置，模型下载走国内镜像
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")

BASE_DIR = Path(__file__).resolve().parent.parent   # rental-ai-service/
DATA_DIR = BASE_DIR / "data"
FAISS_DIR = DATA_DIR / "faiss_index"
FAISS_DIR.mkdir(parents=True, exist_ok=True)

# ---- 模型（讲义04：bge-small-zh-v1.5 是课程首选，512维）----
BGE_MODEL = os.environ.get("BGE_MODEL", "BAAI/bge-small-zh-v1.5")
RERANK_MODEL = os.environ.get("RERANK_MODEL", "BAAI/bge-reranker-base")
# 讲义05 的坑：clip-ViT-B-32 英文友好，中文查询效果差 →
# 换中文 CLIP（OFA-Sys，输出同为 512 维，换模型必须重建 img 索引）
CLIP_MODEL = os.environ.get("CLIP_MODEL", "OFA-Sys/chinese-clip-vit-base-patch16")
VECTOR_DIM = 512

# ---- 分片参数（讲义04 §3 父子分片）----
PARENT_CHUNK_SIZE = 2000      # 父块：喂给 LLM，保上下文
PARENT_CHUNK_OVERLAP = 200
CHILD_CHUNK_SIZE = 500        # 子块：做向量检索，保精度
CHILD_CHUNK_OVERLAP = 50

# ---- FAISS 索引文件（文本库；阶段7B 的图像库是另一套文件）----
TEXT_INDEX_FILE = FAISS_DIR / "text.index"
TEXT_META_FILE = FAISS_DIR / "text_meta.json"
IMG_INDEX_FILE = FAISS_DIR / "img.index"      # 阶段7B
IMG_META_FILE = FAISS_DIR / "img_meta.json"   # 阶段7B

# ---- 服务 ----
HOST = os.environ.get("AI_SERVICE_HOST", "0.0.0.0")
PORT = int(os.environ.get("AI_SERVICE_PORT", "8000"))
