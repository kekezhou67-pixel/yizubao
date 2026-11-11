package com.rental.platform.mapper;

import com.rental.platform.entity.KbChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbChunkMapper {

    int insert(KbChunk chunk);

    /** 批量插入（入库流程一次写几百条） */
    int batchInsert(@Param("list") List<KbChunk> chunks);

    KbChunk findById(@Param("id") Long id);

    List<KbChunk> findByIds(@Param("ids") List<Long> ids);

    /** 按 vector_id 查（RRF 融合后反查分片内容） */
    List<KbChunk> findByVectorIds(@Param("vectorIds") List<String> vectorIds);

    List<KbChunk> listByDoc(@Param("docId") Long docId);

    int deleteByDocId(@Param("docId") Long docId);
}
