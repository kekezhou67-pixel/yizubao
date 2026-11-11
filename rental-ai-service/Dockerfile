# Python AI 服务镜像（AI 改造 · 阶段8）
# 职责：解析/分片/BGE向量化/FAISS检索/Rerank/OCR/CLIP
#
# 体积说明：torch+paddle 依赖大（镜像约3GB），模型文件（BGE/Reranker/CLIP 约4GB）
# 通过 volume 挂载 ~/.cache/huggingface，不打进镜像（工程方案 §8.3）
FROM python:3.10-slim

WORKDIR /app

# 系统依赖：libgomp1 是 faiss/sklearn 的 OpenMP 运行时，libgl/libglib 是 opencv(paddleocr) 需要
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt requirements-ocr.txt ./

# ★ 先装 CPU 版 torch（默认源会拉 CUDA 版 2.5GB+），再装其余依赖
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple \
    && pip install --no-cache-dir -r requirements-ocr.txt -i https://pypi.tuna.tsinghua.edu.cn/simple \
    && pip install --no-cache-dir "numpy==1.26.4" --force-reinstall

COPY app ./app

# 模型下载走国内镜像 + 禁用 xet（实测：xet 直连海外源超时，讲义风险表的坑）
ENV HF_ENDPOINT=https://hf-mirror.com \
    HF_HUB_DISABLE_XET=1 \
    PYTHONUNBUFFERED=1

EXPOSE 8000

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
