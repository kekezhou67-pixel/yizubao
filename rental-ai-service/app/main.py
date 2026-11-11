# -*- coding: utf-8 -*-
"""租房平台 Python AI 服务（阶段4）

职责边界（工程方案决策4）：
  Python 只做"AI 计算"—— 解析、分片、向量化、向量检索、精排、OCR、CLIP
  不做业务、不调 LLM、不管权限（那些都在 Java 侧）

启动：
  venv\\Scripts\\python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
"""
import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import clip, ocr, parse, rag

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
log = logging.getLogger("ai-service")

_state = {"started_at": None, "models_ready": False}


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时预热模型（首次会联网下载，走 hf-mirror 镜像）"""
    _state["started_at"] = time.time()
    log.info("服务启动，开始预热模型（首次需下载，请耐心等待）...")
    try:
        from app.services import embedder
        embedder.warmup()
        _state["models_ready"] = True
        log.info("模型预热完成 ✓")
    except Exception as e:
        # 预热失败不阻塞启动（/health 会暴露状态），方便先调试其他接口
        log.error("模型预热失败: %s", e)
    yield
    log.info("服务关闭")


app = FastAPI(
    title="租房平台 AI 服务",
    description="文档解析/父子分片/BGE向量化/FAISS检索/Rerank精排（Java 主服务的 AI 计算后端）",
    version="1.0.0",
    lifespan=lifespan,
)

# 跨域：Java(:8080) 与前端直调都要能访问（讲义04 的坑：忘配 CORS 浏览器报跨域）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(parse.router)
app.include_router(rag.router)
app.include_router(ocr.router)
app.include_router(clip.router)


@app.get("/health")
def health():
    from app.services.vector_store import get_text_store
    return {
        "status": "ok",
        "models_ready": _state["models_ready"],
        "uptime_seconds": int(time.time() - (_state["started_at"] or time.time())),
        "text_index_count": get_text_store().count(),
    }


if __name__ == "__main__":
    import uvicorn
    from app import config
    uvicorn.run("app.main:app", host=config.HOST, port=config.PORT, reload=False)
