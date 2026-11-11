package com.rental.platform.mapper;

import com.rental.platform.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentMapper {

    int insert(KbDocument doc);

    KbDocument findById(@Param("id") Long id);

    /** 列表（不含 content 大字段，防止内存爆炸） */
    List<KbDocument> list(@Param("sourceType") String sourceType,
                          @Param("city") String city);

    int updateChunkCount(@Param("id") Long id, @Param("chunkCount") Integer chunkCount);

    /** 逻辑删除 */
    int softDelete(@Param("id") Long id);
}
